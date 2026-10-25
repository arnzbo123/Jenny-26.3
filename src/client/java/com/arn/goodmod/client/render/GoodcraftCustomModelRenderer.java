package com.arn.goodmod.client.render;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.CustomModelEntity;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/** Renders a supplied local addon carrier. NPC owner bone attachments are separate layers. */
final class GoodcraftCustomModelRenderer extends GeoEntityRenderer<CustomModelEntity, GoodcraftNpcRenderState> {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    GoodcraftCustomModelRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState value) {
                String name = ((GoodcraftNpcRenderState)value).action;
                return name.equals("cross") ? GoodcraftNpcModel.id("cross") : GoodcraftCustomModels.get(name).id();
            }
            @Override public Identifier getTextureResource(GeoRenderState value) {
                String name = ((GoodcraftNpcRenderState)value).action;
                return name.equals("cross") ? GoodcraftNpcModel.id("textures/cross.png") : GoodcraftCustomModels.get(name).texture();
            }
            @Override public Identifier getAnimationResource(CustomModelEntity entity) { return GoodcraftNpcModel.id("slime/slime"); }
            @Override public BakedGeoModel getBakedModel(Identifier id) {
                var custom = GoodcraftCustomModels.byId(id);
                return custom == null ? super.getBakedModel(id) : custom.geometry();
            }
        });
    }
    @Override public GoodcraftNpcRenderState createRenderState(CustomModelEntity entity, Void related) { return new GoodcraftNpcRenderState(); }
    @Override public void addRenderData(CustomModelEntity entity, Void related, GoodcraftNpcRenderState state, float partialTick) { state.action = entity.getModelIdentifier(); }
    @Override public void submit(GoodcraftNpcRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.action.equals("cross") && GoodcraftCustomModels.get(state.action) == null) {
            if (!state.action.isBlank() && REPORTED.add(state.action)) GoodcraftMod.LOGGER.warn("Custom model '{}' has no successfully loaded local geometry/config/texture", state.action);
            return;
        }
        super.submit(state, pose, collector, camera);
    }
}
