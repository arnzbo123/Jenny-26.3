package com.arn.goodmod.registry;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.block.PersistentFireBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class GoodcraftBlocks {
    private static final ResourceKey<Block> FIRE_KEY = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(GoodcraftMod.MOD_ID, "fire"));
    public static final Block FIRE = Registry.register(BuiltInRegistries.BLOCK, FIRE_KEY,
            new PersistentFireBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FIRE).setId(FIRE_KEY)));

    private GoodcraftBlocks() {}

    public static void initialize() {
        // Loads this class during the Fabric registration phase.
    }
}
