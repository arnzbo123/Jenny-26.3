package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Real-level regressions for the stateful NPC migration; excluded from the shipped jar. */
public final class EntityWorldChecks {
    private EntityWorldChecks() {}
    private interface Check { void run(); }
    public static List<Map<String, Object>> run(ServerLevel level, ServerPlayer player) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Entity> created = new ArrayList<>();
        try {
            check(results, "entity-equipment-save-key-and-vanilla-mirror", () -> {
                GoodcraftNpc npc = make(level, player, NpcKind.JENNY, created);
                npc.getEquipmentInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
                npc.getEquipmentInventory().setItem(2, new ItemStack(Items.DIAMOND_HELMET));
                npc.setMovementMode(MovementMode.WAIT); npc.tick();
                CompoundTag saved = save(level, npc);
                require(saved.getCompound("npcEquipment").isPresent(), "Backing equipment container not saved");
                require(saved.getCompound("equipment").isPresent(), "Vanilla equipment codec was overwritten");
                require(!saved.getCompound("equipment").orElseThrow().contains("Items"), "Backing Items list overwrote vanilla equipment");
                GoodcraftNpc restored = make(level, player, NpcKind.JENNY, created); load(level, restored, saved);
                require(restored.getEquipmentInventory().getItem(0).is(Items.DIAMOND_SWORD), "Weapon backing slot lost");
                require(restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "Vanilla armor mirror was not restored");
                require(restored.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "Vanilla weapon mirror was not restored");
                // Compatibility with the first migration format must not consume vanilla equipment compounds.
                CompoundTag prior = saved.copy(); prior.put("equipment", prior.getCompound("npcEquipment").orElseThrow().copy()); prior.remove("npcEquipment");
                GoodcraftNpc migrated = make(level, player, NpcKind.JENNY, created); load(level, migrated, prior);
                require(migrated.getEquipmentInventory().getItem(2).is(Items.DIAMOND_HELMET), "Prior backing inventory migration lost armor");
            });
            check(results, "entity-player-form-recreation-grants-no-items", () -> {
                for (NpcKind kind : GoodcraftEntities.playerTypes().keySet()) {
                    GoodcraftNpc form = GoodcraftEntities.createPlayerForm(kind, level, player.getUUID()); created.add(form);
                    require(form.getEquipmentInventory().isEmpty(), "Form constructor granted equipment for " + kind);
                    require(!form.hurtServer(level, level.damageSources().generic(), 100), "Visual player form took damage");
                    require(form.getAssociatedPlayerUUID().filter(player.getUUID()::equals).isPresent(), "Form association lost");
                }
            });
            check(results, "entity-companion-downed-immunity-and-recovery", () -> {
                GoodcraftNpc npc = make(level, player, NpcKind.JENNY, created); npc.setMovementMode(MovementMode.WAIT);
                require(npc.hurtServer(level, level.damageSources().generic(), 100), "Lethal hit was not handled");
                require(npc.isAlive() && npc.getHealth() == 1 && npc.getAnimationState().equals("DOWNED"), "Companion died instead of entering DOWNED");
                require(!npc.hurtServer(level, level.damageSources().generic(), 10), "Incapacitated companion accepted a second hit");
                npc.setHealth(npc.getMaxHealth() - 1);
                for (int tick = 0; tick < 81; tick++) npc.tick();
                require(npc.getHealth() == npc.getMaxHealth() && npc.getAnimationState().equals("NULL"), "Companion did not recover after regeneration");
            });
            check(results, "entity-galath-knockout-survives-save-load", () -> {
                GalathNpc boss = (GalathNpc)make(level, player, NpcKind.GALATH, created);
                UUID minion = UUID.randomUUID(); boss.registerSummonedSkeleton(minion); boss.knockOut(boss.position().subtract(1, 0, 0));
                GalathNpc restored = (GalathNpc)make(level, player, NpcKind.GALATH, created); load(level, restored, save(level, boss));
                require(restored.getCombatPhase() == GalathNpc.Phase.KNOCK_OUT_FLY, "Knockout phase was not restored");
                require(restored.isSummonedSkeleton(minion), "Unloaded summoned minion identity was lost");
                restored.setOnGround(true); restored.tick();
                require(restored.getCombatPhase() == GalathNpc.Phase.KNOCK_OUT_GROUND, "Restored knockout could not advance");
                for (int tick = 0; tick < 110; tick++) restored.tick();
                require(restored.getCombatPhase() == GalathNpc.Phase.NONE && restored.getAnimationState().equals("NULL"), "Restored boss remained stuck in knockout");
                require(restored.getTarget() == null, "Owned Galath attacked its owner");
            });
            check(results, "entity-galath-owner-flight-boost-and-dismount", () -> {
                GalathNpc mount = (GalathNpc)make(level, player, NpcKind.GALATH, created);
                require(level.addFreshEntity(mount), "World rejected flight actor");
                mount.setOwnerUUID(UUID.randomUUID()); require(!mount.startRidingFlight(player), "Unowned player mounted Galath");
                mount.setOwnerUUID(player.getUUID()); require(mount.startRidingFlight(player), "Owner could not mount Galath");
                try {
                    player.setLastClientInput(new net.minecraft.world.entity.player.Input(true, false, false, false, true, false, false));
                    for (int tick = 0; tick < 16; tick++) mount.tick();
                    require(mount.getBoostCharges() == 2 && mount.getAnimationState().equals("BOOST"), "Jump input failed to consume one boost");
                    require(mount.getDeltaMovement().length() > 4, "Boost never reached source flight velocity");
                    for (int tick = 0; tick < 32; tick++) mount.tick();
                    require(mount.getBoostCharges() == 2 && mount.getAnimationState().equals("CONTROLLED_FLIGHT"), "Holding jump repeated boost or failed to finish it");
                } finally { player.setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY); player.stopRiding(); }
                mount.tick(); require(!mount.isFlightActive() && !mount.isNoGravity() && mount.getAssociatedPlayerUUID().isEmpty(), "Dismount did not release flight state");
                mount.discard();
            });
            check(results, "entity-galath-manglelie-adoption-and-persisted-links", () -> {
                GalathNpc mother = (GalathNpc)make(level, player, NpcKind.GALATH, created);
                ManglelieNpc child = (ManglelieNpc)make(level, player, NpcKind.MANGLELIE, created); child.setOwnerUUID(null);
                mother.setPos(player.position().add(6, 0, 6)); child.setPos(mother.position().add(1, 0, 0));
                require(level.addFreshEntity(mother) && level.addFreshEntity(child), "World rejected adoption actors");
                mother.setOnGround(true); mother.tick();
                require(mother.getAnimationState().equals("HUG_MANG") && mother.isUsingCombinedModel(), "Pair did not enter combined hug");
                require(child.getMotherUUID().filter(mother.getUUID()::equals).isPresent(), "Child association missing");
                for (int tick = 0; tick < 241; tick++) { mother.tick(); child.tick(); }
                require(!mother.isAnchored() && !mother.isUsingCombinedModel(), "Hug did not release parent");
                require(child.getAnimationState().equals("RIDE_MOMMY_HEAD") && child.isNoGravity(), "Child attachment was not retained");
                GalathNpc restoredMother = (GalathNpc)make(level, player, NpcKind.GALATH, created); load(level, restoredMother, save(level, mother));
                ManglelieNpc restoredChild = (ManglelieNpc)make(level, player, NpcKind.MANGLELIE, created); load(level, restoredChild, save(level, child));
                require(restoredMother.getManglelieUUID().filter(child.getUUID()::equals).isPresent(), "Parent association lost on reload");
                require(restoredChild.getMotherUUID().filter(mother.getUUID()::equals).isPresent(), "Child association lost on reload");
                mother.discard(); child.discard();
            });
        } finally { created.forEach(Entity::discard); }
        return results;
    }
    private static GoodcraftNpc make(ServerLevel level, ServerPlayer player, NpcKind kind, List<Entity> created) {
        GoodcraftNpc npc = GoodcraftEntities.create(kind, level); require(npc != null, "Factory returned null");
        npc.setPos(player.position().add(2, 0, 2)); npc.setOwnerUUID(player.getUUID()); created.add(npc); return npc;
    }
    private static CompoundTag save(ServerLevel level, Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess()); entity.saveWithoutId(output); return output.buildResult();
    }
    private static void load(ServerLevel level, Entity entity, CompoundTag tag) { entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag)); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void check(List<Map<String, Object>> results, String name, Check work) {
        var result = new LinkedHashMap<String, Object>(); result.put("name", name);
        try { work.run(); result.put("passed", true); }
        catch (Throwable failure) { result.put("passed", false); result.put("error", failure.toString()); org.slf4j.LoggerFactory.getLogger("sexmod_world_test").error(name, failure); }
        results.add(result);
    }
}
