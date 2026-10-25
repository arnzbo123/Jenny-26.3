package com.arn.goodmod.worldgen;

import com.arn.goodmod.registry.GoodcraftEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** The goblin template rotated toward its cave opening, plus g3's resident goblin. */
public final class GoblinLairPiece extends TemplateStructurePiece {
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("sexmod", "goblin");
    private final BlockPos resident;
    private final float yaw;

    public GoblinLairPiece(StructureTemplateManager manager, BlockPos origin, Rotation rotation, BlockPos resident, float yaw) {
        super(GoodcraftWorldgen.GOBLIN_LAIR_PIECE, 0, manager, TEMPLATE, TEMPLATE.toString(), settings(rotation), origin);
        this.resident = resident; this.yaw = yaw;
    }

    public GoblinLairPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(GoodcraftWorldgen.GOBLIN_LAIR_PIECE, tag, manager, template -> settings(Rotation.valueOf(tag.getStringOr("Rot", "NONE"))));
        resident = new BlockPos(tag.getIntOr("ResidentX", 0), tag.getIntOr("ResidentY", 0), tag.getIntOr("ResidentZ", 0));
        yaw = tag.getFloatOr("ResidentYaw", 0);
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(false).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
        tag.putInt("ResidentX", resident.getX()); tag.putInt("ResidentY", resident.getY()); tag.putInt("ResidentZ", resident.getZ());
        tag.putFloat("ResidentYaw", yaw);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBox, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, chunkBox, chunk, pivot);
        // Placement runs once per chunk; only the chunk holding the resident's feet spawns her.
        if (!chunkBox.isInside(resident)) return;
        var goblin = GoodcraftEntities.GOBLIN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (goblin == null) return;
        goblin.setPos(resident.getX() + .5, resident.getY(), resident.getZ() + .5);
        goblin.setYRot(yaw); goblin.setTargetYaw(yaw); goblin.setHomePosition(resident);
        goblin.setLairResident(true); goblin.setPersistenceRequired();
        level.addFreshEntity(goblin);
    }

    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
