package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import com.arn.goodmod.registry.GoodcraftItems;
import com.arn.goodmod.scene.SceneScripts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Drives each menu action's whole scene on the server with a fake partner: pay, approach, faster, finish, release. */
public final class SceneWorldChecks {
    private record Scenario(EntityType<? extends GoodcraftNpc> type, String action, boolean bed, boolean owned, List<String> expected) {}
    private static final Set<String> WAITS = Set.of("WAITDOGGY", "ANAL_WAIT", "SITDOWNIDLE", "WAIT_CAT");

    private SceneWorldChecks() {}

    public static List<Map<String, Object>> run(ServerLevel level, FakePlayer player, BlockPos origin) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Scenario> scenarios = List.of(
                new Scenario(GoodcraftEntities.JENNY, "action.names.blowjob", false, false, List.of("PAYMENT", "STARTBLOWJOB", "SUCKBLOWJOB", "THRUSTBLOWJOB", "CUMBLOWJOB")),
                new Scenario(GoodcraftEntities.JENNY, "action.names.boobjob", false, false, List.of("PAYMENT", "STRIP", "PAIZURI_START", "PAIZURI_SLOW", "PAIZURI_FAST", "PAIZURI_CUM")),
                new Scenario(GoodcraftEntities.JENNY, "action.names.doggy", true, false, List.of("PAYMENT", "STRIP", "STARTDOGGY", "WAITDOGGY", "DOGGYSTART", "DOGGYSLOW", "DOGGYFAST", "DOGGYCUM")),
                new Scenario(GoodcraftEntities.JENNY, "action.names.strip", false, false, List.of("PAYMENT", "STRIP")),
                new Scenario(GoodcraftEntities.ELLIE, "Face fuck", false, false, List.of("CARRY_INTRO", "CARRY_SLOW", "CARRY_FAST", "CARRY_CUM")),
                new Scenario(GoodcraftEntities.BIA, "action.names.headpat", false, false, List.of("HEAD_PAT")),
                new Scenario(GoodcraftEntities.BIA, "action.names.talk", true, false, List.of("TALK_HORNY", "TALK_IDLE")),
                new Scenario(GoodcraftEntities.LUNA, "action.names.touchboobs", false, false, List.of("PAYMENT", "TOUCH_BOOBS_INTRO", "TOUCH_BOOBS_SLOW", "TOUCH_BOOBS_CUM")),
                new Scenario(GoodcraftEntities.LUNA, "action.names.sex", true, false, List.of("PAYMENT", "WAIT_CAT", "COWGIRL_SITTING_INTRO", "COWGIRL_SITTING_SLOW", "COWGIRL_SITTING_CUM")),
                new Scenario(GoodcraftEntities.KOBOLD, "anal", false, false, List.of("PAYMENT", "KOBOLD_ANAL_START", "KOBOLD_ANAL_SLOW", "KOBOLD_ANAL_FAST", "KOBOLD_ANAL_CUM")),
                new Scenario(GoodcraftEntities.KOBOLD, "oral", false, false, List.of("PAYMENT", "STARTBLOWJOB", "SUCKBLOWJOB_BLINK", "THRUSTBLOWJOB", "CUMBLOWJOB")),
                new Scenario(GoodcraftEntities.KOBOLD, "mating", false, true, List.of("MATING_PRESS_START", "MATING_PRESS_SOFT", "MATING_PRESS_HARD", "MATING_PRESS_CUM")),
                new Scenario(GoodcraftEntities.GOBLIN, "use her", false, false, List.of("PAIZURI_START", "PAIZURI_IDLE", "PAIZURI_FAST", "PAIZURI_CUM")),
                new Scenario(GoodcraftEntities.GALATH, "cowgirl", false, true, List.of("RAPE_INTRO", "RAPE_ON_GOING", "RAPE_CUM")),
                new Scenario(GoodcraftEntities.GALATH, "anal", false, true, List.of("CORRUPT_SLOW", "CORRUPT_FAST", "CORRUPT_CUM")),
                new Scenario(GoodcraftEntities.ELLIE, "action.names.cowgirl", true, false, List.of("SITDOWN", "SITDOWNIDLE", "COWGIRLSTART", "COWGIRLSLOW", "COWGIRLCUM")),
                new Scenario(GoodcraftEntities.ELLIE, "action.names.missionary", true, false, List.of("SITDOWNIDLE", "MISSIONARY_START", "MISSIONARY_SLOW", "MISSIONARY_CUM")),
                new Scenario(GoodcraftEntities.BIA, "action.names.anal", true, false, List.of("ANAL_PREPARE", "ANAL_WAIT", "ANAL_START", "ANAL_SLOW", "ANAL_CUM")),
                new Scenario(GoodcraftEntities.BIA, "doggy", true, false, List.of("SITDOWN", "SITDOWNIDLE", "PRONE_DOGGY_INTRO", "PRONE_DOGGY_SOFT", "PRONE_DOGGY_CUM")),
                new Scenario(GoodcraftEntities.SLIME, "action.names.blowjob", false, false, List.of("SUCKBLOWJOB", "THRUSTBLOWJOB", "CUMBLOWJOB")),
                new Scenario(GoodcraftEntities.SLIME, "action.names.doggy", false, false, List.of("STARTDOGGY", "WAITDOGGY", "DOGGYSTART", "DOGGYSLOW", "DOGGYCUM")),
                new Scenario(GoodcraftEntities.BEE, "action.names.sex", false, false, List.of("CITIZEN_START", "CITIZEN_SLOW", "CITIZEN_FAST", "CITIZEN_CUM")),
                new Scenario(GoodcraftEntities.ALLIE, "action.names.deepthroat", false, true, List.of("ALLIE_PREPARE_FIRST_TIME", "DEEPTHROAT_START", "DEEPTHROAT_SLOW", "DEEPTHROAT_FAST", "DEEPTHROAT_CUM")),
                new Scenario(GoodcraftEntities.ALLIE, "Reverse cowgirl", false, true, List.of("ALLIE_PREPARE_FIRST_TIME", "REVERSE_COWGIRL_START", "REVERSE_COWGIRL_SLOW", "REVERSE_COWGIRL_CUM")));
        int index = 0;
        for (Scenario scenario : scenarios) {
            BlockPos base = origin.offset(-40 + (index++ % 6) * 12, 0, 40 + (index / 6) * 12);
            results.add(check("scene:" + BuiltInRegistries(scenario.type()) + ":" + scenario.action(), () -> runScenario(level, player, base, scenario)));
        }
        results.add(check("goblin-thief-caught-gives-back", () -> goblinThief(level, player, origin.offset(-40, 0, -40), "take ur stuff back")));
        results.add(check("goblin-thief-caught-used", () -> goblinThief(level, player, origin.offset(-52, 0, -40), "use her")));
        results.add(check("galath-boss-defeat-gives-coin", () -> galathDefeat(level, player, origin.offset(40, 0, -40))));
        results.add(check("galath-boss-fights-survival-player", () -> galathFight(level, player, origin.offset(-60, 0, -60))));
        results.add(check("kobold-tribe-worldgen", () -> tribeWorldgen(level, origin)));
        for (String house : List.of("jenny", "ellie", "bia", "luna")) results.add(check("locate-house:" + house, () -> locate(level, house)));
        return results;
    }

    private static String BuiltInRegistries(EntityType<?> type) { return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(); }

    private interface Work { String run() throws Exception; }
    private static Map<String, Object> check(String name, Work work) {
        var result = new LinkedHashMap<String, Object>(); result.put("name", name);
        try { result.put("detail", work.run()); result.put("passed", true); }
        catch (Throwable failure) {
            result.put("passed", false); result.put("error", failure.toString());
            LoggerFactory.getLogger("sexmod_world_test").error("Scene check failed: " + name, failure);
        }
        return result;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void floor(ServerLevel level, BlockPos base) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 0; y < 4; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static String runScenario(ServerLevel level, FakePlayer player, BlockPos base, Scenario scenario) {
        floor(level, base);
        if (scenario.bed()) {
            BlockPos foot = base.offset(4, 0, 0);
            level.setBlock(foot, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT), 3);
            level.setBlock(foot.east(), Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD), 3);
        }
        player.teleportTo(level, base.getX() + .5, base.getY(), base.getZ() + .5, Set.of(), 0, 0, false);
        player.setNoGravity(false);
        player.getInventory().clearContent();
        for (var item : List.of(Items.EMERALD, Items.ENDER_PEARL, Items.DIAMOND, Items.GOLD_INGOT, Items.COD, Items.SALMON)) player.getInventory().add(new ItemStack(item, 8));
        player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        GoodcraftNpc npc = scenario.type().create(level, EntitySpawnReason.COMMAND);
        require(npc != null, "NPC factory returned null");
        npc.setPos(base.getX() + .5, base.getY(), base.getZ() + 2.5);
        if (scenario.owned()) npc.setOwnerUUID(player.getUUID());
        if (npc instanceof AllieNpc) npc.setAnimationState("SUMMON_WAIT");
        if (npc instanceof BeeNpc) npc.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.arn.goodmod.registry.GoodcraftEffects.TRANSFORMATION, 200));
        require(level.addFreshEntity(npc), "World rejected NPC");
        try {
            int emeralds = wealth(player);
            player.setOnGround(true);
            require(npc.scene().request(player, scenario.action()), "Menu rejected " + scenario.action() + "; offered " + com.arn.goodmod.scene.SceneMenus.actions(npc, player));
            var script = SceneScripts.of(npc.getNpcKind());
            List<String> seen = new ArrayList<>();
            String last = "";
            int fastTicks = 0, slowTicks = 0, tick;
            boolean finished = false, followUpChosen = false, sawNude = false;
            int soundsBefore = com.arn.goodmod.scene.SceneDirector.SOUNDS_PLAYED.get();
            for (tick = 0; tick < 8000; tick++) {
                npc.tick();
                String state = npc.getAnimationState();
                if (!state.equals(last)) { seen.add(state); last = state; slowTicks = 0; }
                if (npc.getModelVariant() == 0 && !state.equals("NULL")) sawNude = true;
                // b0/e0.d()/el.t(): the partner walks up to her once she waits on the bed.
                if (WAITS.contains(state)) player.teleportTo(npc.getX(), npc.getY(), npc.getZ());
                // Bia's talk asks for a choice mid-scene.
                if (state.equals("TALK_IDLE") && !followUpChosen) { followUpChosen = true; require(npc.scene().request(player, "action.names.anal"), "Bia offered no follow-up"); }
                // Hold sneak for a while, then let go, so both the faster and the fall-back-to-slow paths run.
                boolean hold = fastTicks++ % 160 < 60;
                boolean sped = seen.stream().anyMatch(seenState -> seenState.contains("FAST") || seenState.contains("HARD") || seenState.contains("THRUST"));
                if (script.hasFinish(state) && (++slowTicks > 40 && sped || slowTicks > 400)) { npc.setArousal(1); npc.scene().input(player, hold, true, false); }
                else npc.scene().input(player, hold, false, false);
                if (tick > 5 && !npc.scene().isActive() && state.equals("NULL")) { finished = true; break; }
                if (npc.isRemoved()) { finished = true; break; }
            }
            String detail = String.join(">", seen) + " in " + tick + " ticks";
            require(finished, "Scene never ended: " + detail);
            for (String expected : scenario.expected()) require(seen.contains(expected), "Missing " + expected + ": " + detail);
            require(!player.isNoGravity(), "Partner left without gravity: " + detail);
            int sounds = com.arn.goodmod.scene.SceneDirector.SOUNDS_PLAYED.get() - soundsBefore;
            require(sounds >= 1, "Only " + sounds + " scene sounds: " + detail);
            detail += " (" + sounds + " sounds)";
            if (scenario.type() == GoodcraftEntities.BIA && !scenario.action().contains("headpat") && !scenario.action().contains("talk") || scenario.type() == GoodcraftEntities.SLIME)
                require(sawNude, "She stayed dressed for " + scenario.action() + ": " + detail);
            int after = wealth(player);
            if (seen.contains("PAYMENT")) require(after < emeralds, "Paid action took no items: " + detail);
            return detail;
        } finally { npc.discard(); }
    }

    private static int wealth(FakePlayer player) {
        int total = 0;
        for (var item : List.of(Items.EMERALD, Items.ENDER_PEARL, Items.DIAMOND, Items.GOLD_INGOT, Items.COD, Items.SALMON, Items.IRON_PICKAXE))
            total += player.getInventory().countItem(item);
        return total;
    }

    private static String goblinThief(ServerLevel level, FakePlayer player, BlockPos base, String choice) {
        floor(level, base);
        player.teleportTo(level, base.getX() + .5, base.getY(), base.getZ() + .5, Set.of(), 0, 0, false);
        player.getInventory().clearContent(); player.setNoGravity(false);
        var thief = (GoblinNpc)GoodcraftEntities.GOBLIN.create(level, EntitySpawnReason.COMMAND);
        thief.setPos(base.getX() + .5, base.getY(), base.getZ() + 2.5);
        thief.setStolen(new ItemStack(Items.GOLD_INGOT, 5)); thief.setAnimationState("RUN");
        require(level.addFreshEntity(thief), "World rejected thief");
        try {
            thief.interact(player, InteractionHand.MAIN_HAND, thief.position());
            require(thief.getAnimationState().equals("CATCH"), "Catching did not start: " + thief.getAnimationState());
            List<String> seen = new ArrayList<>(List.of("CATCH"));
            boolean chosen = false;
            for (int tick = 0; tick < 3000 && !thief.isRemoved(); tick++) {
                thief.tick();
                String state = thief.getAnimationState();
                if (!seen.getLast().equals(state)) seen.add(state);
                if (state.equals("CATCH_BJ_IDLE") && !chosen) { chosen = true; require(thief.scene().request(player, choice), "Caught thief did not offer " + choice); }
                boolean hold = tick % 160 < 60;
                if (com.arn.goodmod.scene.SceneScripts.of(NpcKind.GOBLIN).hasFinish(state) && thief.getActionTicks() > 40) { thief.setArousal(1); thief.scene().input(player, hold, true, false); }
                else thief.scene().input(player, hold, false, false);
            }
            String detail = String.join(">", seen);
            require(thief.isRemoved(), "Thief stayed after " + choice + ": " + detail);
            if (choice.startsWith("take")) require(player.getInventory().countItem(Items.GOLD_INGOT) == 5, "Stolen gold not returned: " + detail);
            require(!com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) && !player.isNoGravity(), "Player left frozen: " + detail);
            return detail;
        } finally { thief.discard(); }
    }

    private static String galathDefeat(ServerLevel level, FakePlayer player, BlockPos base) {
        floor(level, base);
        player.teleportTo(level, base.getX() + .5, base.getY(), base.getZ() + .5, Set.of(), 0, 0, false);
        player.getInventory().clearContent();
        var galath = (GalathNpc)GoodcraftEntities.GALATH.create(level, EntitySpawnReason.COMMAND);
        require(galath != null, "Galath factory returned null");
        galath.setPos(base.getX() + .5, base.getY(), base.getZ() + 3.5);
        require(level.addFreshEntity(galath), "World rejected Galath");
        try {
            require(galath.hurtServer(level, level.damageSources().playerAttack(player), 1000), "Lethal hit was ignored");
            require(galath.isAlive() && galath.isDefeated(), "Wild Galath died instead of being paralyzed");
            for (int tick = 0; tick < 400 && galath.getCombatPhase() != GalathNpc.Phase.KNOCK_OUT_GROUND; tick++) galath.tick();
            require(galath.getCombatPhase() == GalathNpc.Phase.KNOCK_OUT_GROUND, "Never landed paralyzed: " + galath.getCombatPhase());
            for (int tick = 0; tick < 200; tick++) galath.tick();
            require(galath.getCombatPhase() == GalathNpc.Phase.KNOCK_OUT_GROUND, "Paralyzed Galath stood back up");
            player.teleportTo(galath.getX(), galath.getY(), galath.getZ() - 1);
            galath.interact(player, InteractionHand.MAIN_HAND, galath.position());
            require(galath.getAnimationState().equals("GIVE_COIN"), "Right click did not start her surrender: " + galath.getAnimationState());
            require(galath.getRenderPartnerUUID().filter(player.getUUID()::equals).isPresent() && player.position().distanceTo(galath.position()) < .1,
                    "Victor is not posed at Galath for GIVE_COIN");
            for (int tick = 0; tick < 160 && !galath.isRemoved(); tick++) galath.tick();
            require(galath.isRemoved(), "Galath did not return into her coin");
            require(player.getInventory().countItem(GoodcraftItems.GALATH_COIN) == 1, "Victor did not receive the coin");
            require(!com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) && !player.isNoGravity(), "Victor left frozen after the hand-over");
            return "paralyzed, surrendered, coin granted";
        } finally { galath.discard(); }
    }

    private static String galathFight(ServerLevel level, FakePlayer player, BlockPos base) {
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 0; y < 14; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.teleportTo(level, base.getX() + .5, base.getY(), base.getZ() + .5, Set.of(), 0, 0, false);
        player.setHealth(player.getMaxHealth());
        var galath = (GalathNpc)GoodcraftEntities.GALATH.create(level, EntitySpawnReason.COMMAND);
        galath.setPos(base.getX() + .5, base.getY(), base.getZ() + 6.5);
        require(level.addFreshEntity(galath), "World rejected Galath");
        List<String> phases = new ArrayList<>();
        float lowest = player.getHealth();
        double closestStrike = Double.MAX_VALUE;
        try {
            for (int tick = 0; tick < 1200; tick++) {
                galath.tick();
                for (var skeleton : level.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.WitherSkeleton.class, galath.getBoundingBox().inflate(30))) skeleton.tick();
                for (var ball : level.getEntitiesOfClass(EnergyBallEntity.class, galath.getBoundingBox().inflate(30))) ball.tick();
                String phase = galath.getCombatPhase().name();
                if (phases.isEmpty() || !phases.getLast().equals(phase)) phases.add(phase);
                lowest = Math.min(lowest, player.getHealth());
                if (phase.equals("ATTACK_SWORD")) closestStrike = Math.min(closestStrike, galath.distanceTo(player));
                if (player.getHealth() <= 4) player.setHealth(player.getMaxHealth());
            }
            String detail = "target=" + galath.getTarget() + " phases=" + String.join(">", phases.subList(0, Math.min(20, phases.size()))) + " lowestHealth=" + lowest + " closestSwordStrike=" + closestStrike;
            require(phases.contains("ATTACK_SWORD") || phases.contains("SUMMON_SKELETON"), "Galath never attacked: " + detail);
            // FakePlayer reports an unloaded client, which makes vanilla players invulnerable; check the strike reaches instead.
            require(closestStrike < 2.5, "Galath's sword never reached the player: " + detail);
            return detail;
        } finally {
            galath.discard();
            level.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.WitherSkeleton.class, galath.getBoundingBox().inflate(40)).forEach(net.minecraft.world.entity.Entity::discard);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }
    }

    private static String tribeWorldgen(ServerLevel level, BlockPos base) throws Exception {
        var placed = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("sexmod", "legacy_buildings")));
        require(placed.isPresent(), "Tribe placed feature is not registered");
        var plains = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        boolean attached = plains.value().getGenerationSettings().features().stream().anyMatch(step -> step.contains(placed.get()));
        require(attached, "Tribe feature is not attached to plains generation");
        // g3.b() tests the chunk's centre column; give it dry grass above any natural terrain.
        BlockPos column = new BlockPos((base.getX() >> 4) * 16 + 8, base.getY() - 1, (base.getZ() >> 4) * 16 + 8);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(column.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int y = 1; y < 4; y++) level.setBlock(column.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        var feature = new com.arn.goodmod.worldgen.LegacyBuildingsFeature();
        var random = net.minecraft.util.RandomSource.create(7);
        int tribes = 0, attempts = 3000;
        for (int i = 0; i < attempts; i++) {
            if (feature.place(level, level.getChunkSource().getGenerator(), random, base)) tribes++;
        }
        var kobolds = level.getEntitiesOfClass(KoboldNpc.class, new net.minecraft.world.phys.AABB(column).inflate(40));
        kobolds.forEach(net.minecraft.world.entity.Entity::discard);
        String diagnostics = "";
        if (tribes == 0) {
            var tribesMethod = com.arn.goodmod.worldgen.GoodcraftWorldgen.class.getDeclaredMethod("tribes", ServerLevel.class); tribesMethod.setAccessible(true);
            var ground = com.arn.goodmod.worldgen.LegacyBuildingsFeature.class.getDeclaredMethod("ground", net.minecraft.world.level.WorldGenLevel.class, int.class, int.class); ground.setAccessible(true);
            int y = (int)ground.invoke(null, level, column.getX(), column.getZ());
            diagnostics = " tribesService=" + tribesMethod.invoke(null, level) + " groundY=" + y + " block=" + level.getBlockState(new BlockPos(column.getX(), y, column.getZ()))
                    + " flat=" + (level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource) + " config=" + com.arn.goodmod.GoodcraftMod.config();
        }
        require(tribes > 0 && kobolds.size() >= 4, "Tribe features succeeded " + tribes + " times in " + attempts + " chunks, " + kobolds.size() + " kobolds found near " + column
                + ", all kobolds: " + level.getEntities(GoodcraftEntities.KOBOLD, k -> true).stream().limit(3).map(k -> k.blockPosition() + (k.isRemoved() ? "removed" : "")).toList() + diagnostics);
        return tribes + " tribes in " + attempts + " chunk attempts, " + kobolds.size() + " kobolds";
    }

    private static String locate(ServerLevel level, String house) {
        var structure = net.minecraft.core.HolderSet.direct(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Identifier.fromNamespaceAndPath("sexmod", house))));
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, structure, BlockPos.ZERO, 150, false);
        require(found != null, "No " + house + " house within 2400 blocks");
        double distance = Math.sqrt(found.getFirst().distSqr(BlockPos.ZERO));
        return house + " at " + found.getFirst().toShortString() + " (" + (int)distance + " blocks)";
    }
}
