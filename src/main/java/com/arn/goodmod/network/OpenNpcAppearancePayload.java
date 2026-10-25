package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenNpcAppearancePayload(int entityId, UUID entityUuid) implements CustomPacketPayload {
    public static final Type<OpenNpcAppearancePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "open_npc_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcAppearancePayload> CODEC = StreamCodec.of(
            (buf,p) -> { buf.writeVarInt(p.entityId()); buf.writeUUID(p.entityUuid()); },
            buf -> new OpenNpcAppearancePayload(buf.readVarInt(), buf.readUUID()));
    @Override public Type<OpenNpcAppearancePayload> type() { return TYPE; }
}
