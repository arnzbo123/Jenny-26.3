package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.entity.KoboldEggEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public final class GoodcraftEggRenderer extends GeoEntityRenderer<KoboldEggEntity, GoodcraftNpcRenderState> {
    public GoodcraftEggRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return GoodcraftNpcModel.id("kobold/koboldegg"); }
            @Override public Identifier getTextureResource(GeoRenderState state) { return GoodcraftNpcModel.id("textures/entity/kobold/koboldegg.png"); }
            @Override public Identifier getAnimationResource(KoboldEggEntity entity) { return GoodcraftNpcModel.id("kobold/egg"); }
        });
        withRenderLayer(new GoodcraftBoneColors<>(this));
    }
    @Override public GoodcraftNpcRenderState createRenderState(KoboldEggEntity egg, Void related) { return new GoodcraftNpcRenderState(); }
    @Override public void addRenderData(KoboldEggEntity egg, Void related, GoodcraftNpcRenderState state, float partialTick) {
        state.kind = "egg";
        int color = 0xffc494cf;
        for (var entry : AnimationCatalog.APPEARANCE.getAsJsonArray("kobold")) {
            var palette = entry.getAsJsonObject();
            if (!palette.get("name").getAsString().equals(egg.getEggColor())) continue;
            var rgb = palette.getAsJsonArray("rgb");
            color = 0xff000000 | rgb.get(0).getAsInt() << 16 | rgb.get(1).getAsInt() << 8 | rgb.get(2).getAsInt();
        }
        state.boneColors = Map.of("shell", 0xffdfce9b, "colorSpots", color);
    }
}
