package com.arn.goodmod.worldgen;

import com.arn.goodmod.GoodcraftMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * g3's house placement as a real structure, so /locate and structure-aware generation see it.
 * Keeps the original footprint origin, biome-at-corner check, height tolerance and dry-foundation rule.
 * Each house has its own structure set, dense enough that every house type is found within a short trip,
 * as the original's first-fitting-chunk placement made them.
 */
public final class LegacyBuildingStructure extends Structure {
    public static final MapCodec<LegacyBuildingStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Identifier.CODEC.fieldOf("template").forGetter(s -> s.template),
            Codec.intRange(0, 64).fieldOf("max_height_difference").forGetter(s -> s.maxHeightDifference),
            Codec.BOOL.fieldOf("foundation").forGetter(s -> s.foundation)
    ).apply(i, LegacyBuildingStructure::new));
    private static final int SAMPLE_STEP = 3;
    private static final int FOUNDATION_SLACK = 2;
    private static final int[][] CANDIDATE_OFFSETS = {{0, 0}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}, {6, 6}, {-6, -6}};
    private final Identifier template;
    private final int maxHeightDifference;
    private final boolean foundation;

    public LegacyBuildingStructure(StructureSettings settings, Identifier template, int maxHeightDifference, boolean foundation) {
        super(settings);
        this.template = template; this.maxHeightDifference = maxHeightDifference; this.foundation = foundation;
    }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!GoodcraftMod.config().shouldGenBuildings()) return Optional.empty();
        var loaded = context.structureTemplateManager().get(template);
        if (loaded.isEmpty()) return Optional.empty();
        var size = loaded.get().getSize();
        int centerX = context.chunkPos().getMinBlockX() + (16 - size.getX()) / 2;
        int centerZ = context.chunkPos().getMinBlockZ() + (16 - size.getZ()) / 2;
        // g3 scanned every chunk of a live world and kept the first that fit. A structure set only offers one
        // chunk per region, so try a few footprints around it before giving the region up.
        for (int[] offset : CANDIDATE_OFFSETS) {
            BlockPos origin = fit(context, centerX + offset[0], centerZ + offset[1], size.getX(), size.getZ());
            if (origin != null) return Optional.of(new GenerationStub(origin, pieces ->
                    pieces.addPiece(new LegacyBuildingPiece(context.structureTemplateManager(), template, origin))));
        }
        return Optional.empty();
    }

    private BlockPos fit(GenerationContext context, int x, int z, int sizeX, int sizeZ) {
        // g3 tests the biome at the footprint corner, y 80, before measuring terrain.
        var biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(80), QuartPos.fromBlock(z));
        if (!context.validBiome().test(biome)) return null;
        var generator = context.chunkGenerator();
        // The beard terrain adaptation fills and trims around the footprint, replacing g3's dirt fill loop,
        // so a founded building tolerates a little more slope than the original's exact-column limit.
        int tolerance = maxHeightDifference + (foundation ? FOUNDATION_SLACK : 0);
        int highest = Integer.MIN_VALUE, lowest = Integer.MAX_VALUE;
        for (int dx : samples(sizeX)) for (int dz : samples(sizeZ)) {
            int surface = generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - 1;
            if (foundation && generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1 < surface) {
                return null; // Water at the surface.
            }
            highest = Math.max(highest, surface); lowest = Math.min(lowest, surface);
            if (highest - lowest > tolerance) return null;
        }
        return new BlockPos(x, highest, z);
    }

    /** Every third column plus the far edge; the original scanned every column of a live world. */
    private static int[] samples(int length) {
        return IntStream.concat(IntStream.iterate(0, i -> i < length - 1, i -> i + SAMPLE_STEP), IntStream.of(length - 1)).distinct().toArray();
    }

    @Override public StructureType<?> type() { return GoodcraftWorldgen.LEGACY_BUILDING; }
}
