package com.arn.goodmod.client.render.mixin;

import com.arn.goodmod.client.render.GoodcraftPlayerForms;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
abstract class PlayerFormCameraMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float sexmod$formCameraHeight(Entity entity) {
        if (entity instanceof Player player) {
            var form = GoodcraftPlayerForms.find(player.getUUID());
            if (form.isPresent() && !form.get().isAnchored()) return GoodcraftPlayerForms.eyeHeight(form.get());
        }
        return entity.getEyeHeight();
    }
}
