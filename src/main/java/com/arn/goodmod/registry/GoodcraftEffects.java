package com.arn.goodmod.registry;

import com.arn.goodmod.world.PlayerForms;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.alchemy.Potion;

/** co/cw potion mechanics, including the delayed ordinary player-form selection. */
public final class GoodcraftEffects {
    public static final Holder<MobEffect> TRANSFORMATION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath("sexmod", "horny_potion"), new TransformationEffect());
    public static final Holder<Potion> POTION = Registry.registerForHolder(BuiltInRegistries.POTION,
            Identifier.fromNamespaceAndPath("sexmod", "horny_potion"), new Potion("horny_potion",
                    new MobEffectInstance(TRANSFORMATION, 3600), new MobEffectInstance(MobEffects.NAUSEA, 200, 1)));
    private record Courtship(Villager first, Villager second, long end) {}
    private static final Map<UUID, Courtship> COURTSHIPS = new HashMap<>();
    private GoodcraftEffects() {}
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> COURTSHIPS.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = COURTSHIPS.entrySet().iterator();
            while (iterator.hasNext()) {
                Courtship courtship = iterator.next().getValue();
                Villager first = courtship.first(), second = courtship.second();
                if (!first.isAlive() || !second.isAlive() || first.isRemoved() || second.isRemoved()
                        || first.level() != second.level() || !(first.level() instanceof ServerLevel level)) { iterator.remove(); continue; }
                first.getLookControl().setLookAt(second, 10, 30);
                if (first.distanceToSqr(second) > 2.25) first.getNavigation().moveTo(second, .25);
                if (first.getRandom().nextInt(35) == 0) level.broadcastEntityEvent(first, (byte)12);
                if (server.getTickCount() >= courtship.end()) {
                    Villager child = first.getBreedOffspring(level, second);
                    if (child != null) {
                        first.setAge(6000); second.setAge(6000); child.setAge(-24000);
                        child.setPos(first.position()); level.addFreshEntity(child); level.broadcastEntityEvent(child, (byte)12);
                    }
                    iterator.remove();
                }
            }
        });
    }
    private static final class TransformationEffect extends MobEffect {
        TransformationEffect() { super(MobEffectCategory.BENEFICIAL, 16736968); }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            if (entity instanceof ServerPlayer player) {
                MobEffectInstance effect = player.getEffect(TRANSFORMATION);
                if (effect != null && effect.getDuration() <= 3500) { PlayerForms.openSelection(player); return false; }
            } else if (entity instanceof Animal animal) {
                if (animal.getAge() >= 0) {
                    animal.setAge(0); animal.resetLove(); animal.setInLove(level.getNearestPlayer(animal, 30));
                }
                return false;
            } else if (entity instanceof Villager villager) {
                // Keep the original 300-tick pursuit, while excluding children and concurrent pairs.
                if (villager.getAge() == 0 && !COURTSHIPS.containsKey(villager.getUUID())) {
                    var mate = level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(8, 3, 8),
                            candidate -> candidate != villager && candidate.isAlive() && candidate.getAge() == 0
                                    && COURTSHIPS.values().stream().noneMatch(pair -> pair.first() == candidate || pair.second() == candidate))
                            .stream().min(java.util.Comparator.comparingDouble(villager::distanceToSqr));
                    mate.ifPresent(other -> COURTSHIPS.put(villager.getUUID(), new Courtship(villager, other, level.getServer().getTickCount() + 300L)));
                }
                return false;
            }
            return true;
        }
    }
}
