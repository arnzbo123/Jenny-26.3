package com.arn.goodmod.item;

import com.arn.goodmod.network.TribeNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class DragonStaffItem extends LegacyAnimatedItem {
    public DragonStaffItem(Properties properties) { super(properties,"dragon_staff"); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getPlayer() instanceof ServerPlayer player) TribeNetworking.openOwned(player,context.getClickedPos(),
                context.getClickedFace().getAxis().isHorizontal()?context.getClickedFace().getOpposite():player.getDirection());
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer serverPlayer) TribeNetworking.openOwned(serverPlayer,player.blockPosition(),player.getDirection());
        return InteractionResult.SUCCESS;
    }
}
