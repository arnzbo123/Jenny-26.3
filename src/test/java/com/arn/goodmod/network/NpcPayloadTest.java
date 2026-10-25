package com.arn.goodmod.network;

import com.arn.goodmod.menu.NpcMenuData;
import com.arn.goodmod.menu.NpcMenuView;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcPayloadTest {
    private static final UUID NPC = UUID.fromString("6dfe7177-c845-469f-997d-d239220c7475");

    @Test void openingPayloadRoundTripsWithoutItemsOrPlayerIdentity() {
        var expected = new OpenNpcMenuPayload(123, NPC, NpcMenuView.STORAGE);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            OpenNpcMenuPayload.CODEC.encode(buffer, expected);
            assertEquals(expected, OpenNpcMenuPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void actionRoundTripsWithMenuSessionIdentity() {
        var expected = new NpcActionPayload(123, NPC, 4, NpcActionPayload.Action.FOLLOW);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            NpcActionPayload.CODEC.encode(buffer, expected);
            assertEquals(expected, NpcActionPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void staleMenuWrongEntityAndRecycledEntityIdAreRejected() {
        var opening = new NpcMenuData(123, NPC, NpcMenuView.EQUIPMENT, false);
        assertTrue(NpcRequestValidation.matchesMenu(new NpcActionPayload(123, NPC, 4, NpcActionPayload.Action.WAIT), 4, opening));
        assertFalse(NpcRequestValidation.matchesMenu(new NpcActionPayload(123, NPC, 3, NpcActionPayload.Action.WAIT), 4, opening));
        assertFalse(NpcRequestValidation.matchesMenu(new NpcActionPayload(124, NPC, 4, NpcActionPayload.Action.WAIT), 4, opening));
        assertFalse(NpcRequestValidation.matchesMenu(new NpcActionPayload(123, UUID.randomUUID(), 4, NpcActionPayload.Action.WAIT), 4, opening));
    }

    @Test void invalidNumericIdsAndUnknownActionsCannotDecode() {
        assertThrows(IllegalArgumentException.class, () -> new OpenNpcMenuPayload(-1, NPC, NpcMenuView.EQUIPMENT));
        assertThrows(IllegalArgumentException.class, () -> new NpcActionPayload(123, NPC, -1, NpcActionPayload.Action.WAIT));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(123);
            buffer.writeUUID(NPC);
            buffer.writeVarInt(4);
            buffer.writeVarInt(999);
            assertThrows(RuntimeException.class, () -> NpcActionPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void appearanceRequestIsBoundedAndPreservesEntityIdentity() {
        var expected = new NpcAppearancePayload(123, NPC, "$0-0-0-0-0-0-0-0-0-");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            NpcAppearancePayload.CODEC.encode(buffer, expected);
            assertEquals(expected, NpcAppearancePayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new NpcAppearancePayload(123, NPC, "x".repeat(4097)));
    }

    @Test void tribeAndPlayerFormRequestsRoundTripWithBoundedNames() {
        var expected = new TribeControlPayload(NPC, TribeControlPayload.Action.MINE, new net.minecraft.core.BlockPos(2,64,3), net.minecraft.core.Direction.NORTH, "Test tribe");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            TribeControlPayload.CODEC.encode(buffer, expected);
            assertEquals(expected, TribeControlPayload.CODEC.decode(buffer));
            var selection = new PlayerFormPayload(NPC, "kobold");
            PlayerFormPayload.CODEC.encode(buffer, selection);
            assertEquals(selection, PlayerFormPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new PlayerFormPayload(NPC, "x".repeat(33)));
    }
}
