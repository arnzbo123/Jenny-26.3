package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

/** fq's two spawn histories, scoped to the actual dimension instead of process-global lists. */
public final class EncounterSpawnHistory extends SavedData {
    public static final Codec<EncounterSpawnHistory> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().optionalFieldOf("galath", List.of()).forGetter(EncounterSpawnHistory::galathPositions),
            BlockPos.CODEC.listOf().optionalFieldOf("manglelie", List.of()).forGetter(EncounterSpawnHistory::mangleliePositions)
    ).apply(i, EncounterSpawnHistory::new));
    public static final SavedDataType<EncounterSpawnHistory> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("sexmod", "galath_spawn_list"), EncounterSpawnHistory::new, CODEC, null);
    private final List<BlockPos> galath = new ArrayList<>(), manglelie = new ArrayList<>();
    public EncounterSpawnHistory() {}
    private EncounterSpawnHistory(List<BlockPos> galath, List<BlockPos> manglelie) { this.galath.addAll(galath); this.manglelie.addAll(manglelie); }
    public static EncounterSpawnHistory get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public List<BlockPos> galathPositions() { return List.copyOf(galath); }
    public List<BlockPos> mangleliePositions() { return List.copyOf(manglelie); }
    public boolean permitsGalath(BlockPos pos) { return galath.stream().noneMatch(old -> old.distSqr(pos) < 1_000_000); }
    public boolean permitsManglelie(BlockPos pos) { return galath.stream().noneMatch(old -> old.distSqr(pos) < 490_000) && manglelie.stream().noneMatch(old -> old.distSqr(pos) < 490_000); }
    public void recordGalath(BlockPos pos) { if (!galath.contains(pos)) { galath.add(pos.immutable()); setDirty(); } }
    public void recordManglelie(BlockPos pos) { if (!manglelie.contains(pos)) { manglelie.add(pos.immutable()); setDirty(); } }
    public static boolean replaceNaturalSpawn(ServerLevel level, Entity original) {
        if (original.getType() != EntityTypes.BLAZE && original.getType() != EntityTypes.WITHER_SKELETON) return false;
        BlockPos pos = original.blockPosition(); EncounterSpawnHistory history = get(level);
        if (!history.permitsGalath(pos) || !level.getEntitiesOfClass(GalathNpc.class, new AABB(pos).inflate(1000), npc -> npc.isAlive() && npc.blockPosition().distSqr(pos) < 1_000_000).isEmpty()) return false;
        for (int y = 0; y < 15; y++) if (!level.isEmptyBlock(pos.above(y))) return false;
        for (int y = 0; y < 5; y++) if (!level.getFluidState(pos.below(y)).isEmpty()) return false;
        GalathNpc boss = GoodcraftEntities.GALATH.create(level, EntitySpawnReason.NATURAL);
        if (boss == null) return false;
        boss.setPos(pos.getX(), pos.getY(), pos.getZ());
        if (!level.addFreshEntity(boss)) return false;
        history.recordGalath(pos); original.discard(); return true;
    }
}
