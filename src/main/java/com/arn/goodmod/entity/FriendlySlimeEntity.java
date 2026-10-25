package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** ay: harmless, persistent slime, maturing into fn after 8,400 ticks. */
public final class FriendlySlimeEntity extends AbstractCubeMob {
    private int ageInTicks;
    public FriendlySlimeEntity(EntityType<? extends FriendlySlimeEntity> type, Level level) { super(type, level); setPersistenceRequired(); }
    @Override protected void addBehaviourGoals() {}
    @Override protected void addTargetingGoals() {}
    @Override protected boolean canDealDamage() { return false; }
    @Override protected boolean canBeABaby() { return false; }
    @Override protected int getJumpDelay() { return random.nextInt(100) + 50; }
    @Override public void setSize(int size, boolean heal) { super.setSize(size, heal); getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2 + 0.1 * getSize()); }
    @Override protected ParticleOptions getParticleType() { return ParticleTypes.ITEM_SLIME; }
    @Override protected SoundEvent getJumpSound() { return SoundEvents.SLIME_JUMP_SMALL; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SLIME_HURT_SMALL; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_DEATH_SMALL; }
    @Override protected SoundEvent getSquishSound() { return SoundEvents.SLIME_SQUISH_SMALL; }
    @Override public void tick() {
        super.tick(); ageInTicks++;
        if (level() instanceof ServerLevel server && ageInTicks > 8400) {
            var npc = GoodcraftEntities.create(NpcKind.SLIME, server); npc.setPos(position()); npc.setYRot(getYRot()); server.addFreshEntity(npc); discard();
        } else if (level().isClientSide() && ageInTicks > 7980) level().addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), 0, 0.02, 0);
        else if (level().isClientSide() && ageInTicks > 5880 && tickCount % 10 == 0) level().addParticle(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 0, 0.02, 0);
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putInt("ageInTicks", ageInTicks); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); ageInTicks = in.getIntOr("ageInTicks", 0); }
}
