package com.arn.goodmod.client.render;

import com.arn.goodmod.entity.FriendlySlimeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

public final class FriendlySlimeRenderer extends AbstractCubeMobRenderer<FriendlySlimeEntity, SlimeRenderState, SlimeModel> {
    public FriendlySlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)));
        addLayer(new SlimeOuterLayer(this, context.getModelSet()));
    }
    @Override public SlimeRenderState createRenderState() { return new SlimeRenderState(); }
    @Override public Identifier getTextureLocation(SlimeRenderState state) { return SlimeRenderer.SLIME_LOCATION; }
    @Override protected void scale(SlimeRenderState state, PoseStack pose) { downscaleSlightly(pose); applySizeAndSquish(state, pose); }
}
