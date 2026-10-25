package com.arn.goodmod.worldgen;

import com.arn.goodmod.GoodcraftMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.Vec3;

/** g3's kobold tribe attempt; houses and goblin lairs are {@link LegacyBuildingStructure} and {@link GoblinLairStructure}. */
public final class LegacyBuildingsFeature implements Feature {
    public static final MapCodec<LegacyBuildingsFeature> CODEC = MapCodec.unit(LegacyBuildingsFeature::new);
    private static final double TRIBE_CHANCE = 0.01;
    @Override public MapCodec<? extends Feature> codec() { return CODEC; }
    @Override public boolean place(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!GoodcraftMod.config().shouldGenBuildings() || generator instanceof FlatLevelSource) return false;
        int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
        boolean placed = false;
        // g3 used 0.004 per chunk; tribes were too rare to find, so the port raises it to 0.01.
        if (random.nextDouble() <= TRIBE_CHANCE) {
            int x = chunkX * 16 + 8, z = chunkZ * 16 + 8, y = ground(world, x, z);
            if (y >= world.getMinY() && world.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
                var tribes = GoodcraftWorldgen.tribes(world.getLevel());
                if (tribes != null) placed = !tribes.spawn(world, new Vec3(x + .5, y + 1, z + .5)).isEmpty();
            }
        }
        return placed;
    }
    /** cj.a searches these six ground blocks, excluding leaves, logs and buildings above the terrain. */
    private static int ground(WorldGenLevel world, int x, int z) {
        // The _WG heightmap only exists while a chunk generates; a finished chunk answers with WORLD_SURFACE.
        int top = Math.min(world.getMaxY(), Math.max(world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z),
                world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z)));
        for (int y = top; y >= world.getMinY(); y--) {
            var state = world.getBlockState(new BlockPos(x, y, z));
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                    || state.is(Blocks.WATER) || state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) return y;
        }
        return world.getMinY() - 1;
    }
}
