package com.arn.goodmod.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TribeControlPayload(UUID tribe, Action action, BlockPos target, Direction direction, String name) implements CustomPacketPayload {
    public enum Action { CLAIM, FOLLOW, WAIT, ASSIGN, UNASSIGN, TREE, MINE, CANCEL }
    public static final Type<TribeControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sexmod","tribe_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TribeControlPayload> CODEC = StreamCodec.of(
            (buf,p)-> {buf.writeUUID(p.tribe());buf.writeEnum(p.action());buf.writeBlockPos(p.target());buf.writeEnum(p.direction());buf.writeUtf(p.name(),48);},
            buf->new TribeControlPayload(buf.readUUID(),buf.readEnum(Action.class),buf.readBlockPos(),buf.readEnum(Direction.class),buf.readUtf(48)));
    public TribeControlPayload {
        java.util.Objects.requireNonNull(tribe); java.util.Objects.requireNonNull(action); java.util.Objects.requireNonNull(direction);
        target=target.immutable();
        if(name.length()>48)throw new IllegalArgumentException("Tribe name is too long");
    }
    @Override public Type<TribeControlPayload> type(){return TYPE;}
}
