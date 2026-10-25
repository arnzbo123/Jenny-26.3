package com.arn.goodmod.registry;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;

/** All 27 bi.java registry identities, using the original namespace. */
public final class GoodcraftEntities {
    private static boolean initialized;
    private static final EnumMap<NpcKind, EntityType<? extends GoodcraftNpc>> NPCS = new EnumMap<>(NpcKind.class);
    private static final EnumMap<NpcKind, EntityType<HumanoidNpc>> PLAYERS = new EnumMap<>(NpcKind.class);
    public static final EntityType<HumanoidNpc> JENNY = humanoid(NpcKind.JENNY);
    public static final EntityType<HumanoidNpc> ELLIE = humanoid(NpcKind.ELLIE);
    public static final EntityType<SlimeNpc> SLIME = npc(NpcKind.SLIME, SlimeNpc::new, MobCategory.CREATURE);
    public static final EntityType<HumanoidNpc> BIA = humanoid(NpcKind.BIA);
    public static final EntityType<BeeNpc> BEE = npc(NpcKind.BEE, BeeNpc::new, MobCategory.CREATURE);
    public static final EntityType<LunaNpc> LUNA = npc(NpcKind.LUNA, LunaNpc::new, MobCategory.CREATURE);
    public static final EntityType<AllieNpc> ALLIE = npc(NpcKind.ALLIE, AllieNpc::new, MobCategory.CREATURE);
    public static final EntityType<KoboldNpc> KOBOLD = npc(NpcKind.KOBOLD, KoboldNpc::new, MobCategory.CREATURE);
    public static final EntityType<GoblinNpc> GOBLIN = npc(NpcKind.GOBLIN, GoblinNpc::new, MobCategory.CREATURE);
    public static final EntityType<GalathNpc> GALATH = npc(NpcKind.GALATH, GalathNpc::new, MobCategory.CREATURE);
    public static final EntityType<ManglelieNpc> MANGLELIE = npc(NpcKind.MANGLELIE, ManglelieNpc::new, MobCategory.AMBIENT);
    public static final EntityType<KoboldEggEntity> KOBOLD_EGG = register("kobold_egg", EntityType.Builder.of(KoboldEggEntity::new, MobCategory.MISC).sized(0.5F, 0.5F));
    public static final EntityType<CustomModelEntity> CUSTOM_MODEL = register("custom_model", EntityType.Builder.of(CustomModelEntity::new, MobCategory.MISC).sized(0.1F, 0.1F));
    public static final EntityType<HumanoidNpc> PLAYER_JENNY = player(NpcKind.JENNY);
    public static final EntityType<HumanoidNpc> PLAYER_ELLIE = player(NpcKind.ELLIE);
    public static final EntityType<HumanoidNpc> PLAYER_SLIME = player(NpcKind.SLIME);
    public static final EntityType<HumanoidNpc> PLAYER_BIA = player(NpcKind.BIA);
    public static final EntityType<HumanoidNpc> PLAYER_BEE = player(NpcKind.BEE);
    public static final EntityType<HumanoidNpc> PLAYER_ALLIE = player(NpcKind.ALLIE);
    public static final EntityType<HumanoidNpc> PLAYER_KOBOLD = player(NpcKind.KOBOLD);
    public static final EntityType<HumanoidNpc> PLAYER_GOBLIN = player(NpcKind.GOBLIN);
    public static final EntityType<HumanoidNpc> PLAYER_LUNA = player(NpcKind.LUNA);
    public static final EntityType<HumanoidNpc> PLAYER_GALATH = player(NpcKind.GALATH);
    public static final EntityType<FriendlySlimeEntity> FRIENDLY_SLIME = register("friendly_slime", EntityType.Builder.of(FriendlySlimeEntity::new, MobCategory.CREATURE).sized(0.51000005F, 0.51000005F));
    public static final EntityType<LunaHookEntity> LUNA_HOOK = register("luna_hook", EntityType.Builder.of(LunaHookEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).noSave());
    public static final EntityType<EnergyBallEntity> ENERGY_BALL = register("energy_ball", EntityType.Builder.of(EnergyBallEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSave());
    public static final EntityType<PyrocinicalEntity> PYROCINICAL = register("pyrocinical", EntityType.Builder.of(PyrocinicalEntity::new, MobCategory.AMBIENT).sized(0.6F, 1.8F));
    private GoodcraftEntities() {}
    private static EntityType<HumanoidNpc> humanoid(NpcKind kind) { return npc(kind, (type, level) -> new HumanoidNpc(type, level, kind, false), MobCategory.CREATURE); }
    private static <T extends GoodcraftNpc> EntityType<T> npc(NpcKind kind, EntityType.EntityFactory<T> factory, MobCategory category) {
        EntityType<T> type = register(kind.id(), EntityType.Builder.of(factory, category).sized(kind.width(), kind.height())); NPCS.put(kind, type); return type;
    }
    private static EntityType<HumanoidNpc> player(NpcKind kind) {
        EntityType<HumanoidNpc> type = register("player_" + kind.id(), EntityType.Builder.<HumanoidNpc>of((entityType, level) -> new HumanoidNpc(entityType, level, kind, true), MobCategory.MISC).sized(0.01F, 0.01F).clientTrackingRange(7).dontTrackDeltas().noSave()); PLAYERS.put(kind, type); return type;
    }
    private static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
        Identifier id = Identifier.fromNamespaceAndPath(GoodcraftMod.MOD_ID, path);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        // Modern tracking units are chunks: 4 covers legacy 50 blocks; forms use 7 for 100.
        if (!path.startsWith("player_")) builder.clientTrackingRange(4);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.updateInterval(1).build(key));
    }
    public static Map<NpcKind, EntityType<? extends GoodcraftNpc>> npcTypes() { return Collections.unmodifiableMap(NPCS); }
    public static Map<NpcKind, EntityType<HumanoidNpc>> playerTypes() { return Collections.unmodifiableMap(PLAYERS); }
    public static GoodcraftNpc create(NpcKind kind, ServerLevel level) { return NPCS.get(kind).create(level, EntitySpawnReason.TRIGGERED); }
    public static GoodcraftNpc createPlayerForm(NpcKind kind, ServerLevel level, UUID player) {
        EntityType<HumanoidNpc> type = PLAYERS.get(kind); if (type == null) throw new IllegalArgumentException("No player form for " + kind);
        var npc = type.create(level, EntitySpawnReason.TRIGGERED); if (npc != null) { npc.setAssociatedPlayerUUID(player); npc.setOwnerUUID(player); } return npc;
    }
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        NPCS.forEach((kind, type) -> FabricDefaultAttributeRegistry.register(type, GoodcraftNpc.createAttributes(kind)));
        PLAYERS.forEach((kind, type) -> FabricDefaultAttributeRegistry.register(type, GoodcraftNpc.createAttributes(NpcKind.JENNY)));
        FabricDefaultAttributeRegistry.register(KOBOLD_EGG, Mob.createMobAttributes());
        FabricDefaultAttributeRegistry.register(CUSTOM_MODEL, Mob.createMobAttributes());
        FabricDefaultAttributeRegistry.register(FRIENDLY_SLIME, Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.MOVEMENT_SPEED, 0.3));
        FabricDefaultAttributeRegistry.register(ENERGY_BALL, Mob.createMobAttributes());
        FabricDefaultAttributeRegistry.register(PYROCINICAL, Mob.createMobAttributes());
        SpawnPlacements.register(SLIME, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
        // bi: forests as in the original; elsewhere bee girls only appear beside a bee nest or hive.
        SpawnPlacements.register(BEE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, reason, pos, random) ->
                Mob.checkMobSpawnRules(type, level, reason, pos, random) && (level.getBiome(pos).is(net.minecraft.world.level.biome.Biomes.FOREST)
                        || com.arn.goodmod.worldgen.BeeNestResidentsFeature.nearNest(level, pos)));
        SpawnPlacements.register(PYROCINICAL, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, reason, pos, random) -> Mob.checkMobSpawnRules(type, level, reason, pos, random) && random.nextInt(100) < 1 && random.nextInt(100) < 10);
        SpawnPlacements.register(MANGLELIE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, reason, pos, random) -> Mob.checkMobSpawnRules(type, level, reason, pos, random) && EncounterSpawnHistory.get(level.getLevel()).permitsManglelie(pos));
    }
}
