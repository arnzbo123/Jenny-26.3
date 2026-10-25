package com.arn.goodmod.network;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.AllieNpc;
import com.arn.goodmod.entity.MovementMode;
import com.arn.goodmod.menu.NpcInventoryMenu;
import com.arn.goodmod.menu.NpcMenus;
import com.arn.goodmod.menu.NpcModelCode;
import com.arn.goodmod.registry.GoodcraftItems;
import com.arn.goodmod.world.CustomModelCatalog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class NpcNetworking {
    private NpcNetworking() {}

    public static void initialize() {
        CustomModelDistribution.initialize();
        PayloadTypeRegistry.serverboundPlay().register(OpenNpcMenuPayload.TYPE, OpenNpcMenuPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NpcActionPayload.TYPE, NpcActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NpcCommandPayload.TYPE, NpcCommandPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenNpcInteractionPayload.TYPE, OpenNpcInteractionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SceneActionPayload.TYPE, SceneActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SceneInputPayload.TYPE, SceneInputPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SceneEffectPayload.TYPE, SceneEffectPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SceneActionPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            GoodcraftNpc npc = authorizedNpc(context.player(), payload.entityId(), payload.entityUuid());
            if (npc != null) npc.scene().request(context.player(), payload.action());
        }));
        ServerPlayNetworking.registerGlobalReceiver(SceneInputPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            // Only the bound partner's own input moves a scene; nothing here grants access to another player's.
            if (context.player().level().getEntity(payload.entityId()) instanceof GoodcraftNpc npc && npc.getUUID().equals(payload.entityUuid()))
                npc.scene().input(context.player(), payload.faster(), payload.finish(), payload.escape());
        }));
        PayloadTypeRegistry.serverboundPlay().register(NpcAppearancePayload.TYPE, NpcAppearancePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenNpcAppearancePayload.TYPE, OpenNpcAppearancePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReloadCustomModelsPayload.TYPE,ReloadCustomModelsPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(CustomModelCatalog::reload);
        // s.a: a partner who leaves mid-scene is released, and nobody rejoins still floating from one.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            for (GoodcraftNpc npc : player.level().getEntitiesOfClass(GoodcraftNpc.class, player.getBoundingBox().inflate(32),
                    npc -> npc.scene().partner().filter(player.getUUID()::equals).isPresent())) npc.scene().end();
        });
        // d3.a(MouseEvent): a frozen scene partner cannot attack, mine, place or use items.
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
                com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                com.arn.goodmod.scene.SceneDirector.isLockedPlayer(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.getPlayer().isNoGravity()) handler.getPlayer().setNoGravity(false);
        });
        PayloadTypeRegistry.serverboundPlay().register(NpcWishPayload.TYPE,NpcWishPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NpcWishPayload.TYPE,NpcWishPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NpcWishPayload.TYPE,(payload,context)->context.server().execute(()->{
            if(!(authorizedNpc(context.player(),payload.entityId(),payload.entityUuid()) instanceof AllieNpc allie)||!allie.canRequestWish(context.player()))return;
            switch(payload.action()) {
                case WEALTH -> allie.grantWealthWish(context.player());
                case DISMISS -> allie.dismiss(context.player());
                case OPEN -> { }
            }
        }));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof GoodcraftNpc npc) || !player.getItemInHand(hand).is(GoodcraftItems.NPC_EDITOR_WAND)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!npc.canPlayerUse(player)) return InteractionResult.FAIL;
                ServerPlayNetworking.send(serverPlayer, new OpenNpcAppearancePayload(npc.getId(), npc.getUUID()));
            }
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(NpcAppearancePayload.TYPE, (payload, context) -> context.server().execute(() -> {
            GoodcraftNpc npc = authorizedNpc(context.player(), payload.entityId(), payload.entityUuid());
            if (npc == null) return;
            try {
                NpcModelCode code = NpcModelCode.parse(payload.code(), npc.getNpcKind());
                CustomModelCatalog.validate(code,npc.getNpcKind());
                npc.setModelCode(code.encode());
                context.player().sendSystemMessage(Component.literal("Appearance updated."));
            } catch (IllegalArgumentException invalid) {
                context.player().sendSystemMessage(Component.literal(invalid.getMessage()));
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(OpenNpcMenuPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                GoodcraftNpc npc = authorizedNpc(context.player(), payload.entityId(), payload.entityUuid());
                if (npc != null) NpcMenus.open(context.player(), npc, payload.view());
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NpcActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> applyAction(context.player(), payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(NpcCommandPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            GoodcraftNpc npc = authorizedNpc(context.player(), payload.entityId(), payload.entityUuid());
            if (npc != null) perform(context.player(), npc, payload.action());
        }));
    }

    /** m: right-clicking a girl opens the overlay; returns false when she has nothing to offer this player. */
    public static boolean openInteraction(ServerPlayer player, GoodcraftNpc npc) {
        boolean icons = npc.canPlayerUse(player) && com.arn.goodmod.scene.SceneMenus.showsCompanionIcons(npc) && npc.scene().partner().isEmpty();
        boolean storage = npc.supportsStorage() && npc.canPlayerUse(player) && npc.getOwnerUUID().filter(player.getUUID()::equals).isPresent();
        var actions = npc.canPlayerUse(player) || npc.scene().partner().filter(player.getUUID()::equals).isPresent()
                ? com.arn.goodmod.scene.SceneMenus.actions(npc, player) : java.util.List.<com.arn.goodmod.scene.SceneMenus.Action>of();
        if (!icons && !storage && actions.isEmpty()) return false;
        var equipment = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        if (icons) for (int slot = 0; slot < (npc.supportsFishingRod() ? 7 : 6); slot++) equipment.add(npc.getEquipmentInventory().getItem(slot).copy());
        ServerPlayNetworking.send(player, new OpenNpcInteractionPayload(npc.getId(), npc.getUUID(), equipment, icons, storage, actions));
        return true;
    }

    public static void openWish(ServerPlayer player,AllieNpc allie) {
        if(allie.canRequestWish(player))ServerPlayNetworking.send(player,new NpcWishPayload(allie.getId(),allie.getUUID(),NpcWishPayload.Action.OPEN));
    }

    private static GoodcraftNpc authorizedNpc(ServerPlayer player, int entityId, UUID entityUuid) {
        if (!player.isAlive() || player.isSpectator()) return null;
        if (!(player.level().getEntity(entityId) instanceof GoodcraftNpc npc) || !npc.getUUID().equals(entityUuid)) return null;
        // Her scene partner keeps answering her mid-scene prompts (Bia's talk) even while she is bound to him.
        boolean partner = npc.scene().partner().filter(player.getUUID()::equals).isPresent() && npc.distanceToSqr(player) <= 64;
        return partner || npc.canPlayerUse(player) ? npc : null;
    }

    static void applyAction(ServerPlayer player, NpcActionPayload payload) {
        if (!(player.containerMenu instanceof NpcInventoryMenu menu)
                || !NpcRequestValidation.matchesMenu(payload, menu.containerId, menu.opening())
                || !menu.stillValid(player)) return;
        GoodcraftNpc npc = authorizedNpc(player, payload.entityId(), payload.entityUuid());
        if (npc == null || npc != menu.npc()) return;
        perform(player, npc, payload.action());
        menu.broadcastChanges();
    }

    private static void perform(ServerPlayer player, GoodcraftNpc npc, NpcActionPayload.Action action) {
        switch (action) {
            case FOLLOW -> {
                if (npc.getOwnerUUID().isEmpty()) npc.setOwnerUUID(player.getUUID());
                npc.setMovementMode(MovementMode.FOLLOW);
            }
            case WAIT -> npc.setMovementMode(MovementMode.WAIT);
            case WANDER -> npc.setMovementMode(MovementMode.WANDER);
            case GO_HOME -> npc.setMovementMode(MovementMode.HOME);
            case SET_HOME -> { npc.setHomePosition(npc.blockPosition()); npc.announceHome(player); }
            case DRESS_UP -> { if (npc.getModelVariant() == 0) npc.setModelVariant(1); }
        }
    }
}
