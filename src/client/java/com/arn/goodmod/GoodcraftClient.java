package com.arn.goodmod;

import net.fabricmc.api.ClientModInitializer;
import com.arn.goodmod.client.GoodcraftMolang;
import com.arn.goodmod.client.render.GoodcraftItemRenderers;
import com.arn.goodmod.client.render.GoodcraftNpcRenderer;
import com.arn.goodmod.client.menu.NpcMenuScreens;
import com.arn.goodmod.client.command.GoodcraftClientCommands;
import com.arn.goodmod.registry.GoodcraftEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client entry point, isolated from dedicated-server class loading. */
public final class GoodcraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GoodcraftMolang.register();
        com.arn.goodmod.client.render.GoodcraftAnimationSounds.initialize();
        GoodcraftItemRenderers.initialize();
        com.arn.goodmod.client.render.GoodcraftAuxRenderers.initialize();
        GoodcraftEntities.npcTypes().values().forEach(type -> EntityRendererRegistry.register(type, GoodcraftNpcRenderer::new));
        GoodcraftEntities.playerTypes().values().forEach(type -> EntityRendererRegistry.register(type, GoodcraftNpcRenderer::new));
        NpcMenuScreens.initialize();
        com.arn.goodmod.client.scene.SceneClient.initialize();
        GoodcraftClientCommands.initialize();
        GoodcraftMod.LOGGER.info("Goodcraft Fabric client initialized");
    }
}
