package com.ptmuk.road.layout;

/**
 * Exact Euclidean distance transform on a grid (Felzenszwalb & Huttenlocher): for every cell,
 * the distance to the nearest marked cell and which cell that is. Used to round junction
 * corners (a morphological closing), lay kerbs and pavements a set distance from the road and
 * blend the ground into the edges.
 */
final class Distance {
    private static final double FAR = 1e12;

    final double[] dist;
    final int[] nearest;

    private Distance(int size) {
        dist = new double[size];
        nearest = new int[size];
    }

    static Distance of(boolean[] feature, int w, int h) {
        Distance out = new Distance(w * h);
        int n = Math.max(w, h);
        double[] f = new double[n], d = new double[n];
        int[] arg = new int[n], v = new int[n];
        double[] zs = new double[n + 1];
        double[] rowD = new double[w * h];
        int[] rowArg = new int[w * h];
        for (int z = 0; z < h; z++) {
            int base = z * w;
            for (int x = 0; x < w; x++) {
                f[x] = feature[base + x] ? 0 : FAR;
            }
            pass(f, w, d, arg, v, zs);
            for (int x = 0; x < w; x++) {
                rowD[base + x] = d[x];
                rowArg[base + x] = arg[x];
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                f[z] = rowD[z * w + x];
            }
            pass(f, h, d, arg, v, zs);
            for (int z = 0; z < h; z++) {
                int i = z * w + x;
                if (d[z] >= FAR / 2) {
                    out.dist[i] = Double.POSITIVE_INFINITY;
                    out.nearest[i] = -1;
                } else {
                    out.dist[i] = Math.sqrt(d[z]);
                    int sz = arg[z];
                    out.nearest[i] = sz * w + rowArg[sz * w + x];
                }
            }
        }
        return out;
    }

    /** 1-D squared distance transform of f (lower envelope of parabolas). */
    private static void pass(double[] f, int n, double[] d, int[] arg, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) {
                k++;
            }
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
            arg[q] = v[k];
        }
    }
}
