package com.arn.goodmod.entity;

import com.arn.goodmod.world.KoboldTribes;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** ff mine/tree work: ten-tick settle, break at 24/25 ticks, 100/103-tick work cycle. */
final class KoboldWorkGoal extends Goal {
    private final KoboldNpc npc;
    private KoboldTribes.WorkTarget target;
    private UUID tribeId;
    private int settleTicks, workTicks, pathTicks;
    private BlockState replant;
    private BlockPos treeOrigin;
    KoboldWorkGoal(KoboldNpc npc) { this.npc = npc; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level) || npc.isAnchored() || !npc.getAnimationState().equals("NULL") || npc.getTribeId().isEmpty()) return false;
        tribeId = npc.getTribeId().get();
        KoboldTribes tribes = KoboldTribes.get(level);
        var tribe = tribes.find(tribeId).orElse(null);
        if (tribe == null || tribe.following() || !bedsExist(level, tribe)) return false;
        target = tribes.reserveWork(tribeId, npc.getUUID(), npc.blockPosition()).orElse(null);
        if (target != null) {
            treeOrigin = tribe.jobs().stream().filter(job -> job.id().equals(target.jobId())).map(KoboldTribes.WorkJob::origin).findFirst().orElse(null);
            return true;
        }
        return false;
    }
    private boolean bedsExist(ServerLevel level, KoboldTribes.Tribe tribe) {
        return tribe.beds().size() >= tribe.members().size() && tribe.beds().stream().filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(BlockTags.BEDS)).count() >= tribe.members().size();
    }
    @Override public boolean canContinueToUse() {
        if (target == null || !(npc.level() instanceof ServerLevel level) || npc.isAnchored()) return false;
        return KoboldTribes.get(level).find(tribeId).filter(tribe -> !tribe.following() && bedsExist(level, tribe)
                && tribe.jobs().stream().anyMatch(job -> job.id().equals(target.jobId()) && job.blocks().contains(target.block()))).isPresent();
    }
    @Override public void start() { settleTicks = 0; workTicks = 0; pathTicks = 0; }
    @Override public void tick() {
        if (!(npc.level() instanceof ServerLevel level) || target == null || !level.hasChunkAt(target.block())) return;
        KoboldTribes tribes = KoboldTribes.get(level);
        var tribe = tribes.find(tribeId).orElse(null); if (tribe == null) return;
        BlockPos blockPos = target.block(); BlockState block = level.getBlockState(blockPos);
        int breakAt = target.kind() == KoboldTribes.JobKind.TREE ? 25 : 24;
        if (block.isAir() && workTicks >= breakAt) {
            if (++workTicks >= (target.kind() == KoboldTribes.JobKind.TREE ? 103 : 100)) complete(level, tribes);
            return;
        }
        if (block.isAir() || block.getDestroySpeed(level, blockPos) < 0 || level.getBlockEntity(blockPos) != null) { complete(level, tribes); return; }
        if (target.kind() == KoboldTribes.JobKind.TREE && !block.is(BlockTags.LOGS) && !block.is(BlockTags.LEAVES)) { complete(level, tribes); return; }
        List<ItemStack> drops = Block.getDrops(block, level, blockPos, null, npc, new ItemStack(Items.IRON_PICKAXE));
        if (!canFit(drops)) { npc.setAnimationState("NULL"); workTicks = 0; deposit(level, tribe); return; }
        npc.getLookControl().setLookAt(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        if (npc.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5) > 9) {
            npc.getNavigation().moveTo(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, 0.35);
            settleTicks = 0;
            if (++pathTicks >= 200 && npc.getNavigation().isDone()) { tribes.releaseWork(npc.getUUID()); target = null; }
            return;
        }
        npc.getNavigation().stop();
        if (++settleTicks < 10) return;
        if (workTicks == 0) { npc.setAnimationInput("legacy.at", target.kind() == KoboldTribes.JobKind.TREE ? 1 : 0); npc.setAnimationState("MINE"); }
        workTicks++;
        if (workTicks == breakAt) {
            var owner = tribe.owner().map(level::getPlayerByUUID).orElse(null);
            if (owner != null && !owner.mayInteract(level, blockPos)) { tribes.releaseWork(npc.getUUID()); target = null; npc.setAnimationState("NULL"); return; }
            if (target.kind() == KoboldTribes.JobKind.TREE && block.is(BlockTags.LOGS)) replant = sapling(block);
            if (level.destroyBlock(blockPos, false, npc)) for (ItemStack drop : drops) npc.getStorageInventory().addItem(drop);
        }
        if (workTicks >= (target.kind() == KoboldTribes.JobKind.TREE ? 103 : 100)) complete(level, tribes);
    }
    private boolean canFit(List<ItemStack> drops) {
        SimpleContainer trial = new SimpleContainer(27);
        for (int slot = 0; slot < 27; slot++) trial.setItem(slot, npc.getStorageInventory().getItem(slot).copy());
        for (ItemStack drop : drops) if (!trial.addItem(drop.copy()).isEmpty()) return false;
        return true;
    }
    private void deposit(ServerLevel level, KoboldTribes.Tribe tribe) {
        BlockPos chest = tribe.chests().stream().filter(level::hasChunkAt).filter(pos -> level.getBlockEntity(pos) instanceof Container)
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(npc.blockPosition()))).orElse(null);
        if (chest == null) { npc.getNavigation().stop(); return; }
        if (npc.distanceToSqr(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5) > 9) { npc.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 0.35); return; }
        Container container = (Container)level.getBlockEntity(chest);
        for (int source = 0; source < 27; source++) {
            ItemStack stack = npc.getStorageInventory().getItem(source);
            for (int destination = 0; destination < container.getContainerSize() && !stack.isEmpty(); destination++) {
                if (!container.canPlaceItem(destination, stack)) continue;
                ItemStack current = container.getItem(destination);
                if (current.isEmpty()) { int moved = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), container.getMaxStackSize())); container.setItem(destination, stack.split(moved)); }
                else if (ItemStack.isSameItemSameComponents(current, stack)) {
                    int moved = Math.min(stack.getCount(), Math.min(current.getMaxStackSize(), container.getMaxStackSize()) - current.getCount());
                    if (moved > 0) { current.grow(moved); stack.shrink(moved); container.setChanged(); }
                }
            }
        }
        npc.getStorageInventory().setChanged();
    }
    private void complete(ServerLevel level, KoboldTribes tribes) {
        if (target == null) return;
        UUID completedJob = target.jobId();
        tribes.completeWork(tribeId, npc.getUUID(), target.block());
        boolean jobFinished = tribes.find(tribeId).map(t -> t.jobs().stream().noneMatch(j -> j.id().equals(completedJob))).orElse(true);
        if (jobFinished && replant != null && treeOrigin != null && level.isEmptyBlock(treeOrigin) && replant.canSurvive(level, treeOrigin)) level.setBlockAndUpdate(treeOrigin, replant);
        target = null; npc.setAnimationState("NULL");
    }
    private static BlockState sapling(BlockState log) {
        if (log.is(Blocks.SPRUCE_LOG)) return Blocks.SPRUCE_SAPLING.defaultBlockState();
        if (log.is(Blocks.BIRCH_LOG)) return Blocks.BIRCH_SAPLING.defaultBlockState();
        if (log.is(Blocks.JUNGLE_LOG)) return Blocks.JUNGLE_SAPLING.defaultBlockState();
        if (log.is(Blocks.ACACIA_LOG)) return Blocks.ACACIA_SAPLING.defaultBlockState();
        if (log.is(Blocks.DARK_OAK_LOG)) return Blocks.DARK_OAK_SAPLING.defaultBlockState();
        return Blocks.OAK_SAPLING.defaultBlockState();
    }
    @Override public void stop() {
        if (npc.level() instanceof ServerLevel level) KoboldTribes.get(level).releaseWork(npc.getUUID());
        target = null; settleTicks = 0; workTicks = 0; npc.getNavigation().stop();
        if (npc.getAnimationState().equals("MINE")) npc.setAnimationState("NULL");
    }
}
