package com.arn.goodmod;

import com.arn.goodmod.config.GoodcraftConfig;
import com.arn.goodmod.registry.GoodcraftBlocks;
import com.arn.goodmod.registry.GoodcraftSounds;
import com.arn.goodmod.registry.GoodcraftEntities;
import com.arn.goodmod.registry.GoodcraftItems;
import com.arn.goodmod.registry.GoodcraftLoot;
import com.arn.goodmod.registry.GoodcraftEffects;
import com.arn.goodmod.menu.NpcMenus;
import com.arn.goodmod.network.NpcNetworking;
import com.arn.goodmod.network.TribeNetworking;
import com.arn.goodmod.command.GoodcraftCommands;
import com.arn.goodmod.world.GalathSummons;
import com.arn.goodmod.world.PlayerForms;
import com.arn.goodmod.worldgen.GoodcraftSpawns;
import com.arn.goodmod.worldgen.GoodcraftWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric bootstrap. Registry identity is retained from the original JAR. */
public final class GoodcraftMod implements ModInitializer {
    public static final String MOD_ID = "sexmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static GoodcraftConfig config = GoodcraftConfig.DEFAULT;

    @Override
    public void onInitialize() {
        config = GoodcraftConfig.load(FabricLoader.getInstance().getConfigDir().resolve("sexmod.json"), LOGGER);
        GoodcraftBlocks.initialize();
        GoodcraftSounds.initialize();
        GoodcraftEntities.initialize();
        GoodcraftItems.initialize();
        GoodcraftLoot.initialize();
        GoodcraftEffects.initialize();
        NpcMenus.initialize();
        NpcNetworking.initialize();
        TribeNetworking.initialize();
        GoodcraftCommands.initialize();
        GoodcraftSpawns.initialize();
        GoodcraftWorldgen.initialize();
        GalathSummons.initialize();
        PlayerForms.initialize();
        LOGGER.warn("Goodcraft migration development build: gameplay migration is incomplete. See MIGRATION.md.");
    }

    public static GoodcraftConfig config() {
        return config;
    }
}
