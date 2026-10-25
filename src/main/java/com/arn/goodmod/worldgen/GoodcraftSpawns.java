package com.arn.goodmod.worldgen;

import com.arn.goodmod.registry.GoodcraftEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biomes;

/** bi.java spawn weights. Removed swamp/forest hill biomes share their modern base biome. */
public final class GoodcraftSpawns {
    private GoodcraftSpawns() {}
    public static void initialize() {
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP), MobCategory.CREATURE,
                GoodcraftEntities.SLIME, 10, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.FOREST), MobCategory.CREATURE,
                GoodcraftEntities.BEE, 5, 1, 1);
        // Port addition: bee-nest biomes, gated by the nest check in the bee's spawn placement.
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(GoodcraftWorldgen.BEE_NEST_BIOMES), MobCategory.CREATURE,
                GoodcraftEntities.BEE, 5, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.NETHER_WASTES), MobCategory.AMBIENT,
                GoodcraftEntities.PYROCINICAL, 3, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.NETHER_WASTES), MobCategory.AMBIENT,
                GoodcraftEntities.MANGLELIE, 5, 1, 1);
    }
}
