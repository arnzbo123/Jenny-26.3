package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** fn: grounded 100-tick jump cycle, with direction selected at tick 85. */
public final class SlimeNpc extends HumanoidNpc {
    private int groundTicks, growthTicks = -1, hornyLevel;
    private boolean warming;
    private float jumpYaw;
    public SlimeNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.SLIME, false); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (growthTicks >= 0 && --growthTicks < 0 && level() instanceof ServerLevel server) {
            var slime = GoodcraftEntities.FRIENDLY_SLIME.create(server, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
            if (slime != null) { slime.setPos(position()); server.addFreshEntity(slime); }
        }
        // fn: a horny potion makes her ready at once; otherwise each landing has a 10% chance to warm her up a level.
        if (hasEffect(com.arn.goodmod.registry.GoodcraftEffects.TRANSFORMATION) && growthTicks < 0 && getAnimationState().equals("NULL")) {
            removeEffect(com.arn.goodmod.registry.GoodcraftEffects.TRANSFORMATION); raiseHornyLevel(2);
        }
        if (hornyLevel >= 2 && tickCount % 10 == 0 && level() instanceof ServerLevel server)
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 1, 0.2, 0.2, 0.2, 0);
        if (isAnchored() || !getAnimationState().equals("NULL")) return;
        if (onGround()) {
            if (groundTicks == 0) warming = growthTicks < 0 && random.nextFloat() < 0.1f;
            if (warming && groundTicks == 50) raiseHornyLevel(hornyLevel + 1);
            groundTicks++;
            if (groundTicks == 85) jumpYaw = random.nextFloat() * 360;
            if (groundTicks >= 100) {
                setYRot(jumpYaw); jumpFromGround();
                Vec3 forward = Vec3.directionFromRotation(0, jumpYaw).scale(0.7);
                setDeltaMovement(forward.x, getDeltaMovement().y, forward.z); groundTicks = 0;
            }
        }
    }
    public int getHornyLevel() { return hornyLevel; }
    private void raiseHornyLevel(int level) {
        int previous = hornyLevel; hornyLevel = Math.min(4, Math.max(hornyLevel, level));
        if (previous == 0 && hornyLevel > 0 && getModelVariant() != 0) setModelVariant(0); // fn UNDRESS at level one.
    }
    /** fn.g() after a scene: calm again and dressed. */
    public void calmDown() { hornyLevel = 0; warming = false; setModelVariant(1); }
    /** fn "pregnant": the friendly slime arrives after the given ticks. */
    public void startBirth(int ticks) { if (growthTicks < 0) growthTicks = ticks; }
    @Override protected int calculateFallDamage(double distance, float multiplier) { return 0; }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putInt("ticksUntilBirth", growthTicks); out.putInt("jumpTicks", groundTicks); out.putInt("hornyLevel", hornyLevel); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); growthTicks = in.getIntOr("ticksUntilBirth", -1); groundTicks = in.getIntOr("jumpTicks", 0); hornyLevel = Math.clamp(in.getIntOr("hornyLevel", 0), 0, 4); }
}
