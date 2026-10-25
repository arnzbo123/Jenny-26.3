package com.arn.goodmod.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import java.util.UUID;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;

/** cy custom model carrier, invulnerable except to void damage. */
public final class CustomModelEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(CustomModelEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(CustomModelEntity.class, EntityDataSerializers.STRING);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public CustomModelEntity(EntityType<? extends CustomModelEntity> type, Level level) { super(type, level); setNoAi(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(OWNER, ""); data.define(MODEL, ""); }
    public void setOwnerUUID(UUID uuid) { entityData.set(OWNER, uuid == null ? "" : uuid.toString()); }
    public String getModelIdentifier() { return entityData.get(MODEL); }
    public void setModelIdentifier(String model) { if (model.length() <= 4096) entityData.set(MODEL, model); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return source.is(DamageTypes.FELL_OUT_OF_WORLD) && super.hurtServer(level, source, amount); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putString("ownerUUID", entityData.get(OWNER)); out.putString("model", getModelIdentifier()); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); entityData.set(OWNER, in.getStringOr("ownerUUID", "")); setModelIdentifier(in.getStringOr("model", "")); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
