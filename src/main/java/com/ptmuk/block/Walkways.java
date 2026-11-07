package com.ptmuk.block;

import com.rinventor.ptm2.objects.blocks.SidewalkBlock;
import com.rinventor.ptm2.objects.blocks.SidewalkSlab;
import com.rinventor.ptm2.objects.blocks.WalkableFloor;
import com.rinventor.ptm2.objects.blocks.WalkableFloorSlab;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** PTM2 pedestrians spawn only on its walkable blocks with clear space on top. */
public final class Walkways {
    private Walkways() {
    }

    public static boolean walkable(BlockState s) {
        return s.getBlock() instanceof SidewalkBlock || s.getBlock() instanceof SidewalkSlab
                || s.getBlock() instanceof WalkableFloor || s.getBlock() instanceof WalkableFloorSlab;
    }

    /** Call after setting a block at pos without neighbour updates: PTM2 only rechecks the block underneath on a neighbour update. */
    public static void placed(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (walkable(under)) {
            under.neighborChanged(level, below, level.getBlockState(pos).getBlock(), pos, false);
        }
    }

    /** Recheck every walkable block in a pasted box (templates paste in any order). */
    public static void area(Level level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() - 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            placed(level, pos.above());
        }
    }
}
