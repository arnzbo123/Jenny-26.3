package com.arn.goodmod.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Follow speeds are f.b(): 0.5 walk / 0.7 sprint plus 0.2 per five blocks. */
final class NpcMovementGoal extends Goal {
    private final GoodcraftNpc npc;
    NpcMovementGoal(GoodcraftNpc npc) { this.npc = npc; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() { return !npc.scene().walking() && npc.isAnchored() || npc.getAnimationState().equals("DOWNED") || npc.getMovementMode() != MovementMode.WANDER; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void tick() {
        if (npc.isAnchored() || npc.getAnimationState().equals("DOWNED") || npc.getMovementMode() == MovementMode.WAIT) { npc.getNavigation().stop(); return; }
        if (npc.getMovementMode() == MovementMode.HOME) {
            npc.getNavigation().stop(); // GoodcraftNpc advances the original pearl timeline.
            return;
        }
        Player owner = npc.getOwnerPlayer().orElse(null);
        if (owner == null || owner.isSpectator()) { npc.getNavigation().stop(); return; }
        double distance = npc.distanceTo(owner);
        npc.getLookControl().setLookAt(owner, 30, 30);
        if (distance < 2) npc.getNavigation().stop();
        else npc.getNavigation().moveTo(owner, (owner.isSprinting() ? 0.7 : 0.5) + Math.floor(distance / 5) * 0.2);
    }
    @Override public void stop() { npc.getNavigation().stop(); }
}
