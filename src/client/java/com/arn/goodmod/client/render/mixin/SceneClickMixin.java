package com.arn.goodmod.client.render.mixin;

import com.arn.goodmod.client.scene.SceneClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** d3.a(MouseEvent): mouse buttons do nothing while the player is frozen in a scene. */
@Mixin(Minecraft.class)
abstract class SceneClickMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void sexmod$noAttack(CallbackInfoReturnable<Boolean> callback) { if (SceneClient.inputLocked()) callback.setReturnValue(false); }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void sexmod$noMining(boolean leftClick, CallbackInfo callback) { if (SceneClient.inputLocked()) callback.cancel(); }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void sexmod$noUse(CallbackInfo callback) { if (SceneClient.inputLocked()) callback.cancel(); }
    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void sexmod$noPick(CallbackInfo callback) { if (SceneClient.inputLocked()) callback.cancel(); }
}
