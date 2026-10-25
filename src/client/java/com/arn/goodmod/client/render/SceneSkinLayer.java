package com.arn.goodmod.client.render;

import com.arn.goodmod.client.scene.SceneClient;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * d_.a(GeoModel, ...): the girl model's "steve" bones are the scene partner's body, drawn with his own skin.
 * The original painted two colors into the skin's unused top-left corner for the "cock" bones; those bones are
 * drawn from a small texture holding just those two patches instead of editing the downloaded skin.
 */
final class SceneSkinLayer extends CustomBoneTextureGeoLayer<GoodcraftNpc, Void, GoodcraftNpcRenderState> {
    private static final Identifier STEVE = Identifier.fromNamespaceAndPath("sexmod", "textures/player/steve.png");
    private static final Identifier PARTS = Identifier.fromNamespaceAndPath("sexmod", "scene_parts");
    private static boolean partsRegistered;

    SceneSkinLayer(GoodcraftNpcRenderer renderer) { super(renderer, "steve", STEVE); }

    @Override public void addRenderData(GoodcraftNpc npc, Void related, GoodcraftNpcRenderState state, float partialTick) {
        state.partnerSkin = STEVE;
        state.partnerLocal = false;
        if (!state.showPlayer) return;
        npc.getRenderPartnerUUID().ifPresent(id -> {
            var player = npc.level().getPlayerByUUID(id);
            var local = Minecraft.getInstance().player;
            state.partnerLocal = local != null && local.getUUID().equals(id);
            if (player instanceof AbstractClientPlayer client) {
                var skin = client.getSkin();
                state.partnerSkin = skin.body().texturePath();
                state.slimArms = skin.model() == PlayerModelType.SLIM;
            }
        });
    }

    @Override public void preRender(RenderPassInfo<GoodcraftNpcRenderState> pass, SubmitNodeCollector collector) {
        var state = pass.renderState();
        state.skinBones.clear(); state.partBones.clear();
        if (state.partnerLocal) pass.addBonePositionListener("boyCam", (world, model, relative) -> {
            if (world != null) SceneClient.recordBoyCam(state.entityUUID, world.subtract(state.getGeckolibData(DataTickets.POSITION)));
        });
        if (!state.showPlayer) return;
        pass.model().getBone("steve").ifPresent(steve -> collect(steve, false, state.skinBones, state.partBones));
        // Only bones the renderer left visible are redrawn; their own cubes are skipped in the girl-textured pass.
        pass.addBoneUpdater((renderPass, bones) -> {
            for (Set<String> group : java.util.List.of(state.skinBones, state.partBones)) {
                for (String name : Set.copyOf(group)) bones.ifPresent(name, bone -> {
                    if (bone.isHidden()) { group.remove(name); return; }
                    bone.skipRender(true).skipChildrenRender(bone.areChildrenHidden());
                });
            }
        });
    }

    private static void collect(GeoBone bone, boolean parts, Set<String> skin, Set<String> partBones) {
        boolean inParts = parts || bone.name().equals("cock");
        (inParts ? partBones : skin).add(bone.name());
        for (GeoBone child : bone.children()) collect(child, inParts, skin, partBones);
    }

    @Override public void addPerBoneRender(RenderPassInfo<GoodcraftNpcRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<GoodcraftNpcRenderState>> consumer) {
        if (!pass.renderState().showPlayer) return;
        for (Set<String> group : java.util.List.of(pass.renderState().skinBones, pass.renderState().partBones)) {
            for (String name : group) pass.model().getBone(name).filter(CuboidGeoBone.class::isInstance).ifPresent(bone -> consumer.accept(bone, this::renderBone));
        }
    }

    @Override protected void renderBone(RenderPassInfo<GoodcraftNpcRenderState> pass, GeoBone bone, SubmitNodeCollector collector) {
        var state = pass.renderState();
        boolean part = state.partBones.contains(bone.name());
        if (!part && !state.skinBones.contains(bone.name())) return;
        Identifier texture = part ? parts() : state.partnerSkin;
        collector.submitCustomGeometry(pass.poseStack(), RenderTypes.entityTranslucent(texture), (pose, vertices) -> {
            var stack = pass.poseStack();
            stack.pushPose();
            stack.last().set(pose);
            bone.translateAwayFromPivotPoint(stack);
            for (var cube : ((CuboidGeoBone)bone).cubes) {
                stack.pushPose();
                renderCube(cube, stack, vertices, pass.packedLight(), pass.packedOverlay(), pass.renderColor(), 1, 1);
                stack.popPose();
            }
            stack.popPose();
        });
    }

    /** d_.a(UUID, World): skin colors 245,199,165 and 245,157,169 at (0,0)-(4,3) and (4,0)-(7,3). */
    private static Identifier parts() {
        if (!partsRegistered) {
            partsRegistered = true;
            NativeImage image = new NativeImage(64, 64, true);
            for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) image.setPixel(x, y, 0);
            for (int x = 0; x < 7; x++) for (int y = 0; y < 3; y++) image.setPixel(x, y, x < 4 ? 0xFFF5C7A5 : 0xFFF59DA9);
            Minecraft.getInstance().getTextureManager().register(PARTS, new DynamicTexture(() -> "sexmod scene parts", image));
        }
        return PARTS;
    }
}
