package com.arn.goodmod.menu;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.GoodcraftNpc;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class NpcMenus {
    public static final ExtendedMenuType<NpcInventoryMenu, NpcMenuData> INVENTORY = Registry.register(
            BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(GoodcraftMod.MOD_ID, "npc_inventory"),
            new ExtendedMenuType<>(NpcInventoryMenu::new, NpcMenuData.CODEC));

    private NpcMenus() {}
    public static void initialize() {}
    public static boolean openEquipment(ServerPlayer player, GoodcraftNpc npc) { return open(player, npc, NpcMenuView.EQUIPMENT); }
    public static boolean openStorage(ServerPlayer player, GoodcraftNpc npc) { return open(player, npc, NpcMenuView.STORAGE); }

    public static boolean open(ServerPlayer player, GoodcraftNpc npc, NpcMenuView view) {
        if (!npc.canPlayerUse(player) || (view == NpcMenuView.EQUIPMENT ? !npc.supportsEquipment() : !npc.supportsStorage())) return false;
        NpcMenuData data = new NpcMenuData(npc.getId(), npc.getUUID(), view, npc.supportsFishingRod());
        return player.openMenu(new ExtendedMenuProvider<NpcMenuData>() {
            @Override public NpcMenuData getScreenOpeningData(ServerPlayer viewer) { return data; }
            @Override public Component getDisplayName() { return npc.getDisplayName(); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player viewer) {
                return npc.canPlayerUse(viewer) ? new NpcInventoryMenu(id, inventory, data, npc) : null;
            }
        }).isPresent();
    }
}
