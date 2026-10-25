package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/** Original at hand cuboids and cn first-person transforms; items keep their native use animation. */
public final class GoodcraftFirstPersonHands {
    private static final ModelPart STANDARD = build(false), ELLIE = build(true);
    private GoodcraftFirstPersonHands() {}

    private static ModelPart build(boolean ellie) {
        MeshDefinition mesh = new MeshDefinition();
        if (ellie) {
            var arm = mesh.getRoot().addOrReplaceChild("arm", CubeListBuilder.create(), PartPose.offset(-5, 1.5708f, 0));
            arm.addOrReplaceChild("box", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -3, -1, 2, 6, 2), PartPose.offsetAndRotation(-1, -3, 1, 0, 1.5708f, 0));
        } else mesh.getRoot().addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -6, 0, 2, 6, 2), PartPose.offset(-5, 2.5f, 0));
        return LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    public static boolean render(PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm, boolean mapHand) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isInvisible()) return false;
        // l.a(RenderHandEvent): no first-person arm while a girl's scene clip draws the player's body.
        if (com.arn.goodmod.client.scene.SceneClient.hidesPlayer(player.getUUID())) return true;
        var active = GoodcraftPlayerForms.find(player.getUUID());
        if (active.isEmpty()) return false;
        GoodcraftNpc npc = active.get();
        String kind = npc.getNpcKind().id();
        String folder = kind.equals("luna") ? "cat" : kind.equals("goblin") ? "kobold" : kind;
        String texture = "textures/entity/" + folder + "/hand" + ((kind.equals("jenny") || kind.equals("ellie")) && npc.getModelVariant() == 0 ? "_nude" : "") + ".png";
        int color = handColor(npc);
        ModelPart model = kind.equals("ellie") ? ELLIE : STANDARD;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.pushPose();
        if (mapHand) {
            pose.rotateDegrees(Axis.YP, 92); pose.rotateDegrees(Axis.XP, 45); pose.rotateDegrees(Axis.ZP, side * -41);
            pose.translate(side * .3f, -1.1f, .45f);
            pose.translate(arm == HumanoidArm.RIGHT ? .63f : 1.6f, arm == HumanoidArm.RIGHT ? .36f : .35f, 0);
        } else {
            float root = (float)Math.sqrt(swing), a = (float)Math.sin(root * Math.PI), b = (float)Math.sin(root * Math.PI * 2);
            pose.translate(side * (-.3f * a + .64000005f), .4f * b - .6f + (1 + equip) * -.6f, -.4f * (float)Math.sin(swing * Math.PI) - .71999997f);
            pose.rotateDegrees(Axis.YP, side * 45);
            pose.rotateDegrees(Axis.YP, side * a * 70);
            pose.rotateDegrees(Axis.ZP, side * (float)Math.sin(swing * swing * Math.PI) * -20);
            pose.translate(side * -1, 3.6f, 3.5f);
            pose.rotateDegrees(Axis.ZP, side * 120); pose.rotateDegrees(Axis.XP, 200); pose.rotateDegrees(Axis.YP, side * -135);
            pose.translate(side * 5.6f, 0, 0); pose.translate(.5f, 1.1f, 0);
        }
        // ModelPart's units are 1/16; the original ModelRenderer used 0.175.
        pose.scale(2.8f, 2.8f, 2.8f);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(GoodcraftNpcModel.id(texture)), (matrix, vertices) -> {
            PoseStack captured = new PoseStack(); captured.last().set(matrix);
            model.render(captured, vertices, light, OverlayTexture.NO_OVERLAY, color);
        });
        pose.popPose();
        return true;
    }

    private static int handColor(GoodcraftNpc npc) {
        var selectors = RenderModelCode.read(npc).selectors();
        String palette; int index;
        if (npc.getNpcKind().id().equals("kobold")) { palette = "kobold"; index = selectors.size() == 11 ? selectors.get(1) : 3; }
        else if (npc.getNpcKind().id().equals("goblin")) { palette = "goblin_skin"; index = selectors.size() == 9 ? selectors.get(7) : 2; }
        else return 0xffffffff;
        var rgb = AnimationCatalog.APPEARANCE.getAsJsonArray(palette).get(index).getAsJsonObject().getAsJsonArray("rgb");
        return 0xff000000 | rgb.get(0).getAsInt() << 16 | rgb.get(1).getAsInt() << 8 | rgb.get(2).getAsInt();
    }
}
