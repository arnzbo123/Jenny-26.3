package com.arn.goodmod.entity;

import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** f8/bt link: follow the associated Galath while both entities are free to move. */
public final class ManglelieNpc extends HumanoidNpc {
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> MOTHER = net.minecraft.network.syncher.SynchedEntityData.defineId(ManglelieNpc.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BOW_TICKS = net.minecraft.network.syncher.SynchedEntityData.defineId(ManglelieNpc.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    public ManglelieNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.MANGLELIE, false); }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(MOTHER, ""); data.define(BOW_TICKS, -1); }
    @Override protected void configureNpcGoals() {
        super.configureNpcGoals();
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this, net.minecraft.world.entity.player.Player.class, 20F, 1, 1.2) {
            private boolean protectedByMother() { return getMotherUUID().isPresent() || !level().getEntitiesOfClass(GalathNpc.class, getBoundingBox().inflate(20), npc -> npc.isAlive() && npc.getOwnerUUID().isPresent()).isEmpty(); }
            @Override public boolean canUse() { return !protectedByMother() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !protectedByMother() && super.canContinueToUse(); }
            @Override public void start() { super.start(); setAnimationInput("legacy.ar", 1); }
            @Override public void stop() { super.stop(); setAnimationInput("legacy.ar", 0); }
        });
    }
    public Optional<UUID> getMotherUUID() { try { return Optional.of(UUID.fromString(entityData.get(MOTHER))); } catch (IllegalArgumentException ignored) { return Optional.empty(); } }
    public void setMotherUUID(UUID uuid) { entityData.set(MOTHER, uuid == null ? "" : uuid.toString()); setNoGravity(uuid != null); noPhysics = uuid != null; }
    public int getBowTicks() { return entityData.get(BOW_TICKS); }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) && getMotherUUID().isPresent() && level.getEntity(getMotherUUID().get()) instanceof GalathNpc mother) {
            mother.hurtServer(level, source, amount); return false;
        }
        return super.hurtServer(level, source, amount);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || getMotherUUID().isEmpty()) return;
        if (!(server.getEntity(getMotherUUID().get()) instanceof GalathNpc mother)) return; // A missing loaded entity can be a chunk boundary.
        if (!mother.isAlive() || mother.getManglelieUUID().filter(getUUID()::equals).isEmpty()) { discard(); return; }
        getNavigation().stop(); setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); setNoGravity(true); noPhysics = true;
        setPos(mother.position().add(0, mother.isUsingCombinedModel() ? 0 : 3.5, 0)); setYRot(mother.getYRot()); yBodyRot = mother.yBodyRot;
        if (mother.isUsingCombinedModel()) { setTarget(null); entityData.set(BOW_TICKS, -1); return; }
        if (!validTarget(getTarget(), mother)) {
            setTarget(server.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, mother.getBoundingBox().inflate(15), mob -> validTarget(mob, mother)).stream().findFirst().orElse(null));
            entityData.set(BOW_TICKS, -1);
        }
        if (getTarget() == null) return;
        int bowTicks = getBowTicks() + 1; entityData.set(BOW_TICKS, bowTicks);
        if (bowTicks == 28) {
            var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(server, this, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            arrow.setPos(mother.position().add(0, 3.5, 0));
            arrow.setDeltaMovement(getTarget().position().subtract(arrow.position()).normalize().scale(4));
            server.addFreshEntity(arrow); playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1, 1);
        }
        if (bowTicks >= 60) { setTarget(null); entityData.set(BOW_TICKS, -1); }
    }
    private boolean validTarget(net.minecraft.world.entity.LivingEntity target, GalathNpc mother) {
        if (target == null || !target.isAlive() || mother.isSummonedSkeleton(target.getUUID()) || !mother.getSensing().hasLineOfSight(target)) return false;
        var offset = target.position().subtract(mother.position());
        return offset.horizontalDistanceSqr() <= 225 && offset.x * mother.getLookAngle().x + offset.z * mother.getLookAngle().z >= 0;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putString("motherUUID", entityData.get(MOTHER)); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); try { setMotherUUID(UUID.fromString(in.getStringOr("motherUUID", ""))); } catch (IllegalArgumentException ignored) { setMotherUUID(null); } }
}
