package com.arn.goodmod.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class NpcEquipmentSlot extends Slot {
    private final NpcEquipmentRule rule;

    public NpcEquipmentSlot(Container inventory, NpcEquipmentRule rule, int x, int y) {
        super(inventory, rule.index(), x, y);
        this.rule = rule;
    }

    @Override public boolean mayPlace(ItemStack stack) { return rule.accepts(stack); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public int getMaxStackSize(ItemStack stack) { return 1; }
    public NpcEquipmentRule rule() { return rule; }
}
