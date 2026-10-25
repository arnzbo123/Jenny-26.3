package com.arn.goodmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-only scene effects the original triggered from keyframes on the partner's client (fh black screen). */
public record SceneEffectPayload(int effect) implements CustomPacketPayload {
    public static final int BLACKSCREEN = 0;
    public static final Type<SceneEffectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "scene_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneEffectPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.effect()), buffer -> new SceneEffectPayload(buffer.readVarInt()));
    @Override public Type<SceneEffectPayload> type() { return TYPE; }
}
