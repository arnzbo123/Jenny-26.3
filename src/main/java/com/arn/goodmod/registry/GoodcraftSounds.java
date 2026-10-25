package com.arn.goodmod.registry;

import com.arn.goodmod.GoodcraftMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Preserves the original registry, including IDs that already lacked audio in the original JAR. */
public final class GoodcraftSounds {
    private static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<>();

    private GoodcraftSounds() {}

    private static final Map<String, Integer> LAST_PICK = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * c.X[i] or c.a(c.X): one sound of a legacy array such as GIRLS_JENNY_LIPSOUND. A random pick avoids repeating
     * the previous one, as c.a did.
     */
    public static SoundEvent pick(String array, int index, net.minecraft.util.RandomSource random) {
        String[] parts = array.toLowerCase(java.util.Locale.ROOT).split("_");
        String prefix = String.join(".", parts) + "." + parts[parts.length - 1];
        if (index >= 0) return events().get(prefix + index);
        int count = 0;
        while (events().containsKey(prefix + count)) count++;
        if (count == 0) return null;
        int last = LAST_PICK.getOrDefault(prefix, -1), choice = random.nextInt(count);
        if (count > 1 && choice == last) choice = (choice + 1 + random.nextInt(count - 1)) % count;
        LAST_PICK.put(prefix, choice);
        return events().get(prefix + choice);
    }

    public static void initialize() {
        if (!EVENTS.isEmpty()) {
            return;
        }
        JsonObject manifest = readJson("/assets/" + GoodcraftMod.MOD_ID + "/sounds.json").getAsJsonObject();
        Set<String> names = new TreeSet<>(manifest.keySet());
        int missingAudio = 0;
        for (JsonElement legacyId : readJson("/sexmod/legacy_sound_events.json").getAsJsonArray()) {
            String name = legacyId.getAsString();
            names.add(name);
            if (!manifest.has(name)) {
                missingAudio++;
            }
        }
        for (String name : names) {
            Identifier id = Identifier.fromNamespaceAndPath(GoodcraftMod.MOD_ID, name);
            EVENTS.put(name, Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                    SoundEvent.createVariableRangeEvent(id)));
        }
        GoodcraftMod.LOGGER.info("Registered {} sound events from the original registry and assets", EVENTS.size());
        if (missingAudio > 0) {
            GoodcraftMod.LOGGER.warn("{} legacy sound IDs have no audio definitions in the original JAR; IDs are retained.", missingAudio);
        }
    }

    private static JsonElement readJson(String path) {
        try (InputStream stream = GoodcraftSounds.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled sound manifest: " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load bundled sound manifest", exception);
        }
    }

    public static Map<String, SoundEvent> events() {
        return Collections.unmodifiableMap(EVENTS);
    }
}
