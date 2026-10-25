package com.arn.goodmod.block;

import com.arn.goodmod.registry.GoodcraftBlocks;
import java.lang.reflect.Proxy;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Registry-backed checks without a server, world, player, or EULA change. */
class PersistentFireBlockTest {
    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GoodcraftBlocks.initialize();
    }

    @Test
    void retainsLegacyRegistryIdAndVanillaFireLight() {
        assertEquals(Identifier.fromNamespaceAndPath("sexmod", "fire"),
                BuiltInRegistries.BLOCK.getKey(GoodcraftBlocks.FIRE));
        assertEquals(Blocks.FIRE.defaultBlockState().getLightEmission(),
                GoodcraftBlocks.FIRE.defaultBlockState().getLightEmission());
    }

    @Test
    void scheduledTickCannotReadOrModifyWorld() {
        var fire = (PersistentFireBlock) GoodcraftBlocks.FIRE;
        var state = fire.defaultBlockState().setValue(FireBlock.AGE, 9);
        // A null world makes any attempt to spread, schedule, age, or extinguish fail.
        assertDoesNotThrow(() -> fire.tick(state, null, BlockPos.ZERO, RandomSource.create(1)));
        assertEquals(9, state.getValue(FireBlock.AGE));
    }

    @Test
    void supportedNeighborChangePreservesModFireAndAge() {
        var fire = (PersistentFireBlock) GoodcraftBlocks.FIRE;
        var state = fire.defaultBlockState().setValue(FireBlock.AGE, 9);
        var updated = fire.updateShape(state, levelWithSupport(true), null, BlockPos.ZERO,
                Direction.NORTH, BlockPos.ZERO.north(), Blocks.AIR.defaultBlockState(), RandomSource.create(1));
        assertSame(GoodcraftBlocks.FIRE, updated.getBlock());
        assertEquals(9, updated.getValue(FireBlock.AGE));
    }

    @Test
    void losingSupportStillRemovesFire() {
        var fire = (PersistentFireBlock) GoodcraftBlocks.FIRE;
        var updated = fire.updateShape(fire.defaultBlockState(), levelWithSupport(false), null,
                BlockPos.ZERO, Direction.DOWN, BlockPos.ZERO.below(), Blocks.AIR.defaultBlockState(),
                RandomSource.create(1));
        assertTrue(updated.is(Blocks.AIR));
    }

    @Test
    void recognizesSharedFlammabilityButRejectsWaterloggedBlocks() {
        var fire = (PersistentFireBlock) GoodcraftBlocks.FIRE;
        assertTrue(fire.canBurn(Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(fire.canBurn(Blocks.STONE.defaultBlockState()));
        assertFalse(fire.canBurn(Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true)));
    }

    @Test
    void flammableSideSupportPreservesModFireAndUpdatesAttachment() {
        var fire = (PersistentFireBlock) GoodcraftBlocks.FIRE;
        var state = fire.defaultBlockState().setValue(FireBlock.AGE, 4);
        var level = levelWithBlock(BlockPos.ZERO.north(), Blocks.OAK_PLANKS.defaultBlockState());
        var updated = fire.updateShape(state, level, null, BlockPos.ZERO, Direction.NORTH,
                BlockPos.ZERO.north(), Blocks.OAK_PLANKS.defaultBlockState(), RandomSource.create(1));
        assertSame(GoodcraftBlocks.FIRE, updated.getBlock());
        assertEquals(4, updated.getValue(FireBlock.AGE));
        assertTrue(updated.getValue(FireBlock.NORTH));
    }

    private static LevelReader levelWithSupport(boolean supported) {
        return levelWithBlock(BlockPos.ZERO.below(),
                supported ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    private static LevelReader levelWithBlock(BlockPos blockPos, BlockState blockState) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockState")) {
                        BlockPos pos = (BlockPos) args[0];
                        return pos.equals(blockPos) ? blockState : Blocks.AIR.defaultBlockState();
                    }
                    throw new AssertionError("Unexpected world access during fire update: " + method.getName());
                });
    }
}
