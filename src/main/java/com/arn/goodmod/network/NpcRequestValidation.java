package com.arn.goodmod.network;

import com.arn.goodmod.menu.NpcMenuData;

/** The server checks live ownership separately; this rejects stale or cross-NPC menu requests. */
public final class NpcRequestValidation {
    private NpcRequestValidation() {}
    public static boolean matchesMenu(NpcActionPayload request, int activeContainerId, NpcMenuData opening) {
        return request.containerId() == activeContainerId && request.entityId() == opening.entityId()
                && request.entityUuid().equals(opening.entityUuid());
    }
}
