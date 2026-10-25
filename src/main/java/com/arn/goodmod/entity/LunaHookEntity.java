package com.arn.goodmod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;

/** gi: NPC-owned fishing hook, retaining original gravity, drag and bite windows. */
public final class LunaHookEntity extends Projectile {
    private static final EntityDataAccessor<Integer> HOOKED = SynchedEntityData.defineId(LunaHookEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BOBBING = SynchedEntityData.defineId(LunaHookEntity.class, EntityDataSerializers.BOOLEAN);
    private int groundTicks, waitTicks, approachTicks, biteTicks, lure, luck;
    public LunaHookEntity(EntityType<? extends LunaHookEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { data.define(HOOKED, 0); data.define(BOBBING, false); }
    public void setLure(int level) { lure = Math.max(0, level) * 100; }
    public void setLureReduction(int ticks) { lure = Math.max(0, ticks); }
    public void setFishingLuck(int luck) { this.luck = Math.max(0, luck); }
    public boolean hasBite() { return biteTicks > 0; }
    public int getBiteTicks() { return biteTicks; }
    @Override public void tick() {
        super.tick();
        if (!(getOwner() instanceof GoodcraftNpc owner) || !owner.isAlive()) { if (!level().isClientSide()) discard(); return; }
        Entity hooked = entityData.get(HOOKED) == 0 ? null : level().getEntity(entityData.get(HOOKED) - 1);
        if (hooked != null) { if (hooked.isAlive()) { setPos(hooked.getX(), hooked.getY(0.8), hooked.getZ()); return; } entityData.set(HOOKED, 0); }
        if (onGround()) { if (++groundTicks >= 1200) discard(); return; }
        boolean water = level().getFluidState(blockPosition()).is(FluidTags.WATER);
        if (water && !entityData.get(BOBBING)) { entityData.set(BOBBING, true); setDeltaMovement(getDeltaMovement().multiply(0.3, 0.2, 0.3)); }
        if (!entityData.get(BOBBING)) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> entity != owner && (entity.isPickable() || entity instanceof ItemEntity));
            if (hit instanceof EntityHitResult entityHit) { entityData.set(HOOKED, entityHit.getEntity().getId() + 1); setDeltaMovement(Vec3.ZERO); return; }
            if (hit.getType() == HitResult.Type.BLOCK) { setPos(hit.getLocation()); setOnGround(true); return; }
        } else if (water) {
            double surface = blockPosition().getY() + level().getFluidState(blockPosition()).getHeight(level(), blockPosition());
            double dy = getY() + getDeltaMovement().y - surface;
            if (Math.abs(dy) < 0.01) dy += Math.signum(dy) * 0.1;
            setDeltaMovement(getDeltaMovement().multiply(0.9, 1, 0.9).add(0, -dy * random.nextFloat() * 0.2, 0));
            if (level() instanceof ServerLevel server) tickFishing(server);
        }
        if (!water) setDeltaMovement(getDeltaMovement().add(0, -0.03, 0));
        move(MoverType.SELF, getDeltaMovement()); setDeltaMovement(getDeltaMovement().scale(0.92));
    }
    private void tickFishing(ServerLevel server) {
        int rate = 1;
        if (random.nextFloat() < 0.25F && server.isRainingAt(blockPosition().above())) rate++;
        if (random.nextFloat() < 0.5F && !server.canSeeSky(blockPosition().above())) rate--;
        if (biteTicks > 0) { biteTicks--; if (biteTicks == 0) { waitTicks = 0; approachTicks = 0; } else setDeltaMovement(getDeltaMovement().add(0, -0.2 * random.nextFloat() * random.nextFloat(), 0)); }
        else if (approachTicks > 0) {
            approachTicks -= rate;
            server.sendParticles(ParticleTypes.FISHING, getX(), getY(), getZ(), 2, 0.3, 0, 0.3, 0);
            if (approachTicks <= 0) { biteTicks = random.nextInt(21) + 20; setDeltaMovement(getDeltaMovement().add(0, -0.4 * (0.6 + random.nextFloat() * 0.4), 0)); playSound(net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1); }
        } else if (waitTicks > 0) { waitTicks -= rate; if (waitTicks <= 0) approachTicks = random.nextInt(61) + 20; }
        else waitTicks = Math.max(1, random.nextInt(501) + 100 - lure);
    }
    public int retrieve() {
        if (!(level() instanceof ServerLevel server) || !(getOwner() instanceof GoodcraftNpc owner)) return 0;
        Entity hooked = entityData.get(HOOKED) == 0 ? null : level().getEntity(entityData.get(HOOKED) - 1);
        int damage = 0;
        if (hooked != null) { hooked.setDeltaMovement(hooked.getDeltaMovement().add(owner.position().subtract(position()).scale(0.1))); damage = hooked instanceof ItemEntity ? 3 : 5; }
        else if (biteTicks > 0) {
            ItemStack rod = owner.getEquipmentInventory().getItem(6);
            if (rod.isEmpty()) rod = new ItemStack(Items.FISHING_ROD);
            LootParams params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position()).withParameter(LootContextParams.TOOL, rod).withParameter(LootContextParams.THIS_ENTITY, this).withLuck(luck).create(LootContextParamSets.FISHING);
            for (ItemStack stack : server.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params)) {
                if (owner instanceof LunaNpc luna) { luna.acceptCatch(stack); continue; }
                ItemStack remaining = owner.getStorageInventory().addItem(stack);
                if (!remaining.isEmpty()) owner.spawnAtLocation(server, remaining);
            }
            damage = 1; biteTicks = 0;
        }
        if (onGround()) damage = 2;
        discard(); return damage;
    }
}
