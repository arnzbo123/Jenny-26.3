package com.arn.goodmod.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** d3's scene keys: sneak held (faster), jump pressed with a full meter (finish), jump held otherwise (leave). */
public record SceneInputPayload(int entityId, UUID entityUuid, boolean faster, boolean finish, boolean escape) implements CustomPacketPayload {
    public static final Type<SceneInputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "scene_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneInputPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId()); buffer.writeUUID(payload.entityUuid());
                buffer.writeByte((payload.faster() ? 1 : 0) | (payload.finish() ? 2 : 0) | (payload.escape() ? 4 : 0));
            }, buffer -> {
                int id = buffer.readVarInt(); UUID uuid = buffer.readUUID(); int flags = buffer.readByte();
                return new SceneInputPayload(id, uuid, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            });
    public SceneInputPayload {
        if (entityId < 0) throw new IllegalArgumentException("Invalid entity ID");
        Objects.requireNonNull(entityUuid);
    }
    @Override public Type<SceneInputPayload> type() { return TYPE; }
}
