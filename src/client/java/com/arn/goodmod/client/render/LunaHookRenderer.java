package com.arn.goodmod.client.render;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.LunaHookEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

final class LunaHookRenderer extends EntityRenderer<LunaHookEntity, FishingHookRenderState> {
    private final FishingHookRenderer hook;
    LunaHookRenderer(EntityRendererProvider.Context context) { super(context); hook = new FishingHookRenderer(context); }
    @Override public FishingHookRenderState createRenderState() { return new FishingHookRenderState(); }
    @Override public void extractRenderState(LunaHookEntity entity, FishingHookRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lineOriginOffset = Vec3.ZERO;
        if (entity.getOwner() instanceof GoodcraftNpc npc) {
            double yaw = Math.toRadians(npc.getTargetYaw());
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            double hand = npc.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? .35 : -.35;
            Vec3 origin = npc.getPosition(partialTick).add(-cos * hand - sin * .8, npc.getEyeHeight() - .45 - (npc.isCrouching() ? .1875 : 0), -sin * hand + cos * .8);
            state.lineOriginOffset = origin.subtract(entity.getPosition(partialTick)).add(0, -.25, 0);
        }
    }
    @Override public void submit(FishingHookRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) { hook.submit(state, stack, collector, camera); }
}
