package com.arn.goodmod.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/** Original fe slot indices, expressed using the 26.3 item component/tag model. */
public enum NpcEquipmentRule {
    WEAPON(0, EquipmentSlot.MAINHAND), BOW(1, EquipmentSlot.OFFHAND),
    HELMET(2, EquipmentSlot.HEAD), CHEST(3, EquipmentSlot.CHEST),
    LEGS(4, EquipmentSlot.LEGS), FEET(5, EquipmentSlot.FEET), ROD(6, null);

    private final int index;
    private final EquipmentSlot equipmentSlot;

    NpcEquipmentRule(int index, EquipmentSlot equipmentSlot) {
        this.index = index;
        this.equipmentSlot = equipmentSlot;
    }

    public int index() { return index; }
    public EquipmentSlot equipmentSlot() { return equipmentSlot; }

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (this) {
            // ItemSword and ItemTool no longer exist. These tags preserve their original categories.
            case WEAPON -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                    || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS);
            case BOW -> stack.getItem() instanceof BowItem;
            case ROD -> stack.getItem() instanceof FishingRodItem;
            case HELMET, CHEST, LEGS, FEET -> {
                var equippable = stack.get(DataComponents.EQUIPPABLE);
                var attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                // Equippable alone also includes pumpkins/elytra; legacy fe accepted actual armor.
                yield equippable != null && equippable.slot() == equipmentSlot && attributes != null
                        && attributes.modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ARMOR));
            }
        };
    }

    public static NpcEquipmentRule at(int index) {
        if (index < 0 || index >= values().length) throw new IllegalArgumentException("Invalid equipment slot: " + index);
        return values()[index];
    }
}
