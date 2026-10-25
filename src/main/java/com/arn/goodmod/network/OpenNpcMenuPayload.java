package com.arn.goodmod.network;

import com.arn.goodmod.menu.NpcMenuView;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Replaces bo/f3 requests; identity of the requesting player comes from the connection. */
public record OpenNpcMenuPayload(int entityId, UUID entityUuid, NpcMenuView view) implements CustomPacketPayload {
    public static final Type<OpenNpcMenuPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "open_npc_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcMenuPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeUUID(payload.entityUuid());
                buffer.writeEnum(payload.view());
            }, buffer -> new OpenNpcMenuPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readEnum(NpcMenuView.class)));
    public OpenNpcMenuPayload {
        if (entityId < 0) throw new IllegalArgumentException("Invalid entity ID");
        Objects.requireNonNull(entityUuid);
        Objects.requireNonNull(view);
    }
    @Override public Type<OpenNpcMenuPayload> type() { return TYPE; }
}
