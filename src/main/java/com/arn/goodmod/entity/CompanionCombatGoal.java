package com.arn.goodmod.entity;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Companion defense and ranged windup from g.java; vanilla applies current enchantment rules. */
final class CompanionCombatGoal extends Goal {
    private final GoodcraftNpc npc;
    private LivingEntity enemy;
    private int cooldown, bowTicks;
    CompanionCombatGoal(GoodcraftNpc npc) { this.npc = npc; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() {
        if (npc.isPlayerForm() || npc.getOwnerUUID().isEmpty() || npc.getAssociatedPlayerUUID().isPresent() || npc.isAnchored() || npc.getAnimationState().equals("DOWNED")) return false;
        var owner = npc.getOwnerPlayer().orElse(null);
        if (owner == null || npc.distanceToSqr(owner) > 225) return false;
        LivingEntity attacker = npc.getLastHurtByMob();
        if (!valid(attacker) && owner.tickCount - owner.getLastHurtMobTimestamp() < 140) attacker = owner.getLastHurtMob();
        boolean closeToOwner = npc.distanceToSqr(owner) <= 25;
        if (!valid(attacker) && closeToOwner) attacker = owner.getLastHurtByMob();
        if (!valid(attacker) && closeToOwner) attacker = npc.level().getEntitiesOfClass(Monster.class,
                new net.minecraft.world.phys.AABB(npc.position().add(-5, -2, -5), npc.position().add(5, 2, 5)),
                entity -> valid(entity) && !(entity instanceof net.minecraft.world.entity.monster.Creeper))
                .stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        enemy = attacker;
        return valid(enemy);
    }
    private boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !(entity instanceof GoodcraftNpc) && npc.distanceToSqr(entity) < 400
                && npc.getOwnerUUID().filter(entity.getUUID()::equals).isEmpty();
    }
    @Override public boolean canContinueToUse() { return valid(enemy) && npc.getOwnerPlayer().filter(owner -> owner.isAlive() && npc.distanceToSqr(owner) < 225).isPresent()
            && npc.getAssociatedPlayerUUID().isEmpty() && !npc.getAnimationState().equals("DOWNED") && !npc.isAnchored(); }
    @Override public void start() { npc.setTarget(enemy); npc.setHeldWeaponMode(1); }
    @Override public void tick() {
        if (!(npc.level() instanceof ServerLevel level) || enemy == null) return;
        npc.getLookControl().setLookAt(enemy, 30, 30);
        if (cooldown > 0) cooldown--;
        double distance = npc.distanceTo(enemy);
        boolean hasBow = npc.getEquipmentInventory().getItem(1).is(Items.BOW);
        if (distance > 6 && hasBow && npc.getSensing().hasLineOfSight(enemy)) {
            npc.setHeldWeaponMode(2);
            npc.getNavigation().stop();
            if (++bowTicks <= 0) return;
            npc.setAnimationState("BOW");
            if (++bowTicks >= 32) {
                Arrow arrow = new Arrow(level, npc, new ItemStack(Items.ARROW), npc.getEquipmentInventory().getItem(1));
                double dx = enemy.getX() - npc.getX(), dz = enemy.getZ() - npc.getZ();
                double dy = enemy.getY(0.333333333333) - arrow.getY() + Math.sqrt(dx * dx + dz * dz) * 0.2;
                arrow.shoot(dx, dy, dz, 1.6F, 2F); arrow.setBaseDamage(4.5); level.addFreshEntity(arrow);
                bowTicks = -20; npc.setAnimationState("NULL");
            }
            return;
        }
        bowTicks = 0;
        npc.setHeldWeaponMode(1);
        if (npc.getAnimationState().equals("BOW")) npc.setAnimationState("NULL");
        if (distance >= 1.9) { npc.getNavigation().moveTo(enemy, distance > 3 ? 0.7 : 0.5); return; }
        npc.getNavigation().stop();
        if (cooldown == 0) {
            npc.setAnimationState("ATTACK");
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, npc.getEquipmentInventory().getItem(0).copy());
            npc.doHurtTarget(level, enemy);
            double weaponSpeed = npc.getAttributeValue(Attributes.ATTACK_SPEED) - npc.getAttributeBaseValue(Attributes.ATTACK_SPEED);
            cooldown = Math.max(1, (int)Math.round(Math.abs(Math.max(weaponSpeed, 0.5)) / 3.373494 * 20));
        }
    }
    @Override public void stop() { enemy = null; bowTicks = 0; npc.setTarget(null); npc.setHeldWeaponMode(0); npc.getNavigation().stop(); if (!npc.getAnimationState().equals("DOWNED")) npc.setAnimationState("NULL"); }
}
