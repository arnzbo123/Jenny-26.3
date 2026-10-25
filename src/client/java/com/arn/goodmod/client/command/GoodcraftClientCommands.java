package com.arn.goodmod.client.command;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.menu.NpcModelCode;
import com.arn.goodmod.network.NpcAppearancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public final class GoodcraftClientCommands {
    private GoodcraftClientCommands() {}
    public static void initialize() {
        ClientPreferences.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            dispatcher.register(ClientCommands.literal("futa")
                    .then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                        try {
                            ClientPreferences.setAlternateBody(BoolArgumentType.getBool(context, "enabled"));
                            context.getSource().sendFeedback(Component.literal("Appearance preference saved."));
                            return 1;
                        } catch (IOException exception) {
                            context.getSource().sendError(Component.literal("Could not save the appearance preference: " + exception.getMessage()));
                            return 0;
                        }
                    })));
            dispatcher.register(ClientCommands.literal("whitelistserver")
                    .executes(context -> trust(context.getSource(), false))
                    .then(ClientCommands.literal("confirm").executes(context -> trust(context.getSource(), true))));
            dispatcher.register(ClientCommands.literal("setmodelcode")
                    .then(ClientCommands.argument("code", StringArgumentType.greedyString()).executes(context -> {
                        GoodcraftNpc npc = selectedNpc(context.getSource());
                        if (npc == null) {
                            context.getSource().sendError(Component.literal("Look at an NPC or use your active player form."));
                            return 0;
                        }
                        try {
                            NpcModelCode code = NpcModelCode.parse(StringArgumentType.getString(context, "code"), npc.getNpcKind());
                            ClientPlayNetworking.send(new NpcAppearancePayload(npc.getId(),npc.getUUID(),code.encode()));
                            return 1;
                        }
                        catch (IllegalArgumentException invalid) {
                            context.getSource().sendError(Component.literal(invalid.getMessage()));
                            return 0;
                        }
                    })));
        });
    }

    private static int trust(FabricClientCommandSource source, boolean confirm) {
        var server = source.getClient().getCurrentServer();
        if (server == null) {
            source.sendError(Component.literal("This command applies to multiplayer servers."));
            return 0;
        }
        // Retain the port in new entries so trust cannot silently expand to another server.
        String address = server.ip.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (ClientPreferences.trustedServers().stream().anyMatch(value->value.trim().equalsIgnoreCase(address))) {
                ClientModelDownloads.startTrustedDownloads();
                source.sendFeedback(Component.literal("This server is already trusted for custom models."));
                return 1;
            }
            if (!confirm) {
                source.sendFeedback(Component.literal("Trusting " + address + " allows its custom model files. To confirm, use /whitelistserver confirm."));
                return 0;
            }
            ClientPreferences.trustServer(address);
            com.arn.goodmod.client.render.GoodcraftCustomModels.reload();
            ClientModelDownloads.startTrustedDownloads();
            source.sendFeedback(Component.literal("Server preference saved. Models explicitly shared by its owner may now download."));
            return 1;
        } catch (IOException exception) {
            source.sendError(Component.literal("Could not save server trust: " + exception.getMessage()));
            return 0;
        }
    }

    private static GoodcraftNpc selectedNpc(FabricClientCommandSource source) {
        if (source.getClient().hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof GoodcraftNpc npc) return npc;
        for (Entity entity : source.getLevel().entitiesForRendering()) {
            if (entity instanceof GoodcraftNpc npc && npc.isPlayerForm()
                    && npc.getAssociatedPlayerUUID().filter(source.getPlayer().getUUID()::equals).isPresent()) return npc;
        }
        return null;
    }
}
