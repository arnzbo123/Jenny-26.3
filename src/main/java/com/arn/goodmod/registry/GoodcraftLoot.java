package com.arn.goodmod.registry;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;

/** ap's weight-five entry in legacy pool3, falling back to pool2. No extra independent roll. */
public final class GoodcraftLoot {
    private static final Set<String> TABLES = Set.of("chests/simple_dungeon", "chests/abandoned_mineshaft",
            "chests/desert_pyramid", "chests/woodland_mansion");
    private GoodcraftLoot() {}
    public static void initialize() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            Identifier id = key.identifier();
            if (id.getNamespace().equals("minecraft") && TABLES.contains(id.getPath())) addLamp(builder);
        });
    }
    public static void addLamp(LootTable.Builder builder) {
        AtomicInteger count = new AtomicInteger(); builder.modifyPools(pool -> count.incrementAndGet());
        int target = count.get() >= 4 ? 3 : count.get() >= 3 ? 2 : -1;
        AtomicInteger index = new AtomicInteger();
        builder.modifyPools(pool -> {
            if (index.getAndIncrement() == target) pool.add(LootItem.lootTableItem(GoodcraftItems.ALLIES_LAMP).setWeight(5));
        });
    }
}
