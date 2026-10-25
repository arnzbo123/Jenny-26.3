package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModelRequestPayload(UUID request,ModelFile file) implements CustomPacketPayload {
    public static final Type<ModelRequestPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("sexmod","model_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ModelRequestPayload> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.request());buf.writeUtf(p.file().model(),128);buf.writeEnum(p.file().kind());buf.writeVarInt(p.file().length());buf.writeUtf(p.file().sha256(),64);},
            buf->new ModelRequestPayload(buf.readUUID(),new ModelFile(buf.readUtf(128),buf.readEnum(ModelFile.Kind.class),buf.readVarInt(),buf.readUtf(64))));
    public ModelRequestPayload{java.util.Objects.requireNonNull(request);java.util.Objects.requireNonNull(file);}
    @Override public Type<ModelRequestPayload> type(){return TYPE;}
}
