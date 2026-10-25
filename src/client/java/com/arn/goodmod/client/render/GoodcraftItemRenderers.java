package com.arn.goodmod.client.render;

import com.arn.goodmod.item.LegacyAnimatedItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

public final class GoodcraftItemRenderers {
    private GoodcraftItemRenderers() {}

    public static void initialize() {
        LegacyAnimatedItem.setRendererFactory(item -> new GeoRenderProvider() {
            private GeoItemRenderer<LegacyAnimatedItem> renderer;
            @Override public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new ItemModel(item.getVisualId()));
                return renderer;
            }
        });
    }

    private static final class ItemModel extends GeoModel<LegacyAnimatedItem> {
        private final String model, texture, animation;
        ItemModel(String id) {
            switch (id) {
                case "allies_lamp" -> { model = "allie/lamp"; texture = "textures/entity/allie/lamp.png"; animation = "allie/lamp"; }
                case "dragon_staff" -> { model = "kobold/staff"; texture = "textures/entity/kobold/staff.png"; animation = "kobold/staff"; }
                case "kobold_egg_item" -> { model = "kobold/koboldegg"; texture = "textures/entity/kobold/koboldegg.png"; animation = "kobold/egg"; }
                case "galath_coin" -> { model = "galath/galath_coin"; texture = "textures/items/galath_coin/galath_coin.png"; animation = "galath/galath_coin"; }
                default -> throw new IllegalArgumentException("No original animated item model: " + id);
            }
        }
        @Override public Identifier getModelResource(GeoRenderState state) { return GoodcraftNpcModel.id(model); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return GoodcraftNpcModel.id(texture); }
        @Override public Identifier getAnimationResource(LegacyAnimatedItem item) { return GoodcraftNpcModel.id(animation); }
    }
}
