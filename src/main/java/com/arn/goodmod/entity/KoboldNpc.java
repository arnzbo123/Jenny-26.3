package com.arn.goodmod.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** ff tribe identity is persisted on each member, independent of loaded-world indexes. */
public final class KoboldNpc extends HumanoidNpc {
    private static final EntityDataAccessor<String> TRIBE = SynchedEntityData.defineId(KoboldNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> COLOR = SynchedEntityData.defineId(KoboldNpc.class, EntityDataSerializers.STRING);
    public KoboldNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.KOBOLD, false); if (!level.isClientSide()) setModelCode(NpcAppearance.randomCode(NpcKind.KOBOLD, random)); goalSelector.addGoal(1, new KoboldWorkGoal(this)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(TRIBE, ""); data.define(COLOR, "PURPLE"); }
    public Optional<UUID> getTribeId() { try { return Optional.of(UUID.fromString(entityData.get(TRIBE))); } catch (IllegalArgumentException e) { return Optional.empty(); } }
    public void setTribeId(UUID tribe) { entityData.set(TRIBE, tribe == null ? "" : tribe.toString()); }
    public String getTribeColor() { return entityData.get(COLOR); }
    public void setTribeColor(String color) { int index = NpcAppearance.KOBOLD_COLORS.indexOf(color); if (index >= 0) { entityData.set(COLOR, color); setModelCode(NpcAppearance.replaceSelector(getModelCode(), 1, index)); } }
    @Override public void tick() {
        if (level() instanceof net.minecraft.server.level.ServerLevel server && getTribeId().isPresent()) {
            com.arn.goodmod.world.KoboldTribes.get(server).find(getTribeId().get()).ifPresent(tribe -> {
                if (!getOwnerUUID().equals(tribe.owner())) setOwnerUUID(tribe.owner().orElse(null));
                if (!getTribeColor().equals(tribe.color())) setTribeColor(tribe.color());
                if (tribe.following() && getMovementMode() != MovementMode.FOLLOW) setMovementMode(MovementMode.FOLLOW);
                if (!tribe.following() && getMovementMode() == MovementMode.FOLLOW) setMovementMode(MovementMode.WANDER);
            });
        }
        super.tick();
    }
    @Override public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            var tribes = com.arn.goodmod.world.KoboldTribes.get(server); tribes.releaseWork(getUUID());
            if (reason == net.minecraft.world.entity.Entity.RemovalReason.KILLED || reason == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
                getTribeId().ifPresent(id -> tribes.removeMember(id, getUUID()));
        }
        super.remove(reason);
    }
    @Override public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.world.entity.SpawnGroupData data) {
        var result = super.finalizeSpawn(level, difficulty, reason, data);
        if (getTribeId().isEmpty()) {
            UUID id = UUID.randomUUID();
            String color = com.arn.goodmod.world.KoboldTribes.COLORS.get(random.nextInt(com.arn.goodmod.world.KoboldTribes.COLORS.size()));
            com.arn.goodmod.world.KoboldTribes.get(level.getLevel()).registerSingle(id, color, getUUID(), Optional.empty(), blockPosition());
            setTribeId(id); setTribeColor(color); setPersistenceRequired();
        }
        return result;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putString("tribeID", entityData.get(TRIBE)); out.putString("tribeColor", getTribeColor()); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); try { setTribeId(UUID.fromString(in.getStringOr("tribeID", ""))); } catch (IllegalArgumentException ignored) {} setTribeColor(in.getStringOr("tribeColor", "PURPLE")); }
}
