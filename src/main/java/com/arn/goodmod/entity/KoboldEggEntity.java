package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Original i.java hatch timer, damage rule, color, tribe and animation progression. */
public final class KoboldEggEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(KoboldEggEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> COLOR = SynchedEntityData.defineId(KoboldEggEntity.class, EntityDataSerializers.STRING);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID tribeId, ownerUUID;
    public KoboldEggEntity(EntityType<? extends KoboldEggEntity> type, Level level) { super(type, level); setNoAi(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(AGE, 0); data.define(COLOR, "PURPLE"); }
    public int getEggAge() { return entityData.get(AGE); }
    public void setEggAge(int age) { entityData.set(AGE, Math.max(0, age)); }
    public String getEggColor() { return entityData.get(COLOR); }
    public void setEggColor(String color) { if (color.matches("[A-Z_]{1,32}")) entityData.set(COLOR, color); }
    public UUID getTribeId() { return tribeId; }
    public void setTribeId(UUID tribe) { tribeId = tribe; }
    public void setOwnerUUID(UUID owner) { ownerUUID = owner; }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (getEggAge() < 12000) { setEggAge(getEggAge() + 1); return; }
        var member = GoodcraftEntities.KOBOLD.create(level, EntitySpawnReason.BREEDING);
        if (member == null) return;
        if (tribeId == null) tribeId = UUID.randomUUID();
        member.setTribeId(tribeId); member.setTribeColor(getEggColor()); member.setOwnerUUID(ownerUUID);
        var tribes = com.arn.goodmod.world.KoboldTribes.get(level);
        tribes.find(tribeId).ifPresent(tribe -> { member.setOwnerUUID(tribe.owner().orElse(ownerUUID)); member.setTribeColor(tribe.color()); });
        for (KoboldNpc other : level.getEntitiesOfClass(KoboldNpc.class, getBoundingBox().inflate(128))) {
            if (other.getTribeId().filter(tribeId::equals).isEmpty()) continue;
            if (ownerUUID == null) member.setOwnerUUID(other.getOwnerUUID().orElse(null));
            if (!other.getModelCode().isBlank()) member.setModelCode(other.getModelCode());
        }
        member.setPos(getX() + 0.5, getY(), getZ() + 0.5); if (!level.addFreshEntity(member)) return;
        var tribe = tribes.registerSingle(tribeId, getEggColor(), member.getUUID(), java.util.Optional.ofNullable(ownerUUID), blockPosition());
        member.setOwnerUUID(tribe.owner().orElse(null)); member.setTribeColor(tribe.color()); member.setHomePosition(tribe.home());
        level.sendParticles(ParticleTypes.POOF, getX() + 0.5, getY() + 0.5, getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.1);
        playSound(net.minecraft.sounds.SoundEvents.STONE_BREAK, 0.5F, 1); discard();
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { boolean hurt = super.hurtServer(level, source, amount); if (hurt) discard(); return hurt; }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); if (tribeId != null) out.putString("tribeID", tribeId.toString()); if (ownerUUID != null) out.putString("ownerUUID", ownerUUID.toString()); out.putInt("eggAge", getEggAge()); out.putString("egg_color", getEggColor()); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); try { tribeId = UUID.fromString(in.getStringOr("tribeID", "")); } catch (IllegalArgumentException ignored) {} try { ownerUUID = UUID.fromString(in.getStringOr("ownerUUID", "")); } catch (IllegalArgumentException ignored) {} setEggAge(in.getIntOr("eggAge", 0)); setEggColor(in.getStringOr("egg_color", "PURPLE")); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<KoboldEggEntity>("controller", 5, test -> {
            int age = getEggAge();
            String clip = age > 11980 ? "hatch" : age > 11760 ? "veryfast" : age > 10200 ? "fast" : age > 9000 ? "medium" : age > 6000 ? "slow" : "";
            return clip.isEmpty() ? PlayState.STOP : test.setAndContinue(RawAnimation.begin().thenLoop("animation.model." + clip));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
