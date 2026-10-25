package com.arn.goodmod.menu;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Server-supplied opening data; item contents travel through vanilla menu synchronization. */
public record NpcMenuData(int entityId, UUID entityUuid, NpcMenuView view, boolean fishingRod) {
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcMenuData> CODEC = StreamCodec.of(
            (buffer, data) -> {
                buffer.writeVarInt(data.entityId());
                buffer.writeUUID(data.entityUuid());
                buffer.writeEnum(data.view());
                buffer.writeBoolean(data.fishingRod());
            }, buffer -> new NpcMenuData(buffer.readVarInt(), buffer.readUUID(),
                    buffer.readEnum(NpcMenuView.class), buffer.readBoolean()));

    public NpcMenuData {
        if (entityId < 0) throw new IllegalArgumentException("Invalid entity ID");
        Objects.requireNonNull(entityUuid);
        Objects.requireNonNull(view);
    }
}
