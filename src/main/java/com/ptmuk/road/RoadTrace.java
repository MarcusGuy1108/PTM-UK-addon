package com.ptmuk.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Follow an old road in the world: from a point on it, walk both ways down the middle of its
 * surface (whatever blocks it's made of, under any carpet markings), measuring how wide it is,
 * through junctions (where it suddenly gets much wider) to where it ends, or only so far. The Road
 * Planner turns the line into points and roads.
 */
public final class RoadTrace {
    /** The furthest it follows a road each way, in steps. */
    private static final int MAX_STEPS = 250;
    private static final double STEP = 3;
    /** Wider than this isn't a road (a car park, a square). */
    private static final int MAX_WIDTH = 48;
    /** How far round the clicked point the road's blocks are learnt. */
    private static final int LEARN = 8;
    /** The longest run of other blocks between the road's that is still road (a painted line, a slab row, a drain). */
    private static final int SPECK = 3;

    /**
     * The middle of the road, its width there, which points are junctions, its pavement width, and
     * whether each end is only where following stopped (the road carries on past it).
     */
    public record Result(double[] x, double[] z, int[] width, boolean[] junction, int pavement, boolean openStart, boolean openEnd) {
    }

    private final ServerLevel level;
    /** The blocks the road is made of. */
    private final Set<Block> road = new HashSet<>();
    private final Map<Long, BlockState> blocks = new HashMap<>();
    /** What's on top at a column: open ground only (old roads), or whatever's on top (named roads, bridges too). */
    private final java.util.function.BiFunction<Integer, Integer, BlockState> surface;
    private final int maxSteps;
    /** Whether the last walk stopped for want of steps rather than at the road's end. */
    private boolean stoppedShort;

    private RoadTrace(ServerLevel level, java.util.function.BiFunction<Integer, Integer, BlockState> surface, int maxSteps) {
        this.level = level;
        this.surface = surface;
        this.maxSteps = maxSteps;
    }

    /** The surface at a column (air where there's none). */
    private BlockState at(int bx, int bz) {
        long k = ((long) bx << 32) ^ (bz & 0xffffffffL);
        BlockState s = blocks.get(k);
        if (s == null) {
            level.getChunk(bx >> 4, bz >> 4);                    // load it, so the road can be followed out of sight
            s = surface.apply(bx, bz);
            s = s == null ? Blocks.AIR.defaultBlockState() : s;
            blocks.put(k, s);
        }
        return s;
    }

    private boolean on(double x, double z) {
        return road.contains(at((int) Math.floor(x), (int) Math.floor(z)).getBlock());
    }

    /**
     * What the road through (x, z) is made of: the commonest block in the 3 x 3 round it (so a
     * click on a painted line or a speck of a patchy surface still finds the road), then whatever
     * lies in short runs between the road's blocks, across it or along it: the other blocks of a
     * patchy surface, full-block lines, drains, slab rows on a slope. Grass between isn't road.
     */
    private void learn(int x, int z) {
        Map<Block, Integer> near = new HashMap<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockState s = at(x + dx, z + dz);
                if (!s.isAir()) {
                    near.merge(s.getBlock(), 1, Integer::sum);
                }
            }
        }
        Block seed = at(x, z).getBlock();
        int most = near.getOrDefault(seed, 0);
        for (Map.Entry<Block, Integer> e : near.entrySet()) {
            if (e.getValue() > most) {
                seed = e.getKey();
                most = e.getValue();
            }
        }
        road.add(seed);
        for (int pass = 0; pass < 3; pass++) {
            Map<Block, Integer> between = new HashMap<>();
            for (int a = -LEARN; a <= LEARN; a++) {
                for (int dir = 0; dir < 2; dir++) {
                    List<Block> run = null;                      // other blocks since the road's last (null before its first)
                    for (int b = -LEARN; b <= LEARN; b++) {
                        BlockState s = dir == 0 ? at(x + b, z + a) : at(x + a, z + b);
                        if (road.contains(s.getBlock())) {
                            if (run != null && run.size() <= SPECK) {
                                run.forEach(k -> between.merge(k, 1, Integer::sum));
                            }
                            run = new ArrayList<>();
                        } else if (run != null) {
                            boolean soft = s.isAir() || s.is(BlockTags.DIRT) || s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty();
                            if (soft) {
                                run = null;
                            } else {
                                run.add(s.getBlock());
                            }
                        }
                    }
                }
            }
            int before = road.size();
            between.forEach((k, n) -> {
                if (n >= 2) {
                    road.add(k);
                }
            });
            if (road.size() == before) {
                break;
            }
        }
    }

    /** An old road's surface: the open ground's, or a bottom slab or stair on it (a road up a slope). */
    private static BlockState ground(ServerLevel level, int x, int z) {
        int[] h = BlockSwap.heights(level, x, z);
        if (h == null || h[2] == Integer.MIN_VALUE) {
            return null;
        }
        BlockState on = level.getBlockState(new BlockPos(x, h[2] + 1, z));
        return on.getBlock() instanceof SlabBlock || on.getBlock() instanceof StairBlock ? on : level.getBlockState(new BlockPos(x, h[2], z));
    }

    /** How far the road goes from (x, z) along (nx, nz), in whole blocks. */
    private int reach(double x, double z, double nx, double nz) {
        int k = 0;
        while (k < MAX_WIDTH && on(x + nx * (k + 1), z + nz * (k + 1))) {
            k++;
        }
        return k;
    }

    /** The road through (x, z), or null if it isn't on one: all of it, or (reach > 0) about that many blocks each way. */
    public static Result trace(ServerLevel level, int x, int z, int reach) {
        if (ground(level, x, z) == null) {
            return null;
        }
        int steps = reach > 0 ? Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(reach / STEP))) : MAX_STEPS;
        return new RoadTrace(level, (bx, bz) -> ground(level, bx, bz), steps).run(x, z);
    }

    /** The same along whatever's on top, raised roads and bridges included (for naming roads on the map). */
    public static Result traceTop(ServerLevel level, int x, int z, java.util.function.BiFunction<Integer, Integer, BlockState> top) {
        if (top.apply(x, z) == null) {
            return null;
        }
        return new RoadTrace(level, top, MAX_STEPS).run(x, z);
    }

    private Result run(int bx, int bz) {
        learn(bx, bz);
        double x = bx + 0.5, z = bz + 0.5;
        // across the road is the way it's narrowest; of ways as narrow in whole blocks, the one square to the way it runs furthest
        double best = Double.MAX_VALUE, ax = 1, az = 0;
        int bestAlong = -1;
        for (int i = 0; i < 16; i++) {
            double t = Math.PI * i / 16, nx = Math.cos(t), nz = Math.sin(t);
            int w = reach(x, z, nx, nz) + reach(x, z, -nx, -nz) + 1;
            int along = w <= best ? reach(x, z, -nz, nx) + reach(x, z, nz, -nx) : -1;
            if (w < best || w == best && along > bestAlong) {
                best = w;
                bestAlong = along;
                ax = nx;
                az = nz;
            }
        }
        if (best >= MAX_WIDTH) {
            return null;
        }
        int l = reach(x, z, ax, az), r = reach(x, z, -ax, -az);
        double[] c = middle(x, z, ax, az, l, r);
        double cx = c[0], cz = c[1];
        int w0 = l + r + 1;
        List<double[]> ahead = walk(cx, cz, -az, ax, w0);
        boolean openEnd = stoppedShort;
        List<double[]> behind = walk(cx, cz, az, -ax, w0);
        Collections.reverse(behind);
        List<double[]> all = new ArrayList<>(behind);
        all.add(new double[]{cx, cz, w0, 0});
        all.addAll(ahead);
        int n = all.size();
        double[] xs = new double[n], zs = new double[n];
        int[] ws = new int[n];
        boolean[] js = new boolean[n];
        for (int i = 0; i < n; i++) {
            double[] p = all.get(i);
            xs[i] = p[0];
            zs[i] = p[1];
            ws[i] = (int) p[2];
            js[i] = p[3] != 0;
        }
        return new Result(xs, zs, ws, js, pavement(x, z, ax, az, l, r), stoppedShort, openEnd);
    }

    /** Points down the middle of the road from (x, z), heading (dx, dz): {x, z, width, junction}. */
    private List<double[]> walk(double x, double z, double dx, double dz, int w0) {
        List<double[]> out = new ArrayList<>();
        List<Integer> recent = new ArrayList<>(List.of(w0));
        double px = x, pz = z;
        stoppedShort = true;
        for (int step = 0; step < maxSteps; step++) {
            double nx = -dz, nz = dx;
            double qx = px + dx * STEP, qz = pz + dz * STEP;
            if (!on(qx, qz)) {
                // a bend: find the road again just to one side
                int found = 0;
                for (int k = 1; k <= w0 / 2 + 2 && found == 0; k++) {
                    found = on(qx + nx * k, qz + nz * k) ? k : on(qx - nx * k, qz - nz * k) ? -k : 0;
                }
                if (found == 0 && !bandAcross(qx, qz, dx, dz)) {
                    stoppedShort = false;
                    break;                                       // the road ends
                }
                qx += nx * found;
                qz += nz * found;
            }
            int l = reach(qx, qz, nx, nz), r = reach(qx, qz, -nx, -nz), w = l + r + 1;
            int usual = median(recent);
            if (wide(w, usual)) {
                // a junction: where its widening starts and ends along the way we're going (block by block)
                double start = 0;
                while (start > -STEP && on(qx + dx * (start - 1), qz + dz * (start - 1)) && wide(across(qx + dx * (start - 1), qz + dz * (start - 1), nx, nz), usual)) {
                    start -= 1;
                }
                double last = 0, t = 0;
                boolean through = false;
                double far = Math.max(3.0 * usual, 40);
                while (t < far) {
                    t += 1;
                    double tx = qx + dx * t, tz = qz + dz * t;
                    if (!on(tx, tz)) {
                        break;                                   // it ends in the junction (a T from the side road)
                    }
                    if (wide(across(tx, tz, nx, nz), usual)) {
                        last = t;
                    } else if (t - last >= 2) {
                        through = true;                          // its usual width again: the road carries on
                        break;
                    }
                }
                double mid = (start + last) / 2;
                out.add(new double[]{qx + dx * mid, qz + dz * mid, usual, 1});
                if (!through) {
                    stoppedShort = false;
                    break;
                }
                double tx = qx + dx * t, tz = qz + dz * t;
                int tl = reach(tx, tz, nx, nz), tr = reach(tx, tz, -nx, -nz);
                double[] c = middle(tx, tz, nx, nz, tl, tr);
                px = c[0];
                pz = c[1];
                continue;
            }
            double[] c = middle(qx, qz, nx, nz, l, r);
            double cx = c[0], cz = c[1];
            out.add(new double[]{cx, cz, w, 0});
            recent.add(w);
            if (recent.size() > 5) {
                recent.remove(0);
            }
            // turn with the road, gently
            double mx = cx - px, mz = cz - pz, ml = Math.hypot(mx, mz);
            if (ml > 1e-6) {
                double tx = dx * 0.6 + mx / ml * 0.4, tz = dz * 0.6 + mz / ml * 0.4, tl = Math.hypot(tx, tz);
                dx = tx / tl;
                dz = tz / tl;
            }
            px = cx;
            pz = cz;
        }
        return out;
    }

    /**
     * A short band of something else right across the way at (x, z), with the road going on past
     * it (a row of slabs up a slope, a crossing's stripes): learn it as road. Not grass, and not if
     * the road doesn't carry on.
     */
    private boolean bandAcross(double x, double z, double dx, double dz) {
        int t = 0;
        List<Block> band = new ArrayList<>();
        while (t <= SPECK && !on(x + dx * t, z + dz * t)) {
            BlockState s = at((int) Math.floor(x + dx * t), (int) Math.floor(z + dz * t));
            if (s.isAir() || s.is(BlockTags.DIRT) || s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty()) {
                return false;
            }
            band.add(s.getBlock());
            t++;
        }
        if (t > SPECK || !on(x + dx * (t + 1), z + dz * (t + 1))) {
            return false;
        }
        road.addAll(band);
        return true;
    }

    /** Halfway between the middles of the road's two edge blocks, l and r blocks out from (x, z): on a straight road, the middle of its middle block. */
    private static double[] middle(double x, double z, double nx, double nz, int l, int r) {
        double ax = Math.floor(x + nx * l) + 0.5, az = Math.floor(z + nz * l) + 0.5;
        double bx = Math.floor(x - nx * r) + 0.5, bz = Math.floor(z - nz * r) + 0.5;
        return new double[]{(ax + bx) / 2, (az + bz) / 2};
    }

    private int across(double x, double z, double nx, double nz) {
        return reach(x, z, nx, nz) + reach(x, z, -nx, -nz) + 1;
    }

    /** Much wider than the road usually is: a junction (a lay-by or a bus stop isn't this much wider). */
    private static boolean wide(int w, int usual) {
        return w > Math.max(usual * 1.6, usual + 6);
    }

    private static int median(List<Integer> v) {
        List<Integer> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /** The pavement beside the road: paved open ground (not grass) out from its edges (l and r blocks from (x, z)), the narrower side. */
    private int pavement(double x, double z, double nx, double nz, int l, int r) {
        int a = paved(x + nx * (l + 1), z + nz * (l + 1), nx, nz), b = paved(x - nx * (r + 1), z - nz * (r + 1), -nx, -nz);
        return a == 0 ? b : b == 0 ? a : Math.min(a, b);
    }

    private int paved(double x, double z, double nx, double nz) {
        int k = 0;
        while (k < 10) {
            int bx = (int) Math.floor(x + nx * k), bz = (int) Math.floor(z + nz * k);
            BlockState s = at(bx, bz);
            if (s.isAir() || road.contains(s.getBlock()) || BlockSwap.terrain(s)) {
                break;
            }
            k++;
        }
        return k;
    }
}
