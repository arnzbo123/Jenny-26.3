package com.arn.goodmod.worldtest;

import com.arn.goodmod.entity.AllieNpc;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.item.LampItem;
import com.arn.goodmod.menu.NpcInventoryMenu;
import com.arn.goodmod.menu.NpcMenuData;
import com.arn.goodmod.menu.NpcMenuView;
import com.arn.goodmod.registry.GoodcraftEntities;
import com.arn.goodmod.registry.GoodcraftItems;
import com.arn.goodmod.world.KoboldTribes;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Development-only fixture. This source set is never packaged in the distributed mod. */
public final class WorldChecks implements ModInitializer {
    private static final List<Map<String,Object>> RESULTS = new ArrayList<>();
    private static final List<Entity> CREATED = new ArrayList<>();
    private static final UUID OWNER = UUID.fromString("2c15ad19-2026-4263-8263-263202600001");
    private static final BlockPos ORIGIN = new BlockPos(0, 200, 0);
    private interface Check { void run() throws Exception; }
    @Override public void onInitialize() {
        if (Boolean.getBoolean("goodcraft.runWorldTests")) ServerLifecycleEvents.SERVER_STARTED.register(WorldChecks::run);
    }
    private static void check(String name, Check work) {
        var result = new LinkedHashMap<String,Object>(); result.put("name", name);
        try { work.run(); result.put("passed", true); }
        catch (Throwable failure) {
            result.put("passed", false); result.put("error", failure.toString());
            LoggerFactory.getLogger("sexmod_world_test").error("World test failed: " + name, failure);
        }
        RESULTS.add(result);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayer.get(level, new GameProfile(OWNER, "PortTest"));
        try {
            level.getChunk(0, 0);
            for (int x = -2; x <= 15; x++) for (int z = -2; z <= 15; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            }
            player.setPos(Vec3.atBottomCenterOf(ORIGIN)); level.addNewPlayer(player);
            check("natural-spawner-mixin-loads", () -> Class.forName("net.minecraft.world.level.NaturalSpawner"));
            BuiltInRegistries.ENTITY_TYPE.entrySet().stream().filter(entry -> entry.getKey().identifier().getNamespace().equals("sexmod")).forEach(entry -> {
                check("entity-create-save-load:" + entry.getKey().identifier().getPath(), () -> {
                    Entity entity = entry.getValue().create(level, EntitySpawnReason.COMMAND);
                    require(entity != null, "Entity factory returned null");
                    entity.setPos(Vec3.atBottomCenterOf(ORIGIN.offset(2, 0, 2)));
                    if (entity instanceof GoodcraftNpc npc) {
                        npc.setOwnerUUID(OWNER); npc.setHomePosition(ORIGIN.offset(1,0,1));
                        if (npc.supportsEquipment()) npc.getEquipmentInventory().setItem(2, new ItemStack(Items.DIAMOND_HELMET));
                        if (npc.supportsStorage()) npc.getStorageInventory().setItem(4, new ItemStack(Items.EMERALD,17));
                        if (npc instanceof AllieNpc) npc.setAnimationState("SUMMON_WAIT");
                    }
                    var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    entity.saveWithoutId(output);
                    Entity restored = entry.getValue().create(level, EntitySpawnReason.LOAD);
                    require(restored != null, "Reload factory returned null");
                    restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
                    if (entity instanceof GoodcraftNpc before && restored instanceof GoodcraftNpc after) {
                        require(after.getOwnerUUID().filter(OWNER::equals).isPresent(), "Owner was lost");
                        require(before.getHomePosition().equals(after.getHomePosition()), "Home was lost");
                        if (before.supportsEquipment()) require(after.getEquipmentInventory().getItem(2).is(Items.DIAMOND_HELMET), "Equipment was lost");
                        if (before.supportsStorage()) require(after.getStorageInventory().getItem(4).getCount()==17, "Storage was lost");
                    }
                    require(level.addFreshEntity(entity), "World rejected fresh entity"); CREATED.add(entity);
                    entity.tick(); entity.discard(); restored.discard();
                });
            });
            check("npc-inventory-transfer-and-owner-access", () -> {
                var npc = GoodcraftEntities.JENNY.create(level, EntitySpawnReason.COMMAND);
                npc.setPos(player.position().add(1,0,0)); npc.setOwnerUUID(OWNER); level.addFreshEntity(npc); CREATED.add(npc);
                var data = new NpcMenuData(npc.getId(), npc.getUUID(), NpcMenuView.EQUIPMENT, false);
                var menu = com.arn.goodmod.menu.WorldMenuFixture.create(1, player.getInventory(), data, npc);
                require(menu.stillValid(player), "Owner cannot open nearby NPC inventory");
                npc.getEquipmentInventory().setItem(2, new ItemStack(Items.DIAMOND_HELMET));
                int before = player.getInventory().countItem(Items.DIAMOND_HELMET);
                menu.quickMoveStack(player, 2);
                require(npc.getEquipmentInventory().getItem(2).isEmpty(), "Shift transfer left original stack");
                require(player.getInventory().countItem(Items.DIAMOND_HELMET)==before+1, "Shift transfer lost/duplicated item");
                npc.setOwnerUUID(UUID.randomUUID()); require(!menu.stillValid(player), "Foreign player retained NPC access");
                npc.discard();
            });
            check("lamp-first-summon-wish-count-and-owner", () -> {
                ItemStack lamp = GoodcraftItems.ALLIES_LAMP.getDefaultInstance(); player.setItemInHand(InteractionHand.MAIN_HAND, lamp);
                require(GoodcraftItems.ALLIES_LAMP.use(level, player, InteractionHand.MAIN_HAND).consumesAction(), "Lamp use failed");
                for (int tick=0;tick<97;tick++) GoodcraftItems.ALLIES_LAMP.inventoryTick(lamp,level,player,EquipmentSlot.MAINHAND);
                require(LampItem.remainingWishes(lamp)==2, "First wish was not charged exactly once");
                String summonedId = lamp.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getStringOr("sexmodAllieID", "");
                var allie = (AllieNpc)level.getEntity(UUID.fromString(summonedId));
                require(allie != null, "Lamp did not store the actual spawned NPC identity"); CREATED.add(allie);
                require(allie.getAnimationState().equals("SUMMON"), "First wish has wrong summon animation");
                for(int tick=0;tick<244;tick++) allie.tick();
                require(allie.canRequestWish(player), "Summon never reached wish-selection state");
                require(allie.grantWealthWish(player), "Wealth request was rejected");
                allie.tick(); allie.tick();
                require(!allie.grantWealthWish(player), "Repeated request could duplicate rewards");
                for (var reward : List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT)) {
                    int count = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, allie.getBoundingBox().inflate(3))
                            .stream().filter(item -> item.getItem().is(reward)).mapToInt(item -> item.getItem().getCount()).sum();
                    require(count >= 1 && count <= 2, "Wealth reward count is outside the original 1–2 range");
                }
                allie.discard();
                for (int wish = 2; wish <= 3; wish++) {
                    require(GoodcraftItems.ALLIES_LAMP.use(level,player,InteractionHand.MAIN_HAND).consumesAction(), "Remaining wish rejected");
                    for (int tick=0;tick<97;tick++) GoodcraftItems.ALLIES_LAMP.inventoryTick(lamp,level,player,EquipmentSlot.MAINHAND);
                    String nextId=lamp.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getStringOr("sexmodAllieID", "");
                    var next=(AllieNpc)level.getEntity(UUID.fromString(nextId));require(next!=null,"Later wish did not summon");CREATED.add(next);
                    require(next.getAnimationState().equals("SUMMON_NORMAL"),"Later wish incorrectly repeats first summon");next.discard();
                }
                require(LampItem.remainingWishes(lamp)==0,"Third wish did not exhaust lamp");
                require(!GoodcraftItems.ALLIES_LAMP.use(level,player,InteractionHand.MAIN_HAND).consumesAction(),"Exhausted lamp allowed a fourth wish");
            });
            check("tribe-spawn-shared-identity-and-persistence", () -> {
                var state=KoboldTribes.get(level); var members=state.spawn(level,player.position().add(4,0,4)); CREATED.addAll(members);
                require(members.size()==4,"Original tribe must contain four members");
                UUID tribe=members.getFirst().getTribeId().orElseThrow();
                require(members.stream().allMatch(member->member.getTribeId().filter(tribe::equals).isPresent()),"Tribe IDs differ");
                require(state.find(tribe).orElseThrow().members().size()==4,"Persistent tribe lost members");
                members.forEach(Entity::discard);
            });
            RESULTS.addAll(com.arn.goodmod.entity.EntityWorldChecks.run(level, player));
            RESULTS.addAll(com.arn.goodmod.entity.SceneWorldChecks.run(level, player, ORIGIN));
            for(String name:List.of("jenny","bia","ellie","luna","goblin")) check("structure-place:"+name,()->{
                var template=level.getStructureTemplateManager().get(Identifier.fromNamespaceAndPath("sexmod",name)).orElseThrow();
                BlockPos pos=ORIGIN.offset(32+List.of("jenny","bia","ellie","luna","goblin").indexOf(name)*48,0,32);
                require(template.placeInWorld(level,pos,pos,new StructurePlaceSettings().setIgnoreEntities(false),level.getRandom(),2),"Template placement failed");
            });
            check("bee-nest-resident", () -> {
                BlockPos nest = ORIGIN.offset(5, 3, 5);
                level.setBlock(nest.below(3), Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(nest.below(2), Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(nest.below(), Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(nest, Blocks.BEE_NEST.defaultBlockState(), 2);
                require(com.arn.goodmod.worldgen.BeeNestResidentsFeature.nearNest(level, ORIGIN.offset(3, 0, 3)), "Spawn rule did not see the nest");
                var feature = new com.arn.goodmod.worldgen.BeeNestResidentsFeature();
                boolean placed = false;
                // The feature settles a resident a third of the time; retry with one random sequence
                // (fresh small seeds all start near 0.73 and would never pass the roll).
                var random = net.minecraft.util.RandomSource.create(42);
                for (int attempt = 0; attempt < 40 && !placed; attempt++) {
                    placed = feature.place(level, level.getChunkSource().getGenerator(), random, ORIGIN);
                }
                require(placed, "No bee girl settled beside the nest");
                var bees = level.getEntitiesOfClass(com.arn.goodmod.entity.BeeNpc.class, new net.minecraft.world.phys.AABB(nest).inflate(4, 10, 4));
                require(!bees.isEmpty(), "Settled bee girl is not near the nest");
                bees.forEach(Entity::discard);
            });
            check("locate-goblin-lair", () -> {
                var lairs = net.minecraft.core.HolderSet.direct(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Identifier.fromNamespaceAndPath("sexmod", "goblin_lair"))));
                var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, lairs, BlockPos.ZERO, 100, false);
                require(found != null, "No goblin lair within locate range");
                var chunk = new net.minecraft.world.level.ChunkPos(found.getFirst().getX() >> 4, found.getFirst().getZ() >> 4);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk(chunk.x() + x, chunk.z() + z);
                var start = level.structureManager().getStartForStructure(found.getSecond().value(), level.getChunk(chunk.x(), chunk.z()));
                require(start != null && start.isValid(), "Located lair has no generated start");
                var box = start.getBoundingBox();
                long blocks = BlockPos.betweenClosedStream(box).filter(pos -> !level.getBlockState(pos).isAir()).count();
                var residents = level.getEntitiesOfClass(GoodcraftNpc.class, net.minecraft.world.phys.AABB.of(box).inflate(2),
                        npc -> npc.getNpcKind() == NpcKind.GOBLIN);
                LoggerFactory.getLogger("sexmod_world_test").info("Goblin lair {} with {} solid blocks and {} resident(s)", box, blocks, residents.size());
                require(blocks > 0, "Located lair generated no blocks");
                require(!residents.isEmpty(), "Lair has no resident goblin");
            });
            check("locate-buildings", () -> {
                // The same search /locate structure #sexmod:buildings runs; it computes structure starts only.
                var tag = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Identifier.fromNamespaceAndPath("sexmod", "buildings")));
                var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, tag, BlockPos.ZERO, 100, false);
                require(found != null, "No Goodcraft building within locate range");
                LoggerFactory.getLogger("sexmod_world_test").info("Nearest building: {} at {}", found.getSecond().unwrapKey().orElseThrow(), found.getFirst());
                var chunk = new net.minecraft.world.level.ChunkPos(found.getFirst().getX() >> 4, found.getFirst().getZ() >> 4);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk(chunk.x() + x, chunk.z() + z);
                var start = level.structureManager().getStartForStructure(found.getSecond().value(), level.getChunk(chunk.x(), chunk.z()));
                require(start != null && start.isValid(), "Located building has no generated start");
                var box = start.getBoundingBox();
                long blocks = net.minecraft.core.BlockPos.betweenClosedStream(box).filter(pos -> !level.getBlockState(pos).isAir()).count();
                LoggerFactory.getLogger("sexmod_world_test").info("Generated building box {} with {} solid blocks", box, blocks);
                require(blocks > 0, "Located building generated no blocks");
            });
        } catch(Throwable setup) { check("fixture-setup", ()->{throw new IllegalStateException(setup);}); }
        finally {
            CREATED.forEach(Entity::discard); level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            try {
                Path path=Path.of("..","build","reports","world-tests.json"); Files.createDirectories(path.getParent());
                Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
            } catch(Exception output) { LoggerFactory.getLogger("sexmod_world_test").error("Could not save test report",output); }
            long failed=RESULTS.stream().filter(result->!Boolean.TRUE.equals(result.get("passed"))).count();
            LoggerFactory.getLogger("sexmod_world_test").info("GOODCRAFT_WORLD_TESTS completed={} failed={}",RESULTS.size(),failed);
            server.halt(false);
        }
    }
}
