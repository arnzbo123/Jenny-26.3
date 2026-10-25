package com.arn.goodmod.worldgen;

import com.arn.goodmod.GoodcraftMod;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.phys.Vec3;

/**
 * g3's underground goblin lair as a real structure, so /locate finds it. The original tested a random
 * height in every chunk for a solid floor and ceiling and one to four cave openings at door height,
 * then rotated the template so its door faces the opening; this samples the terrain noise the same way.
 */
public final class GoblinLairStructure extends Structure {
    public static final MapCodec<GoblinLairStructure> CODEC = simpleCodec(GoblinLairStructure::new);
    private static final int SIZE = 12;
    /** Wall midpoints at door height, in the original's door-search order, with their rotation data. */
    private record Door(int x, int z, Rotation rotation, BlockPos offset, Vec3 resident, float yaw) {}
    private static final Door[] DOORS = {
            new Door(0, 5, Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, 0, 10), new Vec3(9, 1, 5), 90),
            new Door(5, 0, Rotation.NONE, BlockPos.ZERO, new Vec3(5, 1, 9), 180),
            new Door(5, 11, Rotation.CLOCKWISE_180, new BlockPos(10, 0, 10), new Vec3(5, 1, 1), 0),
            new Door(11, 5, Rotation.CLOCKWISE_90, new BlockPos(10, 0, 0), new Vec3(1, 1, 5), -90)};
    private static final int[][] SUPPORT = {{0, 0}, {11, 0}, {0, 11}, {11, 11}, {5, 5}, {0, 5}, {5, 0}, {5, 11}, {11, 5}};

    public GoblinLairStructure(StructureSettings settings) { super(settings); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!GoodcraftMod.config().shouldGenBuildings()) return Optional.empty();
        int baseX = context.chunkPos().getMinBlockX() + 3, baseZ = context.chunkPos().getMinBlockZ() + 3;
        var height = context.heightAccessor();
        NoiseColumn[] columns = new NoiseColumn[SUPPORT.length];
        for (int i = 0; i < SUPPORT.length; i++) {
            columns[i] = context.chunkGenerator().getBaseColumn(baseX + SUPPORT[i][0], baseZ + SUPPORT[i][1], height, context.randomState());
        }
        // Like g3, start from a random height and take the first that fits, staying below sea level.
        int low = height.getMinY() + 2, high = context.chunkGenerator().getSeaLevel() - 10;
        if (high <= low) return Optional.empty();
        int start = low + context.random().nextInt(high - low);
        for (int i = 0; i < high - low; i++) {
            int y = low + (start - low + i) % (high - low);
            Door door = fit(columns, y);
            if (door == null) continue;
            BlockPos base = new BlockPos(baseX, y, baseZ);
            BlockPos template = base.below().offset(door.offset());
            BlockPos resident = BlockPos.containing(base.getX() + door.resident().x, base.getY() + door.resident().y, base.getZ() + door.resident().z);
            return Optional.of(new GenerationStub(base, pieces -> pieces.addPiece(new GoblinLairPiece(
                    context.structureTemplateManager(), template, door.rotation(), resident, door.yaw()))));
        }
        return Optional.empty();
    }

    /** Solid floor (y-1) and ceiling (y+6) at every sampled column, and one to four open wall midpoints. */
    private static Door fit(NoiseColumn[] columns, int y) {
        for (NoiseColumn column : columns) {
            if (!solid(column.getBlock(y - 1)) || !solid(column.getBlock(y + 6))) return null;
        }
        Door first = null;
        int open = 0;
        for (int i = 0; i < DOORS.length; i++) {
            NoiseColumn column = columns[5 + i];
            if (column.getBlock(y).isAir() && column.getBlock(y + 1).isAir()) { open++; if (first == null) first = DOORS[i]; }
        }
        return open >= 1 && open <= 4 ? first : null;
    }

    private static boolean solid(BlockState state) { return state.isSolid(); }

    @Override public StructureType<?> type() { return GoodcraftWorldgen.GOBLIN_LAIR; }
}
