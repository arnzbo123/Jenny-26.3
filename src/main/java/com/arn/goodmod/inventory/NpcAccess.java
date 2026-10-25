package com.arn.goodmod.inventory;

import java.util.Optional;
import java.util.UUID;

/** One shared access rule for menus and requests; presence checks cannot be bypassed by operators. */
public final class NpcAccess {
    private NpcAccess() {}
    public static boolean permits(UUID player, Optional<UUID> owner, Optional<UUID> associated,
                                  boolean operator, boolean validPresence) {
        return validPresence && (operator || (owner.isEmpty() || owner.get().equals(player))
                && (associated.isEmpty() || associated.get().equals(player)));
    }
}
