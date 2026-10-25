package com.arn.goodmod.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An action chosen in the interaction overlay, which is not a container; authorized like other NPC requests. */
public record NpcCommandPayload(int entityId, UUID entityUuid, NpcActionPayload.Action action) implements CustomPacketPayload {
    public static final Type<NpcCommandPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "npc_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcCommandPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeUUID(payload.entityUuid());
                buffer.writeEnum(payload.action());
            }, buffer -> new NpcCommandPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readEnum(NpcActionPayload.Action.class)));
    public NpcCommandPayload {
        if (entityId < 0) throw new IllegalArgumentException("Invalid entity ID");
        Objects.requireNonNull(entityUuid);
        Objects.requireNonNull(action);
    }
    @Override public Type<NpcCommandPayload> type() { return TYPE; }
}
