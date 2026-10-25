package com.arn.goodmod.worldgen;

import com.arn.goodmod.world.KoboldTribes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class GoodcraftWorldgen {
    public static final StructureType<LegacyBuildingStructure> LEGACY_BUILDING = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            Identifier.fromNamespaceAndPath("sexmod", "legacy_building"), () -> LegacyBuildingStructure.CODEC);
    public static final StructurePieceType LEGACY_BUILDING_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath("sexmod", "legacy_building"), (StructurePieceType.StructureTemplateType) LegacyBuildingPiece::new);
    public static final StructureType<GoblinLairStructure> GOBLIN_LAIR = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            Identifier.fromNamespaceAndPath("sexmod", "goblin_lair"), () -> GoblinLairStructure.CODEC);
    public static final StructurePieceType GOBLIN_LAIR_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath("sexmod", "goblin_lair"), (StructurePieceType.StructureTemplateType) GoblinLairPiece::new);
    /** Biomes whose trees can carry bee nests, besides plain forest. */
    @SuppressWarnings("unchecked")
    static final ResourceKey<net.minecraft.world.level.biome.Biome>[] BEE_NEST_BIOMES = new ResourceKey[]{
            Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FLOWER_FOREST, Biomes.CHERRY_GROVE,
            Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST};
    private static final Map<ServerLevel, KoboldTribes> TRIBES = new ConcurrentHashMap<>();
    private GoodcraftWorldgen() {}
    static KoboldTribes tribes(ServerLevel level) { return TRIBES.get(level); }
    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("sexmod", "legacy_buildings"), LegacyBuildingsFeature.CODEC);
        ServerLevelEvents.LOAD.register((server, level) -> {
            TRIBES.put(level, KoboldTribes.get(level));
        });
        ServerLevelEvents.UNLOAD.register((server, level) -> TRIBES.remove(level));
        BiomeModifications.addFeature(BiomeSelectors.all(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("sexmod", "legacy_buildings")));
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("sexmod", "bee_nest_residents"), BeeNestResidentsFeature.CODEC);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.FOREST).or(BiomeSelectors.includeByKey(BEE_NEST_BIOMES)),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("sexmod", "bee_nest_residents")));
    }
}
