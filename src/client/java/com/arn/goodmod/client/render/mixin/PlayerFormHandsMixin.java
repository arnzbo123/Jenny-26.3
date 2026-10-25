package com.arn.goodmod.client.render.mixin;

import com.arn.goodmod.client.render.GoodcraftFirstPersonHands;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class PlayerFormHandsMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void sexmod$sceneHidesHands(float partialTick, PoseStack pose, SubmitNodeCollector collector, PlayerRenderState state,
            net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, CallbackInfo callback) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && com.arn.goodmod.client.scene.SceneClient.hidesPlayer(player.getUUID())) callback.cancel();
    }
    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void sexmod$formArm(PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm, PlayerRenderState state, CallbackInfo callback) {
        if (GoodcraftFirstPersonHands.render(pose, collector, light, equip, swing, arm, false)) callback.cancel();
    }
    @Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true)
    private void sexmod$formMapHand(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidArm arm, PlayerRenderState state, CallbackInfo callback) {
        if (GoodcraftFirstPersonHands.render(pose, collector, light, 0, 0, arm, true)) callback.cancel();
    }
}
