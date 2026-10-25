package com.arn.goodmod.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcEquipmentRuleTest {
    @BeforeAll static void bootstrap() { com.arn.goodmod.MinecraftTestBootstrap.initialize(); }

    @Test void bowAndFishingRodStayInTheirOwnSlots() {
        assertTrue(NpcEquipmentRule.BOW.accepts(new ItemStack(Items.BOW)));
        assertFalse(NpcEquipmentRule.BOW.accepts(new ItemStack(Items.CROSSBOW)));
        assertFalse(NpcEquipmentRule.BOW.accepts(new ItemStack(Items.FISHING_ROD)));
        assertTrue(NpcEquipmentRule.ROD.accepts(new ItemStack(Items.FISHING_ROD)));
        assertFalse(NpcEquipmentRule.ROD.accepts(new ItemStack(Items.BOW)));
    }

    @Test void armorMustMatchTheBodySlotAndBeActualArmor() {
        assertTrue(NpcEquipmentRule.HELMET.accepts(new ItemStack(Items.DIAMOND_HELMET)));
        assertTrue(NpcEquipmentRule.CHEST.accepts(new ItemStack(Items.IRON_CHESTPLATE)));
        assertTrue(NpcEquipmentRule.LEGS.accepts(new ItemStack(Items.LEATHER_LEGGINGS)));
        assertTrue(NpcEquipmentRule.FEET.accepts(new ItemStack(Items.GOLDEN_BOOTS)));
        assertFalse(NpcEquipmentRule.HELMET.accepts(new ItemStack(Items.IRON_CHESTPLATE)));
        assertFalse(NpcEquipmentRule.HELMET.accepts(new ItemStack(Items.CARVED_PUMPKIN)));
        assertFalse(NpcEquipmentRule.CHEST.accepts(new ItemStack(Items.ELYTRA)));
        assertFalse(NpcEquipmentRule.FEET.accepts(ItemStack.EMPTY));
    }

    @Test void invalidIndicesAreRejected() {
        assertEquals(NpcEquipmentRule.ROD, NpcEquipmentRule.at(6));
        assertThrows(IllegalArgumentException.class, () -> NpcEquipmentRule.at(-1));
        assertThrows(IllegalArgumentException.class, () -> NpcEquipmentRule.at(7));
    }
    @Test void weaponCategoriesUseRealVanillaTagsAndExcludeHoes() {
        assertTrue(NpcEquipmentRule.WEAPON.accepts(new ItemStack(Items.DIAMOND_SWORD)));
        assertTrue(NpcEquipmentRule.WEAPON.accepts(new ItemStack(Items.IRON_AXE)));
        assertTrue(NpcEquipmentRule.WEAPON.accepts(new ItemStack(Items.IRON_PICKAXE)));
        assertTrue(NpcEquipmentRule.WEAPON.accepts(new ItemStack(Items.IRON_SHOVEL)));
        assertFalse(NpcEquipmentRule.WEAPON.accepts(new ItemStack(Items.IRON_HOE)));
    }

    @Test void vanillaShiftTransferRespectsEquipmentRulesAndSingleItemLimit() {
        SimpleContainer equipment = new SimpleContainer(7);
        TransferProbe menu = new TransferProbe(equipment);
        ItemStack bows = new ItemStack(Items.BOW, 2);
        assertTrue(menu.transferIntoEquipment(bows));
        assertEquals(1, equipment.getItem(NpcEquipmentRule.BOW.index()).getCount());
        assertEquals(1, bows.getCount());
        assertFalse(menu.transferIntoEquipment(bows));
        ItemStack stone = new ItemStack(Items.STONE, 64);
        assertFalse(menu.transferIntoEquipment(stone));
        assertEquals(64, stone.getCount());
    }

    private static class TransferProbe extends AbstractContainerMenu {
        TransferProbe(SimpleContainer inventory) {
            super(null, 1);
            for (NpcEquipmentRule rule : NpcEquipmentRule.values()) addSlot(new NpcEquipmentSlot(inventory, rule, 0, 0));
        }
        boolean transferIntoEquipment(ItemStack stack) { return moveItemStackTo(stack, 0, 7, false); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }
}
