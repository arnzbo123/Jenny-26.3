package com.arn.goodmod.client.menu;

import com.arn.goodmod.menu.NpcMenus;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.network.OpenNpcAppearancePayload;
import com.arn.goodmod.network.TribeScreenPayload;
import com.arn.goodmod.network.NpcWishPayload;
import com.arn.goodmod.network.PlayerFormPayload;
import com.arn.goodmod.network.ReloadCustomModelsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

public final class NpcMenuScreens {
    private NpcMenuScreens() {}
    public static void initialize() {
        com.arn.goodmod.client.command.ClientModelDownloads.initialize();
        MenuScreens.register(NpcMenus.INVENTORY, NpcInventoryScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(ReloadCustomModelsPayload.TYPE,(payload,context)->context.client().execute(com.arn.goodmod.client.render.GoodcraftCustomModels::reload));
        ClientPlayNetworking.registerGlobalReceiver(PlayerFormPayload.TYPE,(payload,context)->context.client().execute(()->context.client().setScreenAndShow(new PlayerFormScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(TribeScreenPayload.TYPE,(payload,context)->context.client().execute(()->context.client().setScreenAndShow(new TribeCommandScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(NpcWishPayload.TYPE,(payload,context)->context.client().execute(()->{
            if(payload.action()==NpcWishPayload.Action.OPEN&&context.client().level!=null
                    &&context.client().level.getEntity(payload.entityId()) instanceof GoodcraftNpc npc&&npc.getUUID().equals(payload.entityUuid())) {
                context.client().setScreenAndShow(new NpcWishScreen(payload));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(com.arn.goodmod.network.OpenNpcInteractionPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().level != null && context.client().level.getEntity(payload.entityId()) instanceof GoodcraftNpc npc
                    && npc.getUUID().equals(payload.entityUuid())) context.client().setScreenAndShow(new NpcInteractionScreen(payload));
        }));
        ClientPlayNetworking.registerGlobalReceiver(OpenNpcAppearancePayload.TYPE, (payload,context) -> context.client().execute(() -> {
            if (context.client().level != null && context.client().level.getEntity(payload.entityId()) instanceof GoodcraftNpc npc
                    && npc.getUUID().equals(payload.entityUuid())) openAppearance(npc);
        }));
    }
    public static void openAppearance(GoodcraftNpc npc) { Minecraft.getInstance().setScreenAndShow(new NpcAppearanceScreen(npc)); }
}
