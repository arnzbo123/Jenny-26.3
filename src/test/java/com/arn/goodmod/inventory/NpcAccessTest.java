package com.arn.goodmod.inventory;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcAccessTest {
    private final UUID player = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    @Test void unownedNpcIsAccessibleButAnotherOwnersNpcIsNot() {
        assertTrue(NpcAccess.permits(player, Optional.empty(), Optional.empty(), false, true));
        assertTrue(NpcAccess.permits(player, Optional.of(player), Optional.empty(), false, true));
        assertFalse(NpcAccess.permits(player, Optional.of(other), Optional.empty(), false, true));
    }
    @Test void playerFormIsPrivateEvenWhenItHasNoSeparateOwner() {
        assertFalse(NpcAccess.permits(player, Optional.empty(), Optional.of(other), false, true));
        assertFalse(NpcAccess.permits(player, Optional.of(player), Optional.of(other), false, true));
        assertTrue(NpcAccess.permits(player, Optional.of(player), Optional.of(player), false, true));
    }
    @Test void operatorCanManageOwnershipButCannotBypassPresenceChecks() {
        assertTrue(NpcAccess.permits(player, Optional.of(other), Optional.of(other), true, true));
        assertFalse(NpcAccess.permits(player, Optional.empty(), Optional.empty(), true, false));
        assertFalse(NpcAccess.permits(player, Optional.of(player), Optional.of(player), false, false));
    }
}
