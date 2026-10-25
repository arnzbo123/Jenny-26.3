package com.arn.goodmod.item;

import com.arn.goodmod.world.KoboldTribes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public final class TribeEggItem extends Item {
    public TribeEggItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var eyes = player.getEyePosition();
        var hit = level.clip(new ClipContext(eyes, eyes.add(player.getLookAngle().scale(5)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS || !level.mayInteract(player, hit.getBlockPos())) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server) {
            if (KoboldTribes.get(server).spawn(server, hit.getLocation()).isEmpty()) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
