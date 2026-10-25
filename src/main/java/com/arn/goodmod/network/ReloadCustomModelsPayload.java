package com.arn.goodmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ReloadCustomModelsPayload() implements CustomPacketPayload {
    public static final Type<ReloadCustomModelsPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("sexmod","reload_custom_models"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ReloadCustomModelsPayload> CODEC=StreamCodec.of((buf,p)->{},buf->new ReloadCustomModelsPayload());
    @Override public Type<ReloadCustomModelsPayload> type(){return TYPE;}
}
