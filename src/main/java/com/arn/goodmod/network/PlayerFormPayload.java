package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server sends a single-use selection token; clients can only choose their own form. */
public record PlayerFormPayload(UUID token,String kind) implements CustomPacketPayload {
    public static final Type<PlayerFormPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("sexmod","player_form"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PlayerFormPayload> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.token());buf.writeUtf(p.kind(),32);},buf->new PlayerFormPayload(buf.readUUID(),buf.readUtf(32)));
    public PlayerFormPayload {if(token==null||kind==null||kind.length()>32)throw new IllegalArgumentException("Invalid form selection");}
    @Override public Type<PlayerFormPayload> type(){return TYPE;}
}
