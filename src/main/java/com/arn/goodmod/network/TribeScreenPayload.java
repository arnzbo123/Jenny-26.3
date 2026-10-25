package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TribeScreenPayload(UUID tribe,String name,String color,boolean owned,boolean following,int members,int beds,int chests,int jobs,
                                 BlockPos target,Direction direction,String status) implements CustomPacketPayload {
    public static final Type<TribeScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod","tribe_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TribeScreenPayload> CODEC = StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.tribe());buf.writeUtf(p.name(),48);buf.writeUtf(p.color(),32);buf.writeBoolean(p.owned());buf.writeBoolean(p.following());
                buf.writeVarInt(p.members());buf.writeVarInt(p.beds());buf.writeVarInt(p.chests());buf.writeVarInt(p.jobs());buf.writeBlockPos(p.target());buf.writeEnum(p.direction());buf.writeUtf(p.status(),256);},
            buf->new TribeScreenPayload(buf.readUUID(),buf.readUtf(48),buf.readUtf(32),buf.readBoolean(),buf.readBoolean(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readBlockPos(),buf.readEnum(Direction.class),buf.readUtf(256)));
    @Override public Type<TribeScreenPayload> type(){return TYPE;}
}
