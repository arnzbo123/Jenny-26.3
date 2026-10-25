package com.arn.goodmod.client.render.mixin;

import com.arn.goodmod.client.render.GoodcraftPlayerForms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class PlayerFormVisibilityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void sexmod$renderTrackedForm(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> callback) {
        if (GoodcraftPlayerForms.hideWorldEntity(entity)) callback.setReturnValue(false);
    }
}
