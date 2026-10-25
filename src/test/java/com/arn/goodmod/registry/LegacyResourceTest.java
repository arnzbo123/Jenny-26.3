package com.arn.goodmod.registry;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LegacyResourceTest {
    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GoodcraftSounds.initialize();
    }

    @Test
    void retainsLegacySoundIdsEvenWhenTheOriginalAudioWasMissing() {
        assertEquals(721, GoodcraftSounds.events().size());
        for (String name : new String[]{"girls.jenny.hmph.hmph4", "girls.kobold.lightbreathing.lightbreathing8"}) {
            var event = GoodcraftSounds.events().get(name);
            assertNotNull(event);
            assertSame(event, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.fromNamespaceAndPath("sexmod", name)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"slime", "jenny", "ellie", "bia"})
    void convertedLootTablesDecodeWithMinecraft263Codec(String name) throws Exception {
        var access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        try (var stream = getClass().getResourceAsStream("/data/sexmod/loot_table/" + name + ".json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var result = LootTable.DIRECT_CODEC.parse(ops, json);
            assertTrue(result.isSuccess(), () -> name + ": " + result.error().orElseThrow().message());
        }
    }
}
