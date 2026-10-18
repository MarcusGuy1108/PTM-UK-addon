package com.ptmuk.town;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * British trees, bushes and hedges built from vanilla logs and leaves, at the scale the rest of the
 * mod uses (about 1.8 blocks to a metre), in four sizes from a young tree to a veteran. The same
 * species, size and seed always give the same tree, so a planted tree can be grown a size bigger:
 * the old one comes out block for block and the bigger one goes in.
 */
public final class TreeShape {
    public enum Category { TREE, BUSH, HEDGE }

    enum Crown { DOME, OVAL, CONE, FLAT, ROUND, BALL }

    public enum Species {
        // heights and crown radii in blocks for the four sizes; where the crown starts, as a fraction of the height
        LONDON_PLANE("London plane", Category.TREE, Blocks.STRIPPED_BIRCH_LOG, Blocks.OAK_LEAVES, Crown.DOME,
                new int[]{8, 16, 28, 40}, new int[]{3, 6, 10, 14}, 0.35, 3),
        OAK("English oak", Category.TREE, Blocks.DARK_OAK_LOG, Blocks.OAK_LEAVES, Crown.DOME,
                new int[]{7, 14, 22, 30}, new int[]{3, 6, 11, 15}, 0.3, 3),
        LIME("Lime", Category.TREE, Blocks.OAK_LOG, Blocks.BIRCH_LEAVES, Crown.OVAL,
                new int[]{8, 16, 27, 36}, new int[]{2, 5, 8, 11}, 0.3, 4),
        BIRCH("Silver birch", Category.TREE, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, Crown.OVAL,
                new int[]{7, 13, 21, 28}, new int[]{2, 3, 5, 7}, 0.4, 5),
        CHERRY("Cherry blossom", Category.TREE, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, Crown.ROUND,
                new int[]{5, 8, 12, 15}, new int[]{2, 4, 6, 8}, 0.4, 4),
        PINE("Scots pine", Category.TREE, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Crown.FLAT,
                new int[]{6, 14, 25, 34}, new int[]{2, 4, 6, 8}, 0.65, 4),
        HOLLY("Holly", Category.TREE, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, Crown.CONE,
                new int[]{4, 7, 11, 15}, new int[]{2, 3, 4, 5}, 0.15, 5),
        LEYLANDII("Leylandii", Category.TREE, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Crown.CONE,
                new int[]{5, 9, 15, 22}, new int[]{1, 2, 3, 4}, 0.08, 5),
        BOX_BALL("Box ball", Category.BUSH, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Crown.BALL,
                new int[]{1, 2, 3, 4}, new int[]{1, 1, 2, 3}, 0, 5),
        AZALEA("Flowering shrub", Category.BUSH, Blocks.OAK_LOG, Blocks.FLOWERING_AZALEA_LEAVES, Crown.BALL,
                new int[]{1, 2, 3, 4}, new int[]{1, 2, 2, 3}, 0, 5),
        LAUREL("Laurel bush", Category.BUSH, Blocks.OAK_LOG, Blocks.JUNGLE_LEAVES, Crown.BALL,
                new int[]{2, 3, 4, 5}, new int[]{1, 2, 3, 4}, 0, 5),
        PRIVET_HEDGE("Privet hedge", Category.HEDGE, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Crown.BALL,
                new int[]{1, 2, 3, 4}, new int[]{1, 1, 1, 1}, 0, 5),
        BEECH_HEDGE("Beech hedge", Category.HEDGE, Blocks.OAK_LOG, Blocks.AZALEA_LEAVES, Crown.BALL,
                new int[]{1, 2, 3, 4}, new int[]{1, 1, 1, 1}, 0, 5),
        CONIFER_HEDGE("Conifer hedge", Category.HEDGE, Blocks.OAK_LOG, Blocks.SPRUCE_LEAVES, Crown.BALL,
                new int[]{2, 3, 4, 6}, new int[]{1, 1, 1, 1}, 0, 5);

        public final String title;
        public final Category category;
        final Block log;
        final Block leaves;
        final Crown crown;
        final int[] height;
        final int[] radius;
        final double start;
        /** The size from which the trunk is two blocks thick. */
        final int thick;

        Species(String title, Category category, Block log, Block leaves, Crown crown, int[] height, int[] radius, double start, int thick) {
            this.title = title;
            this.category = category;
            this.log = log;
            this.leaves = leaves;
            this.crown = crown;
            this.height = height;
            this.radius = radius;
            this.start = start;
            this.thick = thick;
        }

        /** How tall it is at this size (1 to 4), in blocks. */
        public int height(int size) {
            return height[size - 1];
        }

        public int radius(int size) {
            return radius[size - 1];
        }
    }

    public static final String[] SIZES = {"Young", "Semi-mature", "Mature", "Veteran"};
    public static final String[] HEDGE_SIZES = {"Low", "Garden", "Tall", "Screen"};

    private TreeShape() {
    }

    /** The tree, relative to the bottom of its trunk (0, 0, 0): what goes where. */
    public static Map<BlockPos, BlockState> tree(Species s, int size, long seed) {
        RandomSource rand = RandomSource.create(seed);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        BlockState leaf = s.leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        double h = s.height(size) * (0.9 + rand.nextDouble() * 0.2);
        double r = s.radius(size) * (0.9 + rand.nextDouble() * 0.2);
        if (s.crown == Crown.BALL) {
            blob(out, leaf, 0, h / 2.0 - 0.5, 0, r + 0.3, h / 2.0, rand, 0.25);
            return out;
        }
        boolean thick = size >= s.thick;
        double mid = thick ? 0.5 : 0;                                // the middle of a 2 x 2 trunk is between its blocks
        int top = (int) Math.round(h);
        int bottom = (int) Math.round(h * s.start);
        switch (s.crown) {
            case CONE -> {
                for (int y = bottom; y <= top; y++) {
                    double rr = r * (top - y + 1) / (double) (top - bottom + 1);
                    disc(out, leaf, mid, y, mid, rr + 0.35, rand);
                }
            }
            case FLAT -> {
                double ry = Math.max(1.5, r * 0.45);
                blob(out, leaf, mid, top - ry, mid, r, ry, rand, 0.2);
                for (int i = 0; i < size; i++) {
                    double a = rand.nextDouble() * Mth.TWO_PI, d = r * (0.4 + rand.nextDouble() * 0.4);
                    double y = bottom + rand.nextDouble() * (top - ry - bottom);
                    blob(out, leaf, mid + Math.cos(a) * d, y, mid + Math.sin(a) * d, r * 0.4, Math.max(1, ry * 0.6), rand, 0.2);
                }
            }
            default -> {
                double ry = s.crown == Crown.OVAL ? (top - bottom) / 2.0 : Math.min((top - bottom) / 2.0, r * (s.crown == Crown.ROUND ? 0.85 : 0.7));
                double cy = top - ry;
                blob(out, leaf, mid, cy, mid, r, ry, rand, 0.25);
                // lumps round the crown, so it isn't a perfect ball
                for (int i = 0; i < 2 + size * 2; i++) {
                    double a = rand.nextDouble() * Mth.TWO_PI, up = rand.nextDouble() * 1.2 - 0.4;
                    double x = mid + Math.cos(a) * r * 0.75, z = mid + Math.sin(a) * r * 0.75;
                    blob(out, leaf, x, cy + up * ry, z, r * 0.45, ry * 0.45, rand, 0.3);
                }
            }
        }
        // the trunk, up into the crown
        int trunkTop = s.crown == Crown.CONE || s.crown == Crown.FLAT ? top - 1 : (int) (top - Math.max(1, (top - bottom) * 0.35));
        BlockState log = s.log.defaultBlockState();
        for (int y = 0; y <= trunkTop; y++) {
            for (int dx = 0; dx <= (thick ? 1 : 0); dx++) {
                for (int dz = 0; dz <= (thick ? 1 : 0); dz++) {
                    out.put(new BlockPos(dx, y, dz), log);
                }
            }
        }
        // boughs out into the crown on the bigger broadleaves
        if (size >= 2 && (s.crown == Crown.DOME || s.crown == Crown.ROUND || s.crown == Crown.OVAL)) {
            for (int i = 0; i < size + 1; i++) {
                // up and out at about 45 degrees from just under the crown, so they climb into it rather than stick out
                double a = rand.nextDouble() * Mth.TWO_PI, len = r * (0.5 + rand.nextDouble() * 0.25);
                double y0 = bottom - 1 + rand.nextDouble() * Math.max(1, (trunkTop - bottom) * 0.4), rise = len * (0.8 + rand.nextDouble() * 0.4);
                Direction.Axis axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
                for (double t = 1; t <= len; t += 0.5) {
                    BlockPos p = BlockPos.containing(mid + 0.5 + Math.cos(a) * t, y0 + rise * t / len, mid + 0.5 + Math.sin(a) * t);
                    out.put(p, log.setValue(RotatedPillarBlock.AXIS, (int) (t * 2) % 2 == 0 ? axis : Direction.Axis.Y));
                }
            }
        }
        return out;
    }

    /** A rough ellipsoid of leaves, hollow in the middle; `holes` is how many of the outermost leaves are left out. */
    private static void blob(Map<BlockPos, BlockState> out, BlockState leaf, double cx, double cy, double cz, double rx, double ry,
                             RandomSource rand, double holes) {
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        int z0 = (int) Math.floor(cz - rx - 1), z1 = (int) Math.ceil(cz + rx + 1);
        for (int x = x0; x <= x1; x++) {
            for (int y = Math.max(0, y0); y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    double dx = (x + 0.5 - (cx + 0.5)) / rx, dy = (y + 0.5 - (cy + 0.5)) / ry, dz = (z + 0.5 - (cz + 0.5)) / rx;
                    double d = dx * dx + dy * dy + dz * dz;
                    double edge = 1 + (rand.nextDouble() - 0.5) * 0.35;
                    if (d > edge || (d < 0.3 && rx > 3) || (d > 0.75 && rand.nextDouble() < holes)) {
                        continue;
                    }
                    out.putIfAbsent(new BlockPos(x, y, z), leaf);
                }
            }
        }
    }

    /** One layer of a conifer: a ragged disc of leaves. */
    private static void disc(Map<BlockPos, BlockState> out, BlockState leaf, double cx, int y, double cz, double r, RandomSource rand) {
        for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
            for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= r - 0.5 || (d <= r && rand.nextDouble() < 0.6)) {
                    out.putIfAbsent(new BlockPos(x, y, z), leaf);
                }
            }
        }
    }
}
