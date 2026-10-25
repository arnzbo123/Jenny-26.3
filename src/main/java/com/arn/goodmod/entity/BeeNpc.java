package com.arn.goodmod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** fz: flying navigator, panic, swimming and aerial wandering. */
public final class BeeNpc extends HumanoidNpc {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> TAMED = net.minecraft.network.syncher.SynchedEntityData.defineId(BeeNpc.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CHEST = net.minecraft.network.syncher.SynchedEntityData.defineId(BeeNpc.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** fz.N / fz.P: ticks since her last scene (ready at 4800) and the taming-verdict timer after one. */
    private static final float READY = 4800;
    private float desire = 3200;
    private int verdict;
    private boolean offered;
    public BeeNpc(EntityType<? extends GoodcraftNpc> type, Level level) {
        super(type, level, NpcKind.BEE, false); moveControl = new FlyingMoveControl<>(this, 20, true);
    }
    @Override protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(TAMED, false); data.define(CHEST, false); }
    public boolean isTamed() { return entityData.get(TAMED); }
    public void setTamed(boolean tamed) { entityData.set(TAMED, tamed); }
    public boolean hasChest() { return entityData.get(CHEST); }
    public boolean isReady() { return desire >= READY && getOwnerUUID().isEmpty(); }
    @Override public boolean supportsStorage() { return hasChest(); }
    @Override protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (isTamed() && !hasChest() && player.getItemInHand(hand).is(net.minecraft.world.item.Items.CHEST) && canPlayerUse(player)) {
            if (!level().isClientSide()) { entityData.set(CHEST, true); if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1); }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    @Override public void tick() {
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel server) tickDesire(server);
        if (!onGround() && getDeltaMovement().y < 0) setDeltaMovement(getDeltaMovement().multiply(1, 0.4, 1));
        if (getDeltaMovement().y > 0) {
            var ground = level().clip(new net.minecraft.world.level.ClipContext(position(), position().with(net.minecraft.core.Direction.Axis.Y, level().getMinY()), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (ground.getType() != net.minecraft.world.phys.HitResult.Type.MISS && getY() - ground.getBlockPos().getY() > 3) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
        }
    }
    /**
     * fz.c()/a(): once ready she flies to the nearest player. The original grabbed him on contact; the port has her
     * offer instead (right click to accept). After her scene she decides over 200 ticks whether to stay tamed.
     */
    private void tickDesire(net.minecraft.server.level.ServerLevel server) {
        if (hasEffect(com.arn.goodmod.registry.GoodcraftEffects.TRANSFORMATION) && desire < READY && scene().partner().isEmpty()) desire = READY;
        if (getAnimationState().equals("CITIZEN_CUM") && verdict == 0) { verdict = 1; desire = 0; }
        if (verdict > 0) {
            verdict++;
            var particle = verdict < 200 ? net.minecraft.core.particles.ParticleTypes.WITCH
                    : isTamed() ? net.minecraft.core.particles.ParticleTypes.HEART : net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER;
            if (verdict == 200) setTamed(random.nextBoolean());
            if (tickCount % 4 == 0) server.sendParticles(particle, getX(), getY() + .3, getZ(), verdict < 200 ? 1 : 3, .2, .3, .2, .25);
            if (verdict >= 250) verdict = 0;
        }
        if (scene().isActive() || !getAnimationState().equals("NULL") || getOwnerUUID().isPresent()) return;
        if (desire < READY) { desire++; offered = false; return; }
        Player nearest = server.getNearestPlayer(this, 10);
        if (nearest == null || nearest.isSpectator() || com.arn.goodmod.scene.SceneDirector.isLockedPlayer(nearest.getUUID())) return;
        if (tickCount % 10 == 0) server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, getX(), getY() + 1.4, getZ(), 1, .2, .2, .2, 0);
        if (distanceTo(nearest) < 1.5) {
            getNavigation().stop();
            if (!offered && nearest instanceof net.minecraft.server.level.ServerPlayer target) {
                offered = true;
                target.sendOverlayMessage(net.minecraft.network.chat.Component.literal("<" + getName().getString() + "> bzz~ want some honey? (right click her)"));
            }
        } else if (tickCount % 20 == 0) getNavigation().moveTo(nearest, 1);
    }
    @Override protected int calculateFallDamage(double distance, float multiplier) { return 0; }
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) { super.addAdditionalSaveData(out); out.putBoolean("isTamed", isTamed()); out.putBoolean("hasChest", hasChest()); out.putFloat("desire", desire); }
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) { super.readAdditionalSaveData(in); setTamed(in.getBooleanOr("isTamed", false)); entityData.set(CHEST, in.getBooleanOr("hasChest", false)); desire = in.getFloatOr("desire", 3200); }
    @Override protected void configureNpcGoals() {
        goalSelector.addGoal(0, new NpcMovementGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25)); goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 3, 1));
        goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1) {
            @Override public boolean canUse() { return getMovementMode() == MovementMode.WANDER && !isAnchored() && super.canUse(); }
        });
    }
}
