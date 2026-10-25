package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModelChunkPayload(UUID request,int offset,byte[] bytes,boolean last) implements CustomPacketPayload {
    public static final Type<ModelChunkPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("sexmod","model_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ModelChunkPayload> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.request());buf.writeVarInt(p.offset());buf.writeByteArray(p.bytes());buf.writeBoolean(p.last());},
            buf->new ModelChunkPayload(buf.readUUID(),buf.readVarInt(),buf.readByteArray(ModelFile.CHUNK_SIZE),buf.readBoolean()));
    public ModelChunkPayload{if(request==null||offset<0||offset>ModelFile.MAX_TOTAL||bytes==null||bytes.length>ModelFile.CHUNK_SIZE)throw new IllegalArgumentException("Invalid model chunk");bytes=bytes.clone();}
    @Override public Type<ModelChunkPayload> type(){return TYPE;}
}
