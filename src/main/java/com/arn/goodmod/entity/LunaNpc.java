package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** eb/gp/gi autonomous fishing and catch handling, driven by the original clip marker times. */
public final class LunaNpc extends HumanoidNpc {
    private static final EntityDataAccessor<ItemStack> CATCH = SynchedEntityData.defineId(LunaNpc.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> CAST = SynchedEntityData.defineId(LunaNpc.class, EntityDataSerializers.BOOLEAN);
    private int idleTicks, catches, travelTicks;
    private BlockPos water, bank;
    private final Set<BlockPos> failedWater = new HashSet<>();
    private LunaHookEntity hook;
    public LunaNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.LUNA, false); getEquipmentInventory().setItem(6, new ItemStack(Items.FISHING_ROD)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(CATCH, ItemStack.EMPTY); data.define(CAST, false); }
    public ItemStack getCaughtItem() { return entityData.get(CATCH); }
    public boolean isRodCast() { return entityData.get(CAST); }
    public void acceptCatch(ItemStack stack) {
        if (!getCaughtItem().isEmpty() && level() instanceof ServerLevel server) spawnAtLocation(server, getCaughtItem().copy());
        entityData.set(CATCH, stack.copy());
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level)) return;
        if (getOwnerUUID().isPresent() || getAssociatedPlayerUUID().isPresent() || getTarget() != null || scene().isActive()) { if (water != null || hook != null) resetFishing(false); return; }
        String action = getAnimationState();
        if (action.startsWith("FISHING")) {
            setItemSlot(EquipmentSlot.MAINHAND, getEquipmentInventory().getItem(6).copy());
            if (action.equals("FISHING_START")) {
                if (getActionTicks() == 25) cast(level);
                if (getActionTicks() >= 57) setAnimationState("FISHING_IDLE");
            } else if (action.equals("FISHING_IDLE")) {
                if (hook == null || hook.isRemoved()) { if (water != null) failedWater.add(water); resetFishing(false); }
                else if (hook.getBiteTicks() == 15) {
                    int wear = hook.retrieve(); getEquipmentInventory().getItem(6).hurtAndBreak(wear, level, null, broken -> {});
                    hook = null; entityData.set(CAST, false);
                    if (getCaughtItem().isEmpty()) resetFishing(false);
                    else setAnimationState(getCaughtItem().has(DataComponents.FOOD) ? "FISHING_EAT" : "FISHING_THROW_AWAY");
                }
            } else if (action.equals("FISHING_EAT")) {
                if (getActionTicks() == 41 || getActionTicks() == 56 || getActionTicks() == 70) setAnimationInput("legacy.aa", Math.max(0, 1 - (getActionTicks() >= 70 ? 3 : getActionTicks() >= 56 ? 2 : 1) / 3.0));
                if (getActionTicks() >= 130) resetFishing(true);
            } else if (action.equals("FISHING_THROW_AWAY")) {
                if (getActionTicks() == 89 && !getCaughtItem().isEmpty()) { spawnAtLocation(level, getCaughtItem().copy()); entityData.set(CATCH, ItemStack.EMPTY); }
                if (getActionTicks() >= 100) resetFishing(true);
            }
            return;
        }
        if (!action.equals("NULL") || isAnchored() || !(getEquipmentInventory().getItem(6).getItem() instanceof FishingRodItem)) return;
        if (++idleTicks < 1200) return;
        if (water == null) { if (tickCount % 40 == 0) locateWater(level); return; }
        if (bank == null || !level.getFluidState(water).is(FluidTags.WATER)) { failedWater.add(water); resetFishing(false); return; }
        if (distanceToSqr(Vec3.atBottomCenterOf(bank)) > 1) {
            getNavigation().moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, 0.35);
            if (++travelTicks > 200) { failedWater.add(water); resetFishing(false); } return;
        }
        getNavigation().stop(); setAnchored(true);
        setTargetYaw((float)(Math.atan2(getZ() - water.getZ(), getX() - water.getX()) * 180 / Math.PI + 90));
        setAnimationState("FISHING_START");
    }
    private void locateWater(ServerLevel level) {
        BlockPos best = null; int bestDepth = 0;
        BlockPos origin = blockPosition(); int candidates = 0;
        outer: for (int radius = 2; radius < 50; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy = -10; dy <= 10; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate) || failedWater.contains(candidate) || !level.getFluidState(candidate).is(FluidTags.WATER) || !level.isEmptyBlock(candidate.above())) continue;
                    if (++candidates > 50) break outer;
                    int depth = 1; while (depth < 10 && level.getFluidState(candidate.below(depth)).is(FluidTags.WATER)) depth++;
                    if (depth > bestDepth && findBank(level, candidate) != null) { best = candidate; bestDepth = depth; if (depth >= 6) break outer; }
                }
            }
        }
        water = best; bank = best == null ? null : findBank(level, best); travelTicks = 0;
    }
    private BlockPos findBank(ServerLevel level, BlockPos waterPos) {
        BlockPos closest = null; double distance = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            BlockPos candidate = waterPos.offset(dx, 1, dz);
            if (!level.hasChunkAt(candidate) || !level.isEmptyBlock(candidate) || !level.isEmptyBlock(candidate.above()) || !level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)) continue;
            double range = candidate.distSqr(blockPosition());
            if (range < distance) { closest = candidate; distance = range; }
        }
        return closest;
    }
    private void cast(ServerLevel level) {
        if (water == null) return;
        hook = GoodcraftEntities.LUNA_HOOK.create(level, EntitySpawnReason.TRIGGERED); if (hook == null) return;
        hook.setOwner(this); hook.setPos(getX(), getEyeY(), getZ());
        double distance = position().distanceTo(Vec3.atLowerCornerOf(water));
        float pitch = -22.5F + 45F * (float)(distance / 7);
        double yaw = Math.toRadians(getTargetYaw()), power = distance * 0.01;
        Vec3 velocity = new Vec3(-Math.sin(yaw) * power, Math.max(-5, Math.min(5, -Math.tan(Math.toRadians(pitch)))) * power, Math.cos(yaw) * power);
        double length = velocity.length();
        if (length > 0) velocity = velocity.multiply(0.6 / length + 0.5 + random.nextGaussian() * 0.0045, 0.6 / length + 0.5 + random.nextGaussian() * 0.0045, 0.6 / length + 0.5 + random.nextGaussian() * 0.0045);
        hook.setDeltaMovement(velocity);
        ItemStack rod = getEquipmentInventory().getItem(6);
        hook.setLureReduction(Math.round(EnchantmentHelper.getFishingTimeReduction(level, rod, this) * 20));
        hook.setFishingLuck(EnchantmentHelper.getFishingLuckBonus(level, rod, this));
        level.addFreshEntity(hook); entityData.set(CAST, true); playSound(net.minecraft.sounds.SoundEvents.FISHING_BOBBER_THROW, 0.5F, 1);
    }
    private void resetFishing(boolean caught) {
        if (hook != null) hook.discard(); hook = null; water = null; bank = null; travelTicks = 0;
        entityData.set(CAST, false); entityData.set(CATCH, ItemStack.EMPTY); setAnimationInput("legacy.aa", 1);
        setAnchored(false); setAnimationState("NULL");
        if (caught && ++catches >= 3) { catches = 0; idleTicks = 0; }
    }
    @Override public void remove(Entity.RemovalReason reason) { if (hook != null) hook.discard(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putInt("fishingIdleTicks", idleTicks); out.putInt("fishingCatches", catches); out.store("caughtItem", ItemStack.OPTIONAL_CODEC, getCaughtItem()); }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in); idleTicks = in.getIntOr("fishingIdleTicks", 0); catches = in.getIntOr("fishingCatches", 0);
        entityData.set(CATCH, in.read("caughtItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        if (getAnimationState().equals("FISHING_START") || getAnimationState().equals("FISHING_IDLE")) resetFishing(false);
    }
}
