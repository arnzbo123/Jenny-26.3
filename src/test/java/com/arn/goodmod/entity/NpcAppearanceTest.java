package com.arn.goodmod.entity;

import com.arn.goodmod.menu.NpcModelCode;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcAppearanceTest {
    @Test void bodySizeSliderPreservesInvertedMolangAnimationScale() {
        assertEquals(0.25, NpcAppearance.koboldAnimationSize(0));
        assertEquals(0.125, NpcAppearance.koboldAnimationSize(50));
        assertEquals(0, NpcAppearance.koboldAnimationSize(100));
    }
    @Test void sourceGeneratedAppearancesAreValidAndRoundTripThroughCustomizationProtocol() {
        RandomSource random = RandomSource.create(91726);
        for (NpcKind kind : new NpcKind[]{NpcKind.KOBOLD, NpcKind.GOBLIN}) for (int i = 0; i < 1000; i++) {
            String code = NpcAppearance.randomCode(kind, random);
            assertEquals(code, NpcModelCode.parse(code, kind).encode());
        }
    }
    @Test void tribeRecolorPreservesAllOtherAppearanceAndAddonSelections() {
        String original = "Hat#Cape#$0-3-9-6-2-70-31-1-2-1-0-";
        String updated = NpcAppearance.replaceSelector(original, 1, 10);
        assertEquals("Hat#Cape#$0-10-9-6-2-70-31-1-2-1-0-", updated);
        assertEquals(updated, NpcModelCode.parse(updated, NpcKind.KOBOLD).encode());
    }
}
