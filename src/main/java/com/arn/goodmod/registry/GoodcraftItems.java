package com.arn.goodmod.registry;

import com.arn.goodmod.item.GalathCoinItem;
import com.arn.goodmod.item.KoboldEggItem;
import com.arn.goodmod.item.LampItem;
import com.arn.goodmod.item.LegacyAnimatedItem;
import com.arn.goodmod.item.TribeEggItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** The seven items registered by f9, plus Fabric's dedicated spawn-egg items for the legacy entity eggs. */
public final class GoodcraftItems {
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public static final LampItem ALLIES_LAMP = register("allies_lamp", p -> new LampItem(p.stacksTo(1)));
    public static final LegacyAnimatedItem DRAGON_STAFF = register("dragon_staff", p -> new com.arn.goodmod.item.DragonStaffItem(p.stacksTo(1)));
    public static final TribeEggItem TRIBE_EGG = register("tribe_egg", p -> new TribeEggItem(p.stacksTo(1)));
    public static final GalathCoinItem GALATH_COIN = register("galath_coin", p -> new GalathCoinItem(p.stacksTo(1)));
    public static final Item NPC_EDITOR_WAND = register("npc_editor_wand", p -> new Item(p.stacksTo(1)));
    public static final KoboldEggItem KOBOLD_EGG_ITEM = register("kobold_egg_item", p -> new KoboldEggItem(p.stacksTo(1)));
    public static final FishingRodItem LUNA_ROD = register("luna_rod", p -> new FishingRodItem(p.durability(64)));
    public static final List<Item> SPAWN_EGGS = List.of(
            egg("jenny", GoodcraftEntities.JENNY), egg("ellie", GoodcraftEntities.ELLIE),
            egg("slime", GoodcraftEntities.SLIME), egg("bia", GoodcraftEntities.BIA),
            egg("bee", GoodcraftEntities.BEE), egg("luna", GoodcraftEntities.LUNA),
            egg("kobold", GoodcraftEntities.KOBOLD),
            egg("goblin", GoodcraftEntities.GOBLIN), egg("galath", GoodcraftEntities.GALATH),
            egg("manglelie", GoodcraftEntities.MANGLELIE));
    private GoodcraftItems() {}
    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory) {
        var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("sexmod", name));
        T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
        ITEMS.put(name, item); return item;
    }
    public static Map<String, Item> items() { return Map.copyOf(ITEMS); }
    private static Item egg(String name, net.minecraft.world.entity.EntityType<?> entity) {
        return register(name + "_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(entity)));
    }
    public static void initialize() {
        // One Goodcraft tab holds every mod item, spawn eggs last, instead of scattering them across vanilla tabs.
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("sexmod", "goodcraft"),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.sexmod.goodcraft"))
                        .icon(() -> new ItemStack(SPAWN_EGGS.getFirst()))
                        .displayItems((parameters, output) -> ITEMS.values().forEach(output::accept))
                        .build());
    }
}
