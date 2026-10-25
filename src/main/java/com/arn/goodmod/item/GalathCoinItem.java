package com.arn.goodmod.item;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.registry.GoodcraftSounds;
import com.arn.goodmod.world.GalathSummons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;

public final class GalathCoinItem extends LegacyAnimatedItem {
    public GalathCoinItem(Properties properties) { super(properties, "galath_coin"); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        GalathSummons summons = GalathSummons.get(serverPlayer.level().getServer());
        if (summons.isBusy(player.getUUID())) return InteractionResult.FAIL;
        ItemStack stack = player.getItemInHand(hand);
        if (summons.beginSummon(serverPlayer, stack)) {
            triggerItemAnimation(player, stack, "summon"); play(level, player, "misc.weoweo.weoweo1");
        } else play(level, player, "misc.beew.beew0");
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof GoodcraftNpc npc) || npc.getNpcKind() != NpcKind.GALATH
                || npc.getOwnerUUID().filter(player.getUUID()::equals).isEmpty()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && GalathSummons.get(serverPlayer.level().getServer()).beginDismiss(serverPlayer, npc, stack)) {
            triggerItemAnimation(player, stack, "summon"); play(player.level(), player, "misc.weoweo.weoweo0");
        }
        return InteractionResult.SUCCESS;
    }
    private static void play(Level level, Player player, String id) {
        var sound = GoodcraftSounds.events().get(id);
        if (sound != null) level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1, 1);
    }
}
