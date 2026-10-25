package com.arn.goodmod.network;

import com.arn.goodmod.menu.NpcModelCode;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAppearancePayload(int entityId, UUID entityUuid, String code) implements CustomPacketPayload {
    public static final Type<NpcAppearancePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "npc_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcAppearancePayload> CODEC = StreamCodec.of(
            (buf,p) -> { buf.writeVarInt(p.entityId()); buf.writeUUID(p.entityUuid()); buf.writeUtf(p.code(), NpcModelCode.MAX_LENGTH); },
            buf -> new NpcAppearancePayload(buf.readVarInt(), buf.readUUID(), buf.readUtf(NpcModelCode.MAX_LENGTH)));
    public NpcAppearancePayload {
        if (entityId < 0 || entityUuid == null || code == null || code.length() > NpcModelCode.MAX_LENGTH) throw new IllegalArgumentException("Invalid appearance request");
    }
    @Override public Type<NpcAppearancePayload> type() { return TYPE; }
}
