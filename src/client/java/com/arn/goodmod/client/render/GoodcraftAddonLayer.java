package com.arn.goodmod.client.render;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Original wearable attachment pivots; geometry stays supplied by the local addon. */
final class GoodcraftAddonLayer extends GeoRenderLayer<GoodcraftNpc, Void, GoodcraftNpcRenderState> {
    record Attachment(String bone, GoodcraftCustomModels.Model model) {}
    GoodcraftAddonLayer(GoodcraftNpcRenderer renderer) { super(renderer); }

    @Override public void addRenderData(GoodcraftNpc npc, Void related, GoodcraftNpcRenderState state, float partialTick) {
        List<Attachment> attachments = new ArrayList<>();
        for (String name : RenderModelCode.read(npc).addons()) {
            var model = GoodcraftCustomModels.get(name);
            if (model == null) continue;
            var config = model.configuration();
            if (npc.getModelVariant() == 0 && !config.getProperty("enable_when_nude", "no").equalsIgnoreCase("yes")) continue;
            String kinds = config.getProperty("which_girls", "").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
            if (!kinds.isEmpty() && !List.of(kinds.split(",")).contains(npc.getNpcKind().id())) continue;
            String bone = switch (config.getProperty("wear_type", "").trim()) {
                case "HEAD" -> "customHead";
                case "FOOT_L" -> "customShoeL";
                case "FOOT_R" -> "customShoeR";
                case "HAND_L" -> "customHandL";
                case "HAND_R" -> "customHandR";
                case "CUSTOM_BONE" -> config.getProperty("custom_bone", "");
                default -> "";
            };
            if (!bone.isEmpty()) attachments.add(new Attachment(bone, model));
        }
        state.addons = List.copyOf(attachments);
    }

    @Override public void preRender(RenderPassInfo<GoodcraftNpcRenderState> pass, SubmitNodeCollector collector) {
        pass.addBoneUpdater((renderPass, bones) -> {
            for (Attachment attachment : pass.renderState().addons) {
                if (pass.model().getBone(attachment.bone()).isEmpty()) continue;
                for (String hidden : attachment.model().configuration().getProperty("bones_to_hide", "").replace(" ", "").split(",")) {
                    bones.ifPresent(hidden, bone -> bone.skipRender(true));
                }
            }
        });
    }

    @Override public void addPerBoneRender(RenderPassInfo<GoodcraftNpcRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<GoodcraftNpcRenderState>> consumer) {
        for (Attachment attachment : pass.renderState().addons) {
            pass.model().getBone(attachment.bone()).ifPresent(bone -> consumer.accept(bone, (renderPass, anchor, collector) -> {
                int light = attachment.model().configuration().getProperty("which_lighting", "DEFAULT").trim().equals("NONE") ? 15728880 : renderPass.packedLight();
                collector.submitCustomGeometry(renderPass.poseStack(), RenderTypes.entityTranslucent(attachment.model().texture()), (pose, vertices) -> {
                    PoseStack stack = renderPass.poseStack();
                    stack.pushPose(); stack.last().set(pose);
                    renderAttached(renderPass, attachment.model().geometry(), vertices, light, renderPass.packedOverlay(), renderPass.renderColor());
                    stack.popPose();
                });
            }));
        }
    }

    static void renderAttached(RenderPassInfo<GoodcraftNpcRenderState> pass, com.geckolib.cache.model.BakedGeoModel geometry,
            com.mojang.blaze3d.vertex.VertexConsumer vertices, int light, int overlay, int color) {
        PoseStack stack = pass.poseStack();
        for (GeoBone root : geometry.topLevelBones()) {
            stack.pushPose(); root.translateAwayFromPivotPoint(stack);
            root.positionAndRender(pass, vertices, light, overlay, color);
            stack.popPose();
        }
    }
}
