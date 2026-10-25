package com.arn.goodmod.worldgen;

import com.arn.goodmod.registry.GoodcraftEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Port addition (1.12 had no beehives): bee girls settle beside naturally generated bee nests.
 * Runs after vegetation, so the chunk's trees and their nests already exist.
 */
public final class BeeNestResidentsFeature implements Feature {
    public static final MapCodec<BeeNestResidentsFeature> CODEC = MapCodec.unit(BeeNestResidentsFeature::new);
    private static final float CHANCE = 1 / 3f;

    @Override public MapCodec<? extends Feature> codec() { return CODEC; }

    @Override public boolean place(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        BlockPos nest = findNest(world, origin.getX() & ~15, origin.getZ() & ~15);
        if (nest == null || random.nextFloat() >= CHANCE) return false;
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = nest.getX() + random.nextInt(5) - 2, z = nest.getZ() + random.nextInt(5) - 2;
            BlockPos feet = new BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!world.ensureCanWrite(feet) || Math.abs(feet.getY() - nest.getY()) > 8) continue;
            if (!world.getBlockState(feet.below()).isSolid() || !world.isEmptyBlock(feet) || !world.isEmptyBlock(feet.above())) continue;
            var bee = GoodcraftEntities.BEE.create(world.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
            if (bee == null) return false;
            bee.setPos(x + .5, feet.getY(), z + .5);
            bee.setYRot(random.nextFloat() * 360);
            bee.setHomePosition(feet);
            return world.addFreshEntity(bee);
        }
        return false;
    }

    /** The first bee nest or beehive in the chunk, searching the top 16 blocks of each column. */
    private static BlockPos findNest(WorldGenLevel world, int minX, int minZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            int top = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = top; y > top - 16 && y >= world.getMinY(); y--) {
                if (world.getBlockState(pos.set(x, y, z)).is(BlockTags.BEEHIVES)) return pos.immutable();
            }
        }
        return null;
    }

    /** Natural-spawn rule outside the original forest: a nest or hive within 8 blocks horizontally, up to 10 above. */
    public static boolean nearNest(LevelReader level, BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-8, -2, -8), pos.offset(8, 10, 8))) {
            // Never load a neighbouring chunk just to answer a spawn check.
            if (!level.hasChunk(check.getX() >> 4, check.getZ() >> 4)) continue;
            if (level.getBlockState(check).is(BlockTags.BEEHIVES)) return true;
        }
        return false;
    }
}
