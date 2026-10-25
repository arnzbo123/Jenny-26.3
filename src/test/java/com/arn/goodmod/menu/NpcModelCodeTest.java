package com.arn.goodmod.menu;

import com.arn.goodmod.entity.NpcKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcModelCodeTest {
    @Test void preservesNamesAndNormalizesLegacyDelimiters() {
        var parsed = NpcModelCode.parse("Hat#cross#Cape#$00-01-02-15-00-05-07-04-05-", NpcKind.GOBLIN);
        assertEquals(java.util.List.of("Hat", "Cape"), parsed.addons());
        assertEquals("Hat#Cape#$0-1-2-15-0-5-7-4-5-", parsed.encode());
        assertEquals(parsed, NpcModelCode.parse(parsed.encode(), NpcKind.GOBLIN));
        assertEquals(parsed, NpcModelCode.parse(parsed.encode()+"01-", NpcKind.GOBLIN));
    }

    @Test void rejectsPathTraversalNegativeOverflowAndWrongSpeciesSelectors() {
        for (String invalid : new String[]{"../hat", "a/b", "a\\b", "$-1-0-0-0-0-0-0-0-0-", "$4-0-0-0-0-0-0-0-0-", "$0--0-0-0-0-0-0-0-", "a$$", "a#a"}) {
            assertThrows(IllegalArgumentException.class, () -> NpcModelCode.parse(invalid, NpcKind.GOBLIN), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> NpcModelCode.parse("$0-", NpcKind.JENNY));
        assertThrows(IllegalArgumentException.class, () -> NpcModelCode.parse("x".repeat(4097), NpcKind.JENNY));
    }

    @Test void exactKoboldAppearanceRangesMatchRecoveredSource() {
        assertEquals(11, NpcModelCode.parse("$100-10-10-7-2-100-100-2-2-3-1-", NpcKind.KOBOLD).selectors().size());
        assertThrows(IllegalArgumentException.class, () -> NpcModelCode.parse("$101-10-10-7-2-100-100-2-2-3-1-", NpcKind.KOBOLD));
        assertTrue(NpcModelCode.parse("", NpcKind.KOBOLD).selectors().isEmpty());
    }
}
