package com.arn.goodmod.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

public final class TribeWorkPlanner {
    private TribeWorkPlanner() {}
    /** e6: a three-wide, three-high tunnel, thirty blocks deep. */
    public static List<BlockPos> mine(BlockPos origin, Direction direction) {
        if (!direction.getAxis().isHorizontal()) throw new IllegalArgumentException("Mining direction must be horizontal");
        List<BlockPos> blocks = new ArrayList<>(270);
        Direction side = direction.getClockWise();
        for (int depth=0;depth<30;depth++) for (int width=-1;width<=1;width++) for (int height=0;height<3;height++) {
            blocks.add(origin.relative(direction,depth).relative(side,width).above(height));
        }
        return List.copyOf(blocks);
    }
    /** bs: connected logs; bound traversal and never force a chunk load. */
    public static List<BlockPos> tree(ServerLevel level, BlockPos origin) {
        Set<BlockPos> found = new LinkedHashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>(); pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (found.contains(pos) || !level.hasChunkAt(pos) || pos.distSqr(origin)>32*32 || !level.getBlockState(pos).is(BlockTags.LOGS)) continue;
            if (found.size() == 512) throw new IllegalArgumentException("Tree is too large for one job");
            found.add(pos.immutable());
            for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
                if(x!=0||y!=0||z!=0) pending.addLast(pos.offset(x,y,z));
            }
        }
        return List.copyOf(found);
    }
}
