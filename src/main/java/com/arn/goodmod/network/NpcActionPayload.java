package com.arn.goodmod.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcActionPayload(int entityId, UUID entityUuid, int containerId, Action action) implements CustomPacketPayload {
    public enum Action { FOLLOW, WAIT, WANDER, GO_HOME, SET_HOME, DRESS_UP }
    public static final Type<NpcActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "npc_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeUUID(payload.entityUuid());
                buffer.writeVarInt(payload.containerId());
                buffer.writeEnum(payload.action());
            }, buffer -> new NpcActionPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(),
                    buffer.readEnum(Action.class)));
    public NpcActionPayload {
        if (entityId < 0 || containerId < 0) throw new IllegalArgumentException("Invalid entity or menu ID");
        Objects.requireNonNull(entityUuid);
        Objects.requireNonNull(action);
    }
    @Override public Type<NpcActionPayload> type() { return TYPE; }
}
