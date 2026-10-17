package com.ptmuk.road.layout;

/**
 * A cubic Bezier road centre line, resampled at even steps along its length so lateral offsets
 * and dash patterns can be measured in blocks.
 */
public final class Curve {
    public static final double STEP = 0.25;

    public final double[] x, z, tx, tz;
    public final int n;
    public final double length;

    private Curve(double[] x, double[] z, double[] tx, double[] tz, double length) {
        this.x = x;
        this.z = z;
        this.tx = tx;
        this.tz = tz;
        this.n = x.length;
        this.length = length;
    }

    public static Curve bezier(double x0, double z0, double x1, double z1, double x2, double z2, double x3, double z3) {
        double chord = Math.hypot(x3 - x0, z3 - z0);
        int fine = Math.max(64, (int) Math.ceil(chord * 12));
        double[] px = new double[fine + 1], pz = new double[fine + 1], len = new double[fine + 1];
        for (int i = 0; i <= fine; i++) {
            double t = (double) i / fine, u = 1 - t;
            double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
            px[i] = a * x0 + b * x1 + c * x2 + d * x3;
            pz[i] = a * z0 + b * z1 + c * z2 + d * z3;
            if (i > 0) {
                len[i] = len[i - 1] + Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
            }
        }
        double total = len[fine];
        int count = Math.max(2, (int) Math.ceil(total / STEP) + 1);
        double[] x = new double[count], z = new double[count], tx = new double[count], tz = new double[count];
        int j = 0;
        for (int k = 0; k < count; k++) {
            double s = Math.min(total, k * STEP);
            while (j < fine - 1 && len[j + 1] < s) {
                j++;
            }
            double seg = len[j + 1] - len[j];
            double f = seg <= 1e-9 ? 0 : (s - len[j]) / seg;
            x[k] = px[j] + (px[j + 1] - px[j]) * f;
            z[k] = pz[j] + (pz[j + 1] - pz[j]) * f;
            double t = (j + f) / fine, u = 1 - t;
            double dx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2);
            double dz = 3 * u * u * (z1 - z0) + 6 * u * t * (z2 - z1) + 3 * t * t * (z3 - z2);
            double m = Math.hypot(dx, dz);
            if (m < 1e-9) {
                dx = x3 - x0;
                dz = z3 - z0;
                m = Math.max(1e-9, Math.hypot(dx, dz));
            }
            tx[k] = dx / m;
            tz[k] = dz / m;
        }
        return new Curve(x, z, tx, tz, total);
    }

    /** Sample index nearest to a distance along the curve. */
    public int index(double s) {
        return Math.max(0, Math.min(n - 1, (int) Math.round(s / STEP)));
    }

    public double s(int i) {
        return Math.min(length, i * STEP);
    }

    /** Left of the direction of travel, seen from above with north up (x east, z south). */
    public double leftX(int i) {
        return tz[i];
    }

    public double leftZ(int i) {
        return -tx[i];
    }
}
