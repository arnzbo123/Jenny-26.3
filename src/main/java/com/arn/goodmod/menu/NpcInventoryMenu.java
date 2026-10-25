package com.arn.goodmod.menu;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.MovementMode;
import com.arn.goodmod.inventory.NpcEquipmentRule;
import com.arn.goodmod.inventory.NpcEquipmentSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** d4/ca/bx: six equipment slots, Luna's fishing slot, or a 27-slot cargo inventory. */
public final class NpcInventoryMenu extends AbstractContainerMenu {
    private final NpcMenuData opening;
    private final GoodcraftNpc npc;
    private final Container npcInventory;
    private final int npcSlotCount;
    private int movementMode;

    public NpcInventoryMenu(int syncId, Inventory playerInventory, NpcMenuData opening) {
        this(syncId, playerInventory, opening, null);
    }

    NpcInventoryMenu(int syncId, Inventory playerInventory, NpcMenuData opening, GoodcraftNpc npc) {
        super(NpcMenus.INVENTORY, syncId);
        this.opening = opening;
        this.npc = npc;
        this.npcSlotCount = opening.view() == NpcMenuView.STORAGE ? 27 : opening.fishingRod() ? 7 : 6;
        this.npcInventory = npc == null ? new SimpleContainer(opening.view() == NpcMenuView.STORAGE ? 27 : 7)
                : opening.view() == NpcMenuView.STORAGE ? npc.getStorageInventory() : npc.getEquipmentInventory();
        checkContainerSize(npcInventory, npcSlotCount);
        npcInventory.startOpen(playerInventory.player);
        if (opening.view() == NpcMenuView.STORAGE) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(npcInventory, row * 9 + column, 8 + column * 18, 18 + row * 18));
                }
            }
        } else {
            // d4 and ca (Luna, with the rod slot first on screen) place slots over girlinventory.png's two bars.
            int[] x = npcSlotCount == 7 ? new int[]{41, 59, 81, 100, 119, 138, 22} : new int[]{31, 50, 72, 91, 110, 129};
            for (int i = 0; i < npcSlotCount; i++) {
                addSlot(new NpcEquipmentSlot(npcInventory, NpcEquipmentRule.at(i), x[i], 60));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        addDataSlot(new DataSlot() {
            @Override public int get() { return npc == null ? movementMode : npc.getMovementMode().ordinal(); }
            @Override public void set(int value) { movementMode = value; }
        });
    }

    public NpcMenuData opening() { return opening; }
    public int npcSlotCount() { return npcSlotCount; }
    public GoodcraftNpc npc() { return npc; }
    public MovementMode movementMode() {
        int value = npc == null ? movementMode : npc.getMovementMode().ordinal();
        return value >= 0 && value < MovementMode.values().length ? MovementMode.values()[value] : MovementMode.WANDER;
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return npc != null && npc.getUUID().equals(opening.entityUuid()) && npc.canPlayerUse(player);
    }

    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (!stillValid(player)) {
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.closeContainer();
            return;
        }
        super.clicked(slot, button, input, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < npcSlotCount
                ? moveItemStackTo(stack, npcSlotCount, slots.size(), true)
                : moveItemStackTo(stack, 0, npcSlotCount, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        npcInventory.stopOpen(player);
    }
}
