package com.arn.goodmod.registry;

import com.arn.goodmod.MinecraftTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GameplayResourceTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }
    private static RegistryOps<com.google.gson.JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    @ParameterizedTest @ValueSource(strings = {"dragon_staff", "npc_editor_wand", "brewing/potion_horny_potion", "brewing/splash_potion_horny_potion", "brewing/lingering_potion_horny_potion", "brewing/potion_horny_potion_gunpowder", "brewing/splash_potion_horny_potion_dragon_breath"})
    void recipesDecodeUsingReal263RecipeCodec(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/sexmod/recipe/" + name + ".json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertDoesNotThrow(() -> Recipe.DIRECT_CODEC.parse(ops(), json).getOrThrow());
        }
    }
    @ParameterizedTest @ValueSource(ints = {2, 3, 4, 5})
    void lampLootUsesOnlyTheOriginalPoolAndWeight(int count) {
        var pools = new com.google.gson.JsonArray();
        for (int i = 0; i < count; i++) pools.add(JsonParser.parseString("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stick\",\"weight\":10}]}"));
        var json = new com.google.gson.JsonObject(); json.add("pools", pools);
        var table = LootTable.DIRECT_CODEC.parse(ops(), json).getOrThrow();
        var builder = net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder.copyOf(table);
        GoodcraftLoot.addLamp(builder);
        var result = LootTable.DIRECT_CODEC.encodeStart(ops(), builder.build()).getOrThrow().getAsJsonObject().getAsJsonArray("pools");
        assertEquals(count, result.size());
        int target = count >= 4 ? 3 : count >= 3 ? 2 : -1;
        for (int i = 0; i < count; i++) {
            var entries = result.get(i).getAsJsonObject().getAsJsonArray("entries");
            assertEquals(i == target ? 2 : 1, entries.size());
            if (i == target) {
                var lamp = entries.get(1).getAsJsonObject();
                assertEquals("sexmod:allies_lamp", lamp.get("name").getAsString());
                assertEquals(5, lamp.get("weight").getAsInt());
            }
        }
    }
    @Test void registeredItemsHaveUsableDefaultComponentsAndLegacyDurability() {
        assertEquals(17, GoodcraftItems.items().size()); // Seven f9 items, nine legacy spawn eggs and the port's kobold egg.
        GoodcraftItems.items().forEach((name, item) -> assertFalse(item.getDefaultInstance().isEmpty(), name));
        assertEquals(64, GoodcraftItems.LUNA_ROD.getDefaultInstance().getMaxDamage());
        assertEquals(1, GoodcraftItems.ALLIES_LAMP.getDefaultMaxStackSize());
        assertEquals(3600, GoodcraftEffects.POTION.value().getEffects().getFirst().getDuration());
    }
}
