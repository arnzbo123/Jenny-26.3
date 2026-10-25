package com.arn.goodmod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** One original template, unrotated as g3 placed it, including the residents saved in the template. */
public final class LegacyBuildingPiece extends TemplateStructurePiece {
    public LegacyBuildingPiece(StructureTemplateManager manager, Identifier template, BlockPos origin) {
        super(GoodcraftWorldgen.LEGACY_BUILDING_PIECE, 0, manager, template, template.toString(), settings(), origin);
    }

    public LegacyBuildingPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(GoodcraftWorldgen.LEGACY_BUILDING_PIECE, tag, manager, template -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setRotation(Rotation.NONE).setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
