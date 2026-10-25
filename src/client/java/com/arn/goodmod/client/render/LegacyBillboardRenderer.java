package com.arn.goodmod.client.render;

import com.arn.goodmod.entity.EnergyBallEntity;
import com.arn.goodmod.entity.PyrocinicalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Original textured camera-facing quads, using 26.3 submitted geometry. */
final class LegacyBillboardRenderer<T extends Entity> extends EntityRenderer<T, LegacyBillboardRenderer.State> {
    static final class State extends EntityRenderState {
        Identifier texture;
        float scale = 1, halfWidth, height, bob;
        boolean energy;
        int outerColor = -1, innerColor = -1;
    }
    LegacyBillboardRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.energy = entity instanceof EnergyBallEntity;
        if (state.energy) {
            state.scale = ((EnergyBallEntity)entity).getVisualScale();
            state.texture = GoodcraftNpcModel.id("textures/entity/galath/energy_ball.png");
            state.halfWidth = .25f; state.height = .5f;
            float t = (float)(.5 * Math.sin((entity.level().getGameTime() + partialTick) * .5) + .5);
            state.outerColor = mix(0xff00fffb, 0xffff00ec, t);
            state.innerColor = mix(0xffff00ec, 0xff00fffb, t);
            if (state.scale != 1) state.outerColor = state.innerColor = mix(0x00ffffff, 0xff00fffb, state.scale);
        } else {
            state.halfWidth = 1; state.height = 2; state.scale = 1.4f;
            var player = Minecraft.getInstance().player;
            double distance = player == null ? Double.MAX_VALUE : Math.abs(entity.getX() - player.getX()) + Math.abs(entity.getY() - player.getY()) + Math.abs(entity.getZ() - player.getZ());
            boolean moving = Math.abs(entity.getX() - entity.xo) + Math.abs(entity.getY() - entity.yo) + Math.abs(entity.getZ() - entity.zo) > 0;
            double phase = Math.sin((player == null ? entity.tickCount : player.tickCount) * .75);
            String frame = distance < 3 ? "praising" : moving ? phase > 0 ? "walking1" : "walking2" : "standing";
            state.bob = moving && distance >= 3 ? (float)phase * .1f : 0;
            state.texture = GoodcraftNpcModel.id("textures/entity/pyrocinical/" + frame + ".png");
            int elapsed = ((PyrocinicalEntity)entity).getRemovalTicks();
            if (elapsed >= 0) {
                int fatFrame = Math.clamp(elapsed, 1, 30);
                state.texture = GoodcraftNpcModel.id("textures/entity/pyrocinical/fat/" + fatFrame + ".png");
                state.scale = 1.4f + (fatFrame == 30 ? 1 : (fatFrame + partialTick) / 30f);
                int alpha = Math.round(255 * (1 - Math.clamp((elapsed - 90 + partialTick) / 30f, 0, 1)));
                state.outerColor = alpha << 24 | 0xffffff;
                state.bob = 0;
            }
        }
    }
    @Override public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, stack, collector, camera);
        stack.pushPose();
        stack.translate(0, state.bob, 0);
        if (state.energy) stack.rotate(camera.orientation);
        else stack.rotateDegrees(com.mojang.math.Axis.YP, 180 - camera.yRot);
        stack.scale(state.scale, state.scale, state.scale);
        collector.submitCustomGeometry(stack, RenderTypes.entityTranslucentEmissive(state.texture), (pose, vertices) -> {
            quad(pose, vertices, state.halfWidth, state.height, 0, 0, state.outerColor, state.energy);
            if (state.energy) quad(pose, vertices, state.halfWidth * .75f, state.height * .75f, .05625f, .001f, state.innerColor, true);
        });
        stack.popPose();
    }
    private static int mix(int first, int second, float t) {
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) color |= Math.round(((first >> shift) & 255) * (1 - t) + ((second >> shift) & 255) * t) << shift;
        return color;
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, float half, float height, float y, float z, int color, boolean energy) {
        vertex(pose, vertices, -half, y, z, 0, energy ? 0 : 1, color);
        vertex(pose, vertices, half, y, z, 1, energy ? 0 : 1, color);
        vertex(pose, vertices, half, y + height, z, 1, energy ? 1 : 0, color);
        vertex(pose, vertices, -half, y + height, z, 0, energy ? 1 : 0, color);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z, float u, float v, int color) {
        vertices.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0, 0, 1);
    }
}
