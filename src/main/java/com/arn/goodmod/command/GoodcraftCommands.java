package com.arn.goodmod.command;

import com.arn.goodmod.entity.GoblinNpc;
import com.arn.goodmod.world.CustomModelCatalog;
import com.arn.goodmod.network.ReloadCustomModelsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.arn.goodmod.network.CustomModelDistribution;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class GoodcraftCommands {
    private GoodcraftCommands() {}
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(Commands.literal("sharecustommodel")
                    .requires(source->source.permissions().hasPermission(Permissions.COMMANDS_OWNER))
                    .then(Commands.argument("model",StringArgumentType.word())
                            .executes(context->CustomModelDistribution.approve(context.getSource(),StringArgumentType.getString(context,"model"),false))
                            .then(Commands.literal("confirm").executes(context->CustomModelDistribution.approve(context.getSource(),StringArgumentType.getString(context,"model"),true)))));
            dispatcher.register(Commands.literal("locatenearestgoblinlair")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER))
                    .executes(context -> locate(context.getSource())));
            dispatcher.register(Commands.literal("reloadcustommodels")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(context -> {
                        var server=context.getSource().getServer();
                        int count=CustomModelCatalog.reload(server);
                        CustomModelDistribution.reset(server);
                        for(var player:server.getPlayerList().getPlayers())ServerPlayNetworking.send(player,new ReloadCustomModelsPayload());
                        context.getSource().sendSuccess(()->Component.literal("Reloaded "+count+" custom models. Clients reload their installed files."),true);
                        return count;
                    }));
        });
    }

    /** gn searched loaded lair goblins rather than forcing structure/chunk generation. */
    static int locate(CommandSourceStack source) {
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Goblin lairs are found in the Overworld."));
            return 0;
        }
        GoblinNpc nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (!(entity instanceof GoblinNpc goblin) || !goblin.isAlive() || !goblin.isLairResident()) continue;
            double candidate = goblin.distanceToSqr(source.getPosition());
            if (candidate < distance) { nearest = goblin; distance = candidate; }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No goblin lair found in the loaded area."));
            return 0;
        }
        BlockPos position = nearest.blockPosition();
        source.sendSuccess(() -> Component.literal("Goblin lair found at " + position.getX() + " " + position.getY() + " " + position.getZ()), false);
        return 1;
    }
}
