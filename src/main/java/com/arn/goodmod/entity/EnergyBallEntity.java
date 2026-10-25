package com.arn.goodmod.entity;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** c4: reflectable energy ball, with skeleton summon or explosion on impact. */
public final class EnergyBallEntity extends PathfinderMob {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> VISUAL_SCALE = net.minecraft.network.syncher.SynchedEntityData.defineId(EnergyBallEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private Vec3 flight = Vec3.ZERO;
    private UUID caster;
    private boolean reflected;
    private boolean charging;
    public EnergyBallEntity(EntityType<? extends EnergyBallEntity> type, Level level) { super(type, level); setNoAi(true); setNoGravity(true); noPhysics = true; }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(VISUAL_SCALE, 1F); }
    public float getVisualScale() { return entityData.get(VISUAL_SCALE); }
    public void setVisualScale(float scale) { if (Float.isFinite(scale) && scale >= 0) entityData.set(VISUAL_SCALE, scale); }
    public void setCharging(boolean charging) { this.charging = charging; }
    public void setCaster(GalathNpc npc) { caster = npc.getUUID(); }
    public void setFlightVelocity(Vec3 velocity) { flight = velocity; setDeltaMovement(velocity); }
    @Override public boolean isPickable() { return true; }
    @Override public void tick() {
        setDeltaMovement(flight); super.tick();
        if (level().isClientSide()) { level().addParticle(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1), getX(), getY() + 0.25, getZ(), 0, 0, 0); return; }
        if (charging) return;
        ServerLevel server = (ServerLevel)level();
        if (reflected) {
            var hit = server.getEntitiesOfClass(GalathNpc.class, getBoundingBox().inflate(0.75));
            if (!hit.isEmpty()) { server.explode(this, getX(), getY(), getZ(), 1, Level.ExplosionInteraction.MOB); hit.forEach(npc -> npc.knockOut(position())); discard(); return; }
        }
        if (server.getBlockState(blockPosition()).isAir()) return;
        GalathNpc owner = caster != null && server.getEntity(caster) instanceof GalathNpc npc ? npc : null;
        Vec3 spawn = new Vec3(getX(), getBlockY() + 1, getZ());
        if (owner != null && owner.getTarget() != null && owner.getTarget().distanceToSqr(spawn) >= 225) server.explode(this, getX(), getY(), getZ(), 2, Level.ExplosionInteraction.MOB);
        else {
            var skeleton = EntityTypes.WITHER_SKELETON.create(server, EntitySpawnReason.MOB_SUMMONED);
            if (skeleton != null) { skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD)); skeleton.setPos(spawn); server.addFreshEntity(skeleton); if (owner != null) owner.registerSummonedSkeleton(skeleton.getUUID()); }
        }
        discard();
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) { discard(); return true; }
        if (charging && source.getEntity() instanceof Player) { discard(); return true; }
        if (source.getDirectEntity() instanceof AbstractArrow arrow) { arrow.discard(); discard(); return true; }
        if (source.getEntity() instanceof Player player) { setFlightVelocity(player.getLookAngle()); reflected = true; return true; }
        return false;
    }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); discard(); }
}
