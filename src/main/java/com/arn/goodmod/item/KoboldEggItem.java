package com.arn.goodmod.item;

import com.arn.goodmod.registry.GoodcraftEntities;
import com.arn.goodmod.world.KoboldTribes;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public final class KoboldEggItem extends LegacyAnimatedItem {
    public KoboldEggItem(Properties properties) { super(properties, "kobold_egg_item"); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (context.getPlayer() == null || !level.mayInteract(context.getPlayer(), context.getClickedPos())) return InteractionResult.FAIL;
        var egg = GoodcraftEntities.KOBOLD_EGG.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (egg == null) return InteractionResult.FAIL;
        var data = context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String color = data.getStringOr("egg_color", "SILVER");
        if (!KoboldTribes.COLORS.contains(color)) color = "PURPLE";
        egg.setEggColor(color);
        String tribe = data.getStringOr("tribeID", "");
        if (!tribe.isEmpty()) {
            try { egg.setTribeId(UUID.fromString(tribe)); } catch (IllegalArgumentException invalid) { return InteractionResult.FAIL; }
        }
        egg.setPos(context.getClickLocation());
        if (!level.addFreshEntity(egg)) return InteractionResult.FAIL;
        context.getItemInHand().shrink(1); // Original c7 consumes placed eggs in creative too.
        return InteractionResult.SUCCESS;
    }
}
