package com.ptmuk.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Follow an old road in the world: from a point on it, walk both ways down the middle of its
 * surface (whatever block that is, under any carpet markings), measuring how wide it is, through
 * junctions (where it suddenly gets much wider) to where it ends. The Road Planner turns the line
 * into points and roads.
 */
public final class RoadTrace {
    /** The furthest it follows a road each way, in steps. */
    private static final int MAX_STEPS = 250;
    private static final double STEP = 3;
    /** Wider than this isn't a road (a car park, a square). */
    private static final int MAX_WIDTH = 48;

    /** The middle of the road, its width there, which points are junctions, and its pavement width. */
    public record Result(double[] x, double[] z, int[] width, boolean[] junction, int pavement) {
    }

    private final ServerLevel level;
    private final Block road;
    private final Map<Long, Boolean> mask = new HashMap<>();

    private RoadTrace(ServerLevel level, Block road) {
        this.level = level;
        this.road = road;
    }

    private boolean on(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        return mask.computeIfAbsent(((long) bx << 32) ^ (bz & 0xffffffffL), k -> {
            level.getChunk(bx >> 4, bz >> 4);                    // load it, so the road can be followed out of sight
            BlockState s = BlockSwap.surface(level, bx, bz);
            return s != null && s.getBlock() == road;
        });
    }

    /** How far the road goes from (x, z) along (nx, nz), in whole blocks. */
    private int reach(double x, double z, double nx, double nz) {
        int k = 0;
        while (k < MAX_WIDTH && on(x + nx * (k + 1), z + nz * (k + 1))) {
            k++;
        }
        return k;
    }

    /** The road through (x, z), or null if it isn't on one. */
    public static Result trace(ServerLevel level, int x, int z) {
        BlockState s = BlockSwap.surface(level, x, z);
        if (s == null) {
            return null;
        }
        return new RoadTrace(level, s.getBlock()).run(x + 0.5, z + 0.5);
    }

    private Result run(double x, double z) {
        // across the road is the way it's narrowest
        double best = Double.MAX_VALUE, ax = 1, az = 0;
        for (int i = 0; i < 16; i++) {
            double t = Math.PI * i / 16, nx = Math.cos(t), nz = Math.sin(t);
            int w = reach(x, z, nx, nz) + reach(x, z, -nx, -nz) + 1;
            if (w < best) {
                best = w;
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
        List<double[]> ahead = walk(cx, cz, -az, ax, w0), behind = walk(cx, cz, az, -ax, w0);
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
        return new Result(xs, zs, ws, js, pavement(x, z, ax, az, l, r));
    }

    /** Points down the middle of the road from (x, z), heading (dx, dz): {x, z, width, junction}. */
    private List<double[]> walk(double x, double z, double dx, double dz, int w0) {
        List<double[]> out = new ArrayList<>();
        List<Integer> recent = new ArrayList<>(List.of(w0));
        double px = x, pz = z;
        for (int step = 0; step < MAX_STEPS; step++) {
            double nx = -dz, nz = dx;
            double qx = px + dx * STEP, qz = pz + dz * STEP;
            if (!on(qx, qz)) {
                // a bend: find the road again just to one side
                int found = 0;
                for (int k = 1; k <= w0 / 2 + 2 && found == 0; k++) {
                    found = on(qx + nx * k, qz + nz * k) ? k : on(qx - nx * k, qz - nz * k) ? -k : 0;
                }
                if (found == 0) {
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
            BlockState s = BlockSwap.surface(level, bx, bz);
            if (s == null || s.getBlock() == road || BlockSwap.terrain(s)) {
                break;
            }
            k++;
        }
        return k;
    }
}
