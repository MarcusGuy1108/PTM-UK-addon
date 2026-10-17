package com.ptmuk.client.road;

import com.mojang.blaze3d.platform.NativeImage;
import com.ptmuk.PtmUk;
import com.ptmuk.road.WorldTerrain;
import com.ptmuk.road.layout.Terrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * A top-down picture of the loaded world round the player, one pixel per block and shaded like
 * a vanilla map, drawn a few rows per frame so opening the planner doesn't stall. It also keeps
 * the ground height of every column, which the road preview levels against (so the preview can
 * be worked out off the render thread).
 */
final class PlannerMap {
    static final ResourceLocation TEXTURE = PtmUk.id("road_planner/map");

    final int originX, originZ, size;
    private final int[] ground;
    private final NativeImage image;
    private final DynamicTexture texture;
    private final Level level;
    private int row;
    private final int fallback;

    PlannerMap(Level level, int centreX, int centreZ, int radius, int fallback) {
        this.level = level;
        this.size = radius * 2;
        this.originX = centreX - radius;
        this.originZ = centreZ - radius;
        this.fallback = fallback;
        this.ground = new int[size * size];
        java.util.Arrays.fill(ground, Integer.MIN_VALUE);
        this.image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        image.fillRect(0, 0, size, size, 0xFF1A1A1A);
        this.texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
    }

    boolean done() {
        return row >= size;
    }

    /** Draw some more rows; returns true when something changed. */
    boolean step(int rows) {
        if (done()) {
            return false;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = Math.min(size, row + rows);
        for (; row < end; row++) {
            int z = originZ + row;
            int prevTop = Integer.MIN_VALUE;
            for (int i = 0; i < size; i++) {
                int x = originX + i;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockState s = level.getBlockState(p.set(x, top, z));
                int north = level.hasChunk(x >> 4, (z - 1) >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1 : top;
                MapColor colour = s.getMapColor(level, p);
                if (colour == MapColor.NONE && top > level.getMinBuildHeight()) {
                    colour = level.getBlockState(p.set(x, top - 1, z)).getMapColor(level, p);
                }
                MapColor.Brightness b = top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                if (!s.getFluidState().isEmpty()) {
                    b = MapColor.Brightness.NORMAL;
                }
                image.setPixelRGBA(i, row, colour == MapColor.NONE ? 0xFF1A1A1A : colour.calculateRGBColor(b));
                ground[row * size + i] = WorldTerrain.scan(level, x, z, fallback);
                prevTop = top;
            }
        }
        texture.upload();
        return true;
    }

    /** Ground heights as sampled so far (safe to read from another thread). */
    Terrain terrain() {
        int[] g = ground.clone();
        int ox = originX, oz = originZ, n = size, fb = fallback;
        return (x, z) -> {
            int i = x - ox, j = z - oz;
            if (i < 0 || j < 0 || i >= n || j >= n) {
                return fb;
            }
            int v = g[j * n + i];
            return v == Integer.MIN_VALUE ? fb : v;
        };
    }

    int groundAt(int x, int z) {
        int i = x - originX, j = z - originZ;
        if (i < 0 || j < 0 || i >= size || j >= size) {
            return fallback;
        }
        int v = ground[j * size + i];
        return v == Integer.MIN_VALUE ? fallback : v;
    }

    void close() {
        Minecraft.getInstance().getTextureManager().release(TEXTURE);
    }
}
