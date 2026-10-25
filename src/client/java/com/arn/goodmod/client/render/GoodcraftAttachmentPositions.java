package com.arn.goodmod.client.render;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.ManglelieNpc;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** Original dh.a uses Galath's animated mangPos socket, rather than a fixed head height. */
final class GoodcraftAttachmentPositions extends GeoRenderLayer<GoodcraftNpc, Void, GoodcraftNpcRenderState> {
    private record Socket(Vec3 offset, float bodyYaw, float bodyScale, long tick, Object level) {}
    private static final Map<UUID, Socket> SOCKETS = new HashMap<>();
    GoodcraftAttachmentPositions(GoodcraftNpcRenderer renderer) { super(renderer); }

    @Override public void addRenderData(GoodcraftNpc npc, Void related, GoodcraftNpcRenderState state, float partialTick) {
        state.entityUUID = npc.getUUID(); state.levelIdentity = npc.level(); state.worldTick = npc.level().getGameTime();
        state.attachmentOffset = Vec3.ZERO; state.parentBodyScale = 1; state.parentBodyYaw = 0;
        if (!(npc instanceof ManglelieNpc child) || !state.action.equals("RIDE_MOMMY_HEAD")) return;
        child.getMotherUUID().ifPresent(id -> {
            Socket socket = SOCKETS.get(id);
            if (socket == null || socket.level != npc.level() || Math.abs(state.worldTick - socket.tick) > 2) return;
            for (var entity : net.minecraft.client.Minecraft.getInstance().level.entitiesForRendering()) {
                if (!entity.getUUID().equals(id)) continue;
                Vec3 current = entity.getPosition(partialTick).add(socket.offset);
                state.attachmentOffset = current.subtract(npc.getPosition(partialTick));
                state.parentBodyYaw = socket.bodyYaw; state.parentBodyScale = socket.bodyScale;
                break;
            }
        });
    }

    @Override public void preRender(RenderPassInfo<GoodcraftNpcRenderState> pass, SubmitNodeCollector collector) {
        var state = pass.renderState();
        if (!state.kind.equals("galath")) return;
        if (SOCKETS.size() > 512) SOCKETS.entrySet().removeIf(entry -> entry.getValue().level != state.levelIdentity || state.worldTick - entry.getValue().tick > 20);
        float[] body = {0, 1};
        pass.addBoneUpdater((render, bones) -> bones.ifPresent("body", value -> { body[0] = value.getRotY(); body[1] = value.getScaleY(); }));
        pass.addBonePositionListener("mangPos", (world, model, relative) -> {
            if (world != null) SOCKETS.put(state.entityUUID, new Socket(world.subtract(state.getGeckolibData(DataTickets.POSITION)), body[0], body[1], state.worldTick, state.levelIdentity));
        });
    }
}
