package com.arn.goodmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** al: ambient wandering, stopping within three blocks of a player; delayed removal on hit. */
public final class PyrocinicalEntity extends PathfinderMob {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> REMOVAL = net.minecraft.network.syncher.SynchedEntityData.defineId(PyrocinicalEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private BlockPos wanderTarget;
    private int waitingTicks, removalTicks = -1;
    public PyrocinicalEntity(EntityType<? extends PyrocinicalEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(REMOVAL, -1); }
    public int getRemovalTicks() { return entityData.get(REMOVAL); }
    @Override public void tick() {
        super.tick(); if (level().isClientSide()) return;
        if (removalTicks >= 0) { entityData.set(REMOVAL, 125 - removalTicks); getNavigation().stop(); if (--removalTicks <= 0) discard(); return; }
        Player player = level().getNearestPlayer(this, 15);
        if (player != null && distanceToSqr(player) < 9) { getNavigation().stop(); return; }
        if (wanderTarget == null || distanceToSqr(net.minecraft.world.phys.Vec3.atLowerCornerOf(wanderTarget)) > 1800 || waitingTicks > 175) {
            int dx = random.nextInt(10) * (random.nextBoolean() ? 1 : -1), dz = random.nextInt(10) * (random.nextBoolean() ? 1 : -1);
            int y = level().dimension() == Level.NETHER ? (int)Math.ceil(getY()) : level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX() + dx, getBlockZ() + dz);
            wanderTarget = new BlockPos(getBlockX() + dx, y, getBlockZ() + dz); waitingTicks = 0;
        }
        if (distanceToSqr(net.minecraft.world.phys.Vec3.atLowerCornerOf(wanderTarget)) > 4) getNavigation().moveTo(wanderTarget.getX(), wanderTarget.getY(), wanderTarget.getZ(), 0.35);
        else waitingTicks++;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) { discard(); return true; }
        if (source.getEntity() instanceof Player) { removalTicks = 125; var sound = com.arn.goodmod.registry.GoodcraftSounds.events().get("misc.weoweo.weoweo4"); if (sound != null) playSound(sound, 1, 1); }
        return false;
    }
}
