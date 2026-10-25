package com.arn.goodmod.client.render;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.menu.NpcModelCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Saved values from older versions must not abort a render frame. */
final class RenderModelCode {
    private static final Set<java.util.UUID> WARNED = new HashSet<>();
    private static final NpcModelCode EMPTY = new NpcModelCode(List.of(), List.of());
    private RenderModelCode() {}
    static NpcModelCode read(GoodcraftNpc npc) {
        try { return NpcModelCode.parse(npc.getModelCode(), npc.getNpcKind()); }
        catch (IllegalArgumentException error) {
            if (WARNED.add(npc.getUUID())) GoodcraftMod.LOGGER.warn("Ignoring invalid saved model code for {}: {}", npc.getUUID(), error.getMessage());
            return EMPTY;
        }
    }
}
