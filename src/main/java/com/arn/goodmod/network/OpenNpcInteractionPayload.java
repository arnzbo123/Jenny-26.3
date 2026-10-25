package com.arn.goodmod.network;

import com.arn.goodmod.scene.SceneMenus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Opens the original m interaction overlay: the equipment side panel and companion icons when {@code icons},
 * an inventory shortcut when {@code storage}, and her action column with the price of each action.
 */
public record OpenNpcInteractionPayload(int entityId, UUID entityUuid, List<ItemStack> equipment, boolean icons, boolean storage,
                                        List<SceneMenus.Action> actions) implements CustomPacketPayload {
    public static final Type<OpenNpcInteractionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod", "open_npc_interaction"));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> EQUIPMENT = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(7));
    private static final StreamCodec<RegistryFriendlyByteBuf, SceneMenus.Action> ACTION = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), SceneMenus.Action::key, ByteBufCodecs.stringUtf8(64), SceneMenus.Action::label,
            ItemStack.OPTIONAL_STREAM_CODEC, SceneMenus.Action::cost, SceneMenus.Action::new);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<SceneMenus.Action>> ACTIONS = ACTION.apply(ByteBufCodecs.list(8));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcInteractionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeUUID(payload.entityUuid());
                EQUIPMENT.encode(buffer, payload.equipment());
                buffer.writeBoolean(payload.icons()); buffer.writeBoolean(payload.storage());
                ACTIONS.encode(buffer, payload.actions());
            }, buffer -> new OpenNpcInteractionPayload(buffer.readVarInt(), buffer.readUUID(), EQUIPMENT.decode(buffer),
                    buffer.readBoolean(), buffer.readBoolean(), ACTIONS.decode(buffer)));
    public OpenNpcInteractionPayload {
        if (entityId < 0) throw new IllegalArgumentException("Invalid entity ID");
        Objects.requireNonNull(entityUuid);
        equipment = List.copyOf(equipment);
        actions = List.copyOf(actions);
    }
    @Override public Type<OpenNpcInteractionPayload> type() { return TYPE; }
}
