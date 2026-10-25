package com.arn.goodmod.client.render.mixin;

import com.arn.goodmod.client.scene.SceneClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** d3.a(InputUpdateEvent): a locked scene partner cannot walk, jump or sneak; the keys only drive the scene. */
@Mixin(KeyboardInput.class)
abstract class SceneInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void sexmod$freezeDuringScene(CallbackInfo callback) {
        if (!SceneClient.inputLocked()) return;
        ClientInput input = (ClientInput)(Object)this;
        input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor)input).sexmod$setMoveVector(Vec2.ZERO);
    }
}
