package com.arn.goodmod.client.render.mixin;

import com.arn.goodmod.client.scene.SceneClient;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** fp.useBoyCam: in first person the view follows the girl model's boyCam bone instead of the frozen player. */
@Mixin(Camera.class)
abstract class SceneCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void sexmod$boyCam(float partialTick, CallbackInfo callback) {
        if (detached) return;
        Vec3 position = SceneClient.boyCamPosition(partialTick);
        if (position != null) setPosition(position);
    }
}
