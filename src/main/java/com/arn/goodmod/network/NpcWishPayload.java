package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcWishPayload(int entityId,UUID entityUuid,Action action) implements CustomPacketPayload {
    public enum Action { OPEN, WEALTH, DISMISS }
    public static final Type<NpcWishPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("sexmod","npc_wish"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcWishPayload> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.entityId());buf.writeUUID(p.entityUuid());buf.writeEnum(p.action());},
            buf->new NpcWishPayload(buf.readVarInt(),buf.readUUID(),buf.readEnum(Action.class)));
    public NpcWishPayload {if(entityId<0||entityUuid==null||action==null)throw new IllegalArgumentException("Invalid wish request");}
    @Override public Type<NpcWishPayload> type(){return TYPE;}
}
