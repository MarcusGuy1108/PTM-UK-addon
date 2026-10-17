package com.ptmuk.road;

import com.ptmuk.road.layout.Terrain;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The real ground in a level, looking through trees, plants and snow so roads aren't lifted
 * over a forest. Water counts as ground at its surface (a road across it gets built up on a
 * causeway). On the client, columns in unloaded chunks report a fallback height; the server
 * loads the chunk.
 */
public final class WorldTerrain implements Terrain {
    private final Level level;
    private final int fallback;
    private final Map<Long, Integer> cache = new HashMap<>();

    public WorldTerrain(Level level, int fallback) {
        this.level = level;
        this.fallback = fallback;
    }

    @Override
    public int groundTop(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Integer c = cache.get(key);
        if (c != null) {
            return c;
        }
        int top = scan(level, x, z, fallback);
        cache.put(key, top);
        return top;
    }

    public static boolean natural(BlockState s) {
        return s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SNOW)
                || (s.canBeReplaced() && s.getFluidState().isEmpty()) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    public static int scan(Level level, int x, int z, int fallback) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            if (level.isClientSide) {
                return fallback;
            }
            level.getChunk(x >> 4, z >> 4);              // the server can load it: build on the real ground
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int min = level.getMinBuildHeight();
        while (y > min) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.is(BlockTags.SNOW) && !s.canBeReplaced()) {
                break;                                   // snow blocks are ground
            }
            if (!natural(s)) {
                break;
            }
            y--;
        }
        return y + 1;
    }
}
