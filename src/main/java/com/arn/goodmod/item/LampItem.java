package com.arn.goodmod.item;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.registry.GoodcraftEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Legacy ap: three wishes and the original 95-tick summon sequence. */
public final class LampItem extends LegacyAnimatedItem {
    public static final String USES = "sexmodUses";
    private static final String ACTIVE = "sexmodAllieInUse";
    private static final String TICKS = "sexmodAllieInUseTicks";
    private static final String ALLIE_ID = "sexmodAllieID";

    public LampItem(Properties properties) { super(properties, "allies_lamp"); }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag data = data(stack);
        if (data.getIntOr(USES, 0) >= 3 || data.getBooleanOr(ACTIVE, false)) return InteractionResult.FAIL;
        if (world instanceof ServerLevel level) {
            String associated = data.getStringOr(ALLIE_ID, "");
            if (!associated.isEmpty()) {
                try {
                    Entity previous = level.getEntity(java.util.UUID.fromString(associated));
                    if (previous != null && previous.isAlive()) return InteractionResult.FAIL;
                } catch (IllegalArgumentException malformedLegacyId) {
                    return InteractionResult.FAIL;
                }
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putBoolean(ACTIVE, true);
                tag.putInt(TICKS, 0);
            });
            triggerItemAnimation(player, stack, "rub");
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, EquipmentSlot slot) {
        if (!(holder instanceof Player player) || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)) return;
        CompoundTag tag = data(stack);
        if (!tag.getBooleanOr(ACTIVE, false)) return;
        int ticks = tag.getIntOr(TICKS, 0);
        Vec3 point = summonPosition(player);
        if (ticks > 50 && ticks < 95) {
            double progress = (1 - Math.cos(Math.PI * (ticks - 50) / 45.0)) / 2;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y + player.getEyeHeight() * (1 - progress),
                    point.z, (int)(150 * progress), 0.75 * progress, 0.75 * progress, 0.75 * progress, progress);
        }
        if (ticks < 95) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putInt(TICKS, ticks + 1));
            return;
        }
        GoodcraftNpc allie = GoodcraftEntities.create(NpcKind.ALLIE, level);
        allie.setOwnerUUID(player.getUUID());
        allie.setPos(point);
        allie.setYRot(player.getYRot() + 180);
        allie.setHomePosition(allie.blockPosition());
        allie.setTargetYaw(player.getYRot() + 180);
        allie.setAnchored(true);
        allie.setPersistenceRequired();
        allie.noPhysics = true;
        allie.setAnimationState(level.getBlockState(allie.blockPosition().below()).is(Blocks.SAND)
                ? "SUMMON_SAND" : tag.getIntOr(USES, 0) == 0 ? "SUMMON" : "SUMMON_NORMAL");
        if (level.addFreshEntity(allie)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.putInt(USES, data.getIntOr(USES, 0) + 1);
                data.putString(ALLIE_ID, allie.getUUID().toString());
                data.putBoolean(ACTIVE, false);
                data.putInt(TICKS, 0);
            });
            allie.setSummoningStack(stack.copy());
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y, point.z, 150, 0.75, 0.75, 0.75, 2);
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putBoolean(ACTIVE, false));
        }
    }

    public static int remainingWishes(ItemStack stack) { return Math.max(0, 3 - data(stack).getIntOr(USES, 0)); }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static Vec3 summonPosition(Player player) {
        float angle = player.getYRot() * Mth.DEG_TO_RAD;
        return player.position().add(-Mth.sin(angle) * 2, 0, Mth.cos(angle) * 2);
    }
}
