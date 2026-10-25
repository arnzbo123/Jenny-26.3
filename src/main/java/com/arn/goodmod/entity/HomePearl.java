package com.arn.goodmod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

/** ho.java: the pearl can complete early only when landing within five blocks of home. */
final class HomePearl extends ThrownEnderpearl {
    private final GoodcraftNpc npc;
    HomePearl(ServerLevel level, GoodcraftNpc npc) { super(level, npc, new ItemStack(Items.ENDER_PEARL)); this.npc = npc; }
    @Override protected void onHit(HitResult hit) {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 32, 0.5, 1, 0.5, 0.1);
            if (position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(npc.getHomePosition())) < 5) npc.finishHomeTeleport(position());
            discard();
        }
    }
}
