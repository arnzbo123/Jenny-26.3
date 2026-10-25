package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.animation.GoodcraftAnimations;
import com.arn.goodmod.registry.GoodcraftSounds;
import java.util.Locale;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Recovered marker audio only; server transitions are advanced by authoritative entity ticks. */
public final class GoodcraftAnimationSounds {
    private GoodcraftAnimationSounds() {}
    public static void initialize() {
        GoodcraftAnimations.setClientPerspective(npc -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            return client.player != null && client.options.getCameraType().isFirstPerson()
                    && npc.getAssociatedPlayerUUID().filter(client.player.getUUID()::equals).isPresent();
        });
        GoodcraftAnimations.setClientKeyframeHandler((npc, marker) -> {
            var callbacks = AnimationCatalog.profile(npc.getNpcKind().id(), npc.isPlayerForm()).getAsJsonObject("unconditional_sound_callbacks");
            // The girls' marker sounds are played by the server (SceneDirector); only player forms play them here.
            if (callbacks == null || !npc.isPlayerForm()) return;
            if (!callbacks.has(marker)) return;
            for (var entry : callbacks.getAsJsonArray(marker)) {
                var sound = entry.getAsJsonObject();
                SoundEvent event;
                if (sound.has("vanilla")) event = SoundEvents.PLAYER_ATTACK_STRONG;
                else {
                    String prefix = sound.get("array").getAsString().toLowerCase(Locale.ROOT).replace('_', '.');
                    prefix += "." + prefix.substring(prefix.lastIndexOf('.') + 1);
                    int index = sound.get("index").getAsInt();
                    if (index < 0) {
                        String base = prefix;
                        var choices = GoodcraftSounds.events().entrySet().stream()
                                .filter(e -> e.getKey().startsWith(base) && e.getKey().substring(base.length()).matches("[0-9]+"))
                                .sorted(java.util.Map.Entry.comparingByKey()).toList();
                        if (choices.isEmpty()) continue;
                        event = choices.get(npc.getRandom().nextInt(choices.size())).getValue();
                    } else event = GoodcraftSounds.events().get(prefix + index);
                }
                if (event != null) npc.level().playLocalSound(npc.getX(), npc.getY(), npc.getZ(), event, SoundSource.NEUTRAL,
                        sound.get("volume").getAsFloat(), 1, false);
            }
        });
    }
}
