package com.arn.goodmod.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A button in the m overlay's action column; the server re-checks it against the menu it would offer. */
public record SceneActionPayload(int entityId, UUID entityUuid, String action) implements CustomPacketPayload {
    public static final Type<SceneActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "scene_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeVarInt(payload.entityId()); buffer.writeUUID(payload.entityUuid()); buffer.writeUtf(payload.action(), 64); },
            buffer -> new SceneActionPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(64)));
    public SceneActionPayload {
        if (entityId < 0 || action.isEmpty() || action.length() > 64) throw new IllegalArgumentException("Invalid scene action");
        Objects.requireNonNull(entityUuid);
    }
    @Override public Type<SceneActionPayload> type() { return TYPE; }
}
