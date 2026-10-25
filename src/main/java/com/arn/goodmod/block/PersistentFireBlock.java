package com.arn.goodmod.block;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Ports legacy dw: fire with its scheduled spreading/extinguishing tick disabled. */
public final class PersistentFireBlock extends FireBlock {
    public PersistentFireBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Deliberately empty in the original mod: this fire does not age or spread.
    }

    @Override
    protected boolean canBurn(BlockState state) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        // Forge 1.12 delegated to the target block's flammability. A newly created
        // vanilla FireBlock has an empty local map, so consult Fabric's shared registry.
        FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
        return entry != null && entry.getIgniteOdds() > 0;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        // Vanilla FireBlock.updateShape creates vanilla fire through a static helper.
        // Keep this block's identity so a neighbor change cannot re-enable spreading.
        return getStateForPlacement(level, pos).setValue(AGE, state.getValue(AGE));
    }
}
