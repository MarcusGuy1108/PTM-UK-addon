package com.ptmuk.road;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Buildings the LIDAR survey sees that the map hasn't got (whole streets of houses are missing
 * from OpenStreetMap in places): each raised shape that's roof-like (flat, or sloping evenly,
 * where trees are rough) and isn't on a road or a mapped building, as an outline in degrees,
 * squared up to its walls (a rectangle, or a stepped one for an L or a T).
 */
final class LidarBuildings {
    /** Standing this high above the ground (metres) is a building's (or a tree's). */
    private static final double RAISED = 2.5;
    /** A roof's survey points lie this close (metres, root mean square) to a plane through each 3 x 3; trees' don't. */
    private static final double ROOF = 0.3, EDGE = 0.6;
    private static final double MIN_AREA = 15, MAX_AREA = 20_000, MIN_WIDTH = 2.5;

    private LidarBuildings() {
    }

    /** Something to keep clear: a polygon or a line (corners in degrees), and how far round it (metres). */
    record Clear(double[] lat, double[] lon, boolean polygon, double reach) {
    }

    /** The unmapped buildings in s..n, w..e: each {lat[], lon[]}, closed (the first corner again at the end). */
    static List<double[][]> find(Lidar lidar, double s, double w, double n, double e, List<Clear> clear) {
        double kx = 111320 * Math.cos(Math.toRadians((s + n) / 2)), kz = 111250;
        int cols = (int) Math.ceil((e - w) * kx), rows = (int) Math.ceil((n - s) * kz);
        if (cols < 3 || rows < 3 || (long) cols * rows > 30_000_000L) {
            return List.of();                               // shortcut: a map bigger than ~30 km2 isn't searched; do it a tile at a time if that's needed
        }
        float[] h = new float[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                h[r * cols + c] = lidar.raw(n - (r + 0.5) / kz, w + (c + 0.5) / kx);
            }
        }
        boolean[] taken = new boolean[cols * rows];
        for (Clear k : clear) {
            mark(taken, cols, rows, k, n, w, kx, kz);
        }
        // roof-like (a seed) and roof edge or ridge (joins the seeds up) points
        byte[] kind = new byte[cols * rows];                 // 2 roof, 1 edge
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < cols - 1; c++) {
                int i = r * cols + c;
                if (taken[i] || !(h[i] >= RAISED)) {
                    continue;
                }
                double rough = rough(h, cols, i);
                kind[i] = (byte) (rough < ROOF ? 2 : rough < EDGE ? 1 : 0);
            }
        }
        List<double[][]> out = new ArrayList<>();
        int[] seen = new int[cols * rows];
        int label = 0;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] != 2 || seen[i] != 0) {
                continue;
            }
            label++;
            List<Integer> px = new ArrayList<>();
            int roof = 0, rim = 0;
            ArrayDeque<Integer> todo = new ArrayDeque<>(List.of(i));
            seen[i] = label;
            while (!todo.isEmpty()) {
                int p = todo.poll();
                px.add(p);
                roof += kind[p] == 2 ? 1 : 0;
                rim += kind[p] == 0 ? 1 : 0;
                int pc = p % cols;
                for (int q : new int[]{pc > 0 ? p - 1 : -1, pc < cols - 1 ? p + 1 : -1, p - cols, p + cols}) {
                    if (q < 0 || q >= kind.length || seen[q] != 0 || kind[p] == 0) {
                        continue;
                    }
                    if (kind[q] != 0) {
                        seen[q] = label;
                        todo.add(q);
                    } else if (!taken[q] && h[q] >= RAISED) {
                        seen[q] = label;                    // its edge (where the 3 x 3 runs off the roof): in, but not grown from
                        todo.add(q);
                    }
                }
            }
            if (px.size() < MIN_AREA || px.size() > MAX_AREA || roof < 0.45 * (px.size() - rim)) {
                continue;                                   // too small, too big, or rough all over: trees
            }
            double[][] shape = square(px, cols);
            if (shape != null) {
                double[] lat = new double[shape[0].length], lon = new double[shape[0].length];
                for (int k = 0; k < lat.length; k++) {
                    lon[k] = w + shape[0][k] / kx;
                    lat[k] = n - shape[1][k] / kz;
                }
                out.add(new double[][]{lat, lon});
            }
        }
        return out;
    }

    /** How far (metres, root mean square) the 3 x 3 round i lies from the plane that fits it best; big where any is missing. */
    static double rough(float[] h, int cols, int i) {
        double sum = 0, sx = 0, sz = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                float v = h[i + dz * cols + dx];
                if (Float.isNaN(v)) {
                    return Double.MAX_VALUE;
                }
                sum += v;
                sx += dx * v;
                sz += dz * v;
            }
        }
        double mean = sum / 9, a = sx / 6, b = sz / 6, err = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                double d = h[i + dz * cols + dx] - (mean + a * dx + b * dz);
                err += d * d;
            }
        }
        return Math.sqrt(err / 9);
    }

    /** Marks what's kept clear (metres from the corner n, w). */
    private static void mark(boolean[] taken, int cols, int rows, Clear k, double n, double w, double kx, double kz) {
        int m = k.lat().length;
        double[] x = new double[m], z = new double[m];
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            x[i] = (k.lon()[i] - w) * kx;
            z[i] = (n - k.lat()[i]) * kz;
            x0 = Math.min(x0, x[i]);
            z0 = Math.min(z0, z[i]);
            x1 = Math.max(x1, x[i]);
            z1 = Math.max(z1, z[i]);
        }
        double reach = k.reach();
        for (int r = Math.max(0, (int) Math.floor(z0 - reach)); r <= Math.min(rows - 1, (int) Math.ceil(z1 + reach)); r++) {
            for (int c = Math.max(0, (int) Math.floor(x0 - reach)); c <= Math.min(cols - 1, (int) Math.ceil(x1 + reach)); c++) {
                double px = c + 0.5, pz = r + 0.5;
                if (k.polygon() && inside(x, z, px, pz) || near(x, z, px, pz, reach)) {
                    taken[r * cols + c] = true;
                }
            }
        }
    }

    private static boolean inside(double[] x, double[] z, double px, double pz) {
        boolean in = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            if ((z[i] > pz) != (z[j] > pz) && px < (x[j] - x[i]) * (pz - z[i]) / (z[j] - z[i]) + x[i]) {
                in = !in;
            }
        }
        return in;
    }

    private static boolean near(double[] x, double[] z, double px, double pz, double reach) {
        for (int i = 0; i + 1 < x.length; i++) {
            double dx = x[i + 1] - x[i], dz = z[i + 1] - z[i], l2 = dx * dx + dz * dz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - x[i]) * dx + (pz - z[i]) * dz) / l2));
            if (Math.hypot(x[i] + dx * t - px, z[i] + dz * t - pz) <= reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * The shape's walls (metres east and south of the corner): turned to whichever way squares it up
     * tightest, a rectangle round nearly all of it, or (an L, a T) a run of rectangles a metre deep
     * along it, the same width while it is; null if it's thinner than a building.
     */
    static double[][] square(List<Integer> px, int cols) {
        int m = px.size();
        double[] x = new double[m], z = new double[m];
        for (int i = 0; i < m; i++) {
            x[i] = px.get(i) % cols + 0.5;
            z[i] = px.get(i) / cols + 0.5;
        }
        double bestArea = Double.MAX_VALUE, bestA = 0;
        double[] u = new double[m], v = new double[m];
        for (int deg = 0; deg < 90; deg += 3) {
            double a = Math.toRadians(deg), ca = Math.cos(a), sa = Math.sin(a);
            for (int i = 0; i < m; i++) {
                u[i] = x[i] * ca + z[i] * sa;
                v[i] = -x[i] * sa + z[i] * ca;
            }
            double area = (spread(u, 0.97) - spread(u, 0.03) + 1) * (spread(v, 0.97) - spread(v, 0.03) + 1);
            if (area < bestArea) {
                bestArea = area;
                bestA = a;
            }
        }
        double ca = Math.cos(bestA), sa = Math.sin(bestA);
        for (int i = 0; i < m; i++) {
            u[i] = x[i] * ca + z[i] * sa;
            v[i] = -x[i] * sa + z[i] * ca;
        }
        double u0 = spread(u, 0.02) - 0.5, u1 = spread(u, 0.98) + 0.5, v0 = spread(v, 0.02) - 0.5, v1 = spread(v, 0.98) + 0.5;
        if (u1 - u0 < MIN_WIDTH || v1 - v0 < MIN_WIDTH) {
            return null;
        }
        List<double[]> corners = new ArrayList<>();          // (u, v)
        if (m >= 0.65 * (u1 - u0) * (v1 - v0)) {
            corners.addAll(List.of(new double[]{u0, v0}, new double[]{u1, v0}, new double[]{u1, v1}, new double[]{u0, v1}));
        } else {
            // strips a metre deep along its longer side, each as wide as the shape is there; strips alike merged
            boolean alongU = u1 - u0 >= v1 - v0;
            double s0 = alongU ? u0 : v0, s1 = alongU ? u1 : v1;
            int strips = (int) Math.ceil(s1 - s0);
            double[] lo = new double[strips], hi = new double[strips];
            Arrays.fill(lo, Double.MAX_VALUE);
            Arrays.fill(hi, -Double.MAX_VALUE);
            for (int i = 0; i < m; i++) {
                double along = alongU ? u[i] : v[i], across = alongU ? v[i] : u[i];
                int k = Math.max(0, Math.min(strips - 1, (int) Math.floor(along - s0)));
                lo[k] = Math.min(lo[k], across - 0.5);
                hi[k] = Math.max(hi[k], across + 0.5);
            }
            List<double[]> runs = new ArrayList<>();         // from, to (along), lo, hi (across)
            for (int k = 0; k < strips; k++) {
                if (lo[k] > hi[k]) {
                    continue;                               // (a gap: the strip before carries on over it)
                }
                double[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
                if (last != null && Math.abs(last[2] - lo[k]) < 1.5 && Math.abs(last[3] - hi[k]) < 1.5) {
                    last[1] = s0 + k + 1;
                    last[2] = Math.min(last[2], lo[k]);
                    last[3] = Math.max(last[3], hi[k]);
                } else {
                    if (last != null) {
                        last[1] = s0 + k;
                    }
                    runs.add(new double[]{s0 + k, s0 + k + 1, lo[k], hi[k]});
                }
            }
            runs.removeIf(r -> r[1] - r[0] < 1.5 && runs.size() > 1);   // a strip on its own is a ragged end
            if (runs.isEmpty()) {
                return null;
            }
            List<double[]> up = new ArrayList<>(), down = new ArrayList<>();
            for (double[] r : runs) {
                up.add(new double[]{r[0], r[3]});
                up.add(new double[]{r[1], r[3]});
                down.add(0, new double[]{r[0], r[2]});
                down.add(0, new double[]{r[1], r[2]});
            }
            for (double[] p : up) {
                corners.add(alongU ? p : new double[]{p[1], p[0]});
            }
            for (double[] p : down) {
                corners.add(alongU ? p : new double[]{p[1], p[0]});
            }
        }
        double[][] out = new double[2][corners.size() + 1];
        for (int i = 0; i <= corners.size(); i++) {
            double[] p = corners.get(i % corners.size());
            out[0][i] = p[0] * ca - p[1] * sa;
            out[1][i] = p[0] * sa + p[1] * ca;
        }
        return out;
    }

    /** The value a fraction q of the way up v (sorted copy). */
    private static double spread(double[] v, double q) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s[(int) Math.min(s.length - 1, Math.max(0, Math.round(q * (s.length - 1))))];
    }
}
