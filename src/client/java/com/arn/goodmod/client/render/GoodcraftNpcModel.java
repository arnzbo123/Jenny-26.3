package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

public final class GoodcraftNpcModel extends GeoModel<GoodcraftNpc> {
    @Override public Identifier getModelResource(GeoRenderState renderState) {
        var state = (GoodcraftNpcRenderState)renderState;
        var models = AnimationCatalog.MODELS.getAsJsonObject(state.kind).getAsJsonArray("models");
        int variant = state.modelVariant;
        if (!state.playerForm && (state.kind.equals("allie") || state.kind.equals("slime"))) variant = variant == 1 ? 2 : 0;
        if (state.combinedModel && (state.kind.equals("galath") || state.kind.equals("manglelie"))) variant = 2;
        variant = Math.clamp(variant, 0, models.size() - 1);
        return id(models.get(variant).getAsString());
    }

    @Override public Identifier getTextureResource(GeoRenderState renderState) {
        return id(AnimationCatalog.MODELS.getAsJsonObject(((GoodcraftNpcRenderState)renderState).kind).get("texture").getAsString());
    }

    @Override public Identifier getAnimationResource(GoodcraftNpc npc) {
        return id(AnimationCatalog.MODELS.getAsJsonObject(npc.getNpcKind().id()).get("animation").getAsString());
    }

    static Identifier id(String path) { return Identifier.fromNamespaceAndPath("sexmod", path); }
}
