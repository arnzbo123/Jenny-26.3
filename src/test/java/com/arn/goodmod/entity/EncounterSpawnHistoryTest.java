package com.arn.goodmod.entity;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncounterSpawnHistoryTest {
    @Test void spacingPersistsAfterCodecReloadWhenOriginalEntitiesAreUnloaded() {
        var history = new EncounterSpawnHistory(); history.recordGalath(new BlockPos(20, 70, -40));
        history.recordManglelie(new BlockPos(1500, 70, -40));
        var encoded = EncounterSpawnHistory.CODEC.encodeStart(JsonOps.INSTANCE, history).getOrThrow();
        var loaded = EncounterSpawnHistory.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertFalse(loaded.permitsGalath(new BlockPos(1019, 70, -40)));
        assertTrue(loaded.permitsGalath(new BlockPos(1020, 70, -40)));
        assertFalse(loaded.permitsManglelie(new BlockPos(1999, 70, -40)));
        assertTrue(loaded.permitsManglelie(new BlockPos(2200, 70, -40)));
    }
    @Test void galathHistoryDoesNotAcquireManglelieOnlySpacingAndUsesThreeDimensions() {
        var history = new EncounterSpawnHistory(); history.recordManglelie(BlockPos.ZERO);
        assertTrue(history.permitsGalath(BlockPos.ZERO));
        assertFalse(history.permitsManglelie(new BlockPos(0, 699, 0)));
        assertTrue(history.permitsManglelie(new BlockPos(0, 700, 0)));
        history.recordManglelie(BlockPos.ZERO); assertEquals(1, history.mangleliePositions().size());
    }
}
