package com.arn.goodmod.menu;
import com.arn.goodmod.entity.GoodcraftNpc;
import net.minecraft.world.entity.player.Inventory;
public final class WorldMenuFixture {
    private WorldMenuFixture() {}
    public static NpcInventoryMenu create(int id, Inventory inventory, NpcMenuData data, GoodcraftNpc npc) {
        return new NpcInventoryMenu(id, inventory, data, npc);
    }
}
