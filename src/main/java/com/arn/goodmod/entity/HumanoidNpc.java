package com.arn.goodmod.entity;

import com.arn.goodmod.menu.NpcMenus;
import com.arn.goodmod.network.NpcNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Shared em/e2 behavior for Jenny, Ellie, Bia, Luna and Allie. */
public class HumanoidNpc extends GoodcraftNpc {
    public HumanoidNpc(EntityType<? extends GoodcraftNpc> type, Level level, NpcKind kind, boolean playerForm) { super(type, level, kind, playerForm); }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isPlayerForm()) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer serverPlayer) {
            // Sneaking reaches a storage girl's chest directly; otherwise the m overlay with her actions opens.
            if (supportsStorage() && player.isShiftKeyDown() && NpcMenus.openStorage(serverPlayer, this)) return InteractionResult.SUCCESS;
            if (NpcNetworking.openInteraction(serverPlayer, this)) {
                // f_.b: Galath's "huh?"; ff.b: a kobold greets her master.
                if (getNpcKind() == NpcKind.GALATH) playLegacy("GIRLS_GALATH_HUH", -1, 1);
                else if (getNpcKind() == NpcKind.KOBOLD && getOwnerUUID().filter(player.getUUID()::equals).isPresent()) playLegacy("GIRLS_KOBOLD_MASTER", -1, 1);
                return InteractionResult.SUCCESS;
            }
            if (supportsStorage() && NpcMenus.openStorage(serverPlayer, this)) return InteractionResult.SUCCESS;
            return super.mobInteract(player, hand);
        }
        return InteractionResult.SUCCESS;
    }
}
