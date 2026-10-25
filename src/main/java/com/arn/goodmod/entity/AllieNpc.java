package com.arn.goodmod.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** ev/bw summon and ordinary wealth-wish lifecycle. Rewards never trust client coordinates. */
public final class AllieNpc extends HumanoidNpc {
    private boolean rewardGranted;
    private int fadeTicks = -1;
    public AllieNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.ALLIE, false); setNoGravity(true); }
    @Override protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        // ev.b(): her owner gets "make me rich", deepthroat and reverse cowgirl in the m overlay.
        if (player instanceof ServerPlayer serverPlayer && canRequestWish(serverPlayer)) {
            com.arn.goodmod.network.NpcNetworking.openInteraction(serverPlayer, this); return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
    public boolean canRequestWish(ServerPlayer player) {
        // ev opened the menu at summonMSG8 (9.68 s into SUMMON) and summon_normalDone; wishes count from there on.
        String state = getAnimationState();
        boolean listening = state.equals("SUMMON_WAIT") || state.equals("SUMMON_NORMAL_WAIT")
                || state.equals("SUMMON") && getActionTicks() >= 193 || state.equals("SUMMON_NORMAL") && getActionTicks() >= 120;
        return canPlayerUse(player) && getOwnerUUID().filter(player.getUUID()::equals).isPresent() && listening;
    }
    public boolean grantWealthWish(ServerPlayer player) {
        if (!canRequestWish(player)) return false;
        int uses = getSummoningStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("sexmodUses", 1);
        rewardGranted = false; setAnimationState(uses == 1 ? "RICH_FIRST_TIME" : "RICH_NORMAL"); return true;
    }
    /** ev.f(): the lamp's first summon plays the longer first-time clips. */
    public boolean isFirstWish() { return getSummoningStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("sexmodUses", 1) == 1; }
    public boolean dismiss(ServerPlayer player) { if (!canPlayerUse(player)) return false; discard(); return true; }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel server)) return;
        if (getAnimationState().equals("NULL") || getOwnerPlayer().isEmpty()) { discard(); return; }
        switch (getAnimationState()) {
            case "SUMMON" -> { if (getActionTicks() >= 242) setAnimationState("SUMMON_WAIT"); }
            case "SUMMON_NORMAL" -> { if (getActionTicks() >= 124) setAnimationState("SUMMON_NORMAL_WAIT"); }
            case "SUMMON_SAND" -> { if (getActionTicks() >= 40 && fadeTicks < 0) fadeTicks = 0; }
            case "RICH_FIRST_TIME", "RICH_NORMAL" -> {
                boolean first = getAnimationState().equals("RICH_FIRST_TIME");
                if (!rewardGranted && getActionTicks() >= (first ? 1 : 11)) {
                    rewardGranted = true;
                    spawnAtLocation(server, new ItemStack(Items.DIAMOND, random.nextInt(2) + 1));
                    spawnAtLocation(server, new ItemStack(Items.EMERALD, random.nextInt(2) + 1));
                    spawnAtLocation(server, new ItemStack(Items.GOLD_INGOT, random.nextInt(2) + 1));
                }
                if (getActionTicks() >= (first ? 17 : 19) && fadeTicks < 0) fadeTicks = 0;
            }
        }
        // d8 originally subtracted 0.01 per rendered frame. Server ticks make removal deterministic.
        if (fadeTicks >= 0) {
            setAnimationInput("allie.alpha", Math.max(0, 0.99 - ++fadeTicks * 0.01));
            if (fadeTicks >= 99) { server.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 30, 0.75, 1.5, 0.75, 0.01); discard(); }
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putBoolean("wealthGranted", rewardGranted); out.putInt("fadeTicks", fadeTicks); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); rewardGranted = in.getBooleanOr("wealthGranted", false); fadeTicks = in.getIntOr("fadeTicks", -1); }
}
