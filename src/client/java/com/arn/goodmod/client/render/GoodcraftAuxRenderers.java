package com.arn.goodmod.client.render;

import com.arn.goodmod.registry.GoodcraftEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class GoodcraftAuxRenderers {
    private GoodcraftAuxRenderers() {}
    public static void initialize() {
        EntityRendererRegistry.register(GoodcraftEntities.KOBOLD_EGG, GoodcraftEggRenderer::new);
        EntityRendererRegistry.register(GoodcraftEntities.FRIENDLY_SLIME, FriendlySlimeRenderer::new);
        EntityRendererRegistry.register(GoodcraftEntities.LUNA_HOOK, LunaHookRenderer::new);
        EntityRendererRegistry.register(GoodcraftEntities.ENERGY_BALL, LegacyBillboardRenderer::new);
        EntityRendererRegistry.register(GoodcraftEntities.PYROCINICAL, LegacyBillboardRenderer::new);
        EntityRendererRegistry.register(GoodcraftEntities.CUSTOM_MODEL, GoodcraftCustomModelRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> GoodcraftCustomModels.reload());
    }
}
