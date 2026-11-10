package com.ptmuk.road;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Real heights for a map from the Environment Agency's LIDAR survey of England: the height of the
 * top of everything (the surface model) and of the bare ground (the terrain model), on grids in
 * latitude and longitude, a tile at a time as the WCS service sends them (GeoTIFF). Each map
 * building is measured from them: how tall it really is above the ground under it, and whether
 * its roof is flat or pitched. Plain Java; saved next to the map file, so it's fetched once.
 */
public final class Lidar {
    /** A grid of heights: pixel (c, r) covers from lon0 + c dLon east and lat0 - r dLat south; NaN where there's no survey. */
    public record Grid(double lat0, double lon0, double dLat, double dLon, int w, int h, float[] v) {
        float at(double lat, double lon) {
            int c = (int) Math.floor((lon - lon0) / dLon), r = (int) Math.floor((lat0 - lat) / dLat);
            return c < 0 || r < 0 || c >= w || r >= h ? Float.NaN : v[r * w + c];
        }
    }

    /**
     * What a building measures, in metres: the top of its roof, its eaves (where its walls stop),
     * and its roof: "gabled" (pitched), "flat", or "" for a big building, whose roof is left to
     * what kind of building it is (a big roof's heights are all sorts: plant, lantern lights, wings).
     */
    public record Shape(double top, double eaves, String roof, int samples) {
        public boolean pitched() {
            return roof.equals("gabled");
        }
    }

    /** Heights above the ground, a grid for each tile fetched. */
    public final List<Grid> tiles = new ArrayList<>();

    /** A tile from its surface model (the tops of things) and terrain model (the bare ground). */
    public void add(Grid top, Grid ground) {
        float[] v = new float[top.v.length];
        for (int r = 0; r < top.h; r++) {
            for (int c = 0; c < top.w; c++) {
                v[r * top.w + c] = top.v[r * top.w + c] - ground.at(top.lat0 - (r + 0.5) * top.dLat, top.lon0 + (c + 0.5) * top.dLon);
            }
        }
        tiles.add(new Grid(top.lat0, top.lon0, top.dLat, top.dLon, top.w, top.h, v));
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }

    /**
     * A building measured from its outline (corners in degrees), from the heights of the survey's
     * points inside it above the ground under them. Null with too few points, or nothing standing.
     */
    public Shape shape(double[] lat, double[] lon) {
        float[] all = samples(lat, lon);
        if (all == null) {
            return null;
        }
        int lo = 0;
        while (lo < all.length && all[lo] < 2) {
            lo++;                                           // the ground: a yard, a garden, or the outline a little off
        }
        float[] v = Arrays.copyOfRange(all, lo, all.length);
        if (v.length < 6 || v.length < all.length * 0.3) {
            return null;                                    // nothing much standing there: gone, or the map and survey disagree
        }
        double p10 = v[v.length / 10], p50 = v[v.length / 2], p90 = v[v.length * 9 / 10];
        int level = 0;
        for (float f : v) {
            level += Math.abs(f - p50) <= 0.5 ? 1 : 0;
        }
        Grid g = tiles.get(0);
        double area = v.length * g.dLat * 111250 * g.dLon * 111320 * Math.cos(Math.toRadians(lat[0]));    // square metres of roof
        // a pitched roof's heights run evenly from eaves to ridge; a flat one's are nearly all the same
        String roof = area > 600 ? "" : p90 - p10 > 1.5 && level < v.length * 0.45 && p90 < 25 ? "gabled" : "flat";
        return new Shape(p90, roof.equals("gabled") ? p10 : p50, roof, v.length);
    }

    /** The heights above the ground of the survey's points inside an outline, more than 0.6 m in from its walls, lowest first; null if there are none. */
    public float[] samples(double[] lat, double[] lon) {
        double s = Double.MAX_VALUE, n = -Double.MAX_VALUE, w = Double.MAX_VALUE, e = -Double.MAX_VALUE;
        for (int i = 0; i < lat.length; i++) {
            s = Math.min(s, lat[i]);
            n = Math.max(n, lat[i]);
            w = Math.min(w, lon[i]);
            e = Math.max(e, lon[i]);
        }
        double mLat = 111250, mLon = 111320 * Math.cos(Math.toRadians((s + n) / 2));
        List<Float> rel = new ArrayList<>();
        for (Grid top : tiles) {
            int c0 = Math.max(0, (int) Math.floor((w - top.lon0) / top.dLon)), c1 = Math.min(top.w - 1, (int) Math.floor((e - top.lon0) / top.dLon));
            int r0 = Math.max(0, (int) Math.floor((top.lat0 - n) / top.dLat)), r1 = Math.min(top.h - 1, (int) Math.floor((top.lat0 - s) / top.dLat));
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    double py = top.lat0 - (r + 0.5) * top.dLat, px = top.lon0 + (c + 0.5) * top.dLon;
                    if (!inside(lat, lon, py, px) || edge(lat, lon, py, px, mLat, mLon) < 0.6) {
                        continue;
                    }
                    float above = top.v[r * top.w + c];
                    if (!Float.isNaN(above)) {
                        rel.add(above);
                    }
                }
            }
        }
        if (rel.isEmpty()) {
            return null;
        }
        float[] v = new float[rel.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = rel.get(i);
        }
        Arrays.sort(v);
        return v;
    }

    private static boolean inside(double[] lat, double[] lon, double y, double x) {
        boolean in = false;
        for (int i = 0, j = lat.length - 1; i < lat.length; j = i++) {
            if ((lat[i] > y) != (lat[j] > y) && x < (lon[j] - lon[i]) * (y - lat[i]) / (lat[j] - lat[i]) + lon[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** Metres from (y, x) to the nearest wall of the outline. */
    private static double edge(double[] lat, double[] lon, double y, double x, double mLat, double mLon) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = lat.length - 1; i < lat.length; j = i++) {
            double ax = (lon[j] - x) * mLon, ay = (lat[j] - y) * mLat, bx = (lon[i] - x) * mLon, by = (lat[i] - y) * mLat;
            double ex = bx - ax, ey = by - ay, l2 = ex * ex + ey * ey;
            double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, -(ax * ex + ay * ey) / l2));
            best = Math.min(best, Math.hypot(ax + ex * t, ay + ey * t));
        }
        return best;
    }

    // ------------------------------------------------------------------ the survey's GeoTIFFs

    /**
     * A grid from one of the service's GeoTIFFs: 32-bit float samples, in strips or tiles, either
     * byte order, placed by its model transformation (or pixel scale and tie point), its no-data
     * value made NaN. Null if it's not one of those.
     */
    public static Grid tiff(byte[] d) {
        if (d.length < 16 || !(d[0] == 'M' && d[1] == 'M' || d[0] == 'I' && d[1] == 'I')) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(d).order(d[0] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int ifd = b.getInt(4), count = b.getShort(ifd) & 0xFFFF;
        int w = 0, h = 0, bits = 0, compression = 1, format = 1, tileW = 0, tileH = 0, rowsPerStrip = Integer.MAX_VALUE;
        long[] offsets = null, counts = null;
        double[] transform = null, scale = null, tie = null;
        float nodata = Float.NaN;
        for (int i = 0; i < count; i++) {
            int at = ifd + 2 + 12 * i, tag = b.getShort(at) & 0xFFFF, type = b.getShort(at + 2) & 0xFFFF, n = b.getInt(at + 4);
            int size = switch (type) {
                case 3 -> 2;
                case 4, 11 -> 4;
                case 5, 12, 16 -> 8;
                default -> 1;
            };
            int where = size * n <= 4 ? at + 8 : b.getInt(at + 8);
            switch (tag) {
                case 256 -> w = num(b, type, where, 0);
                case 257 -> h = num(b, type, where, 0);
                case 258 -> bits = num(b, type, where, 0);
                case 259 -> compression = num(b, type, where, 0);
                case 278 -> rowsPerStrip = num(b, type, where, 0);
                case 339 -> format = num(b, type, where, 0);
                case 322 -> tileW = num(b, type, where, 0);
                case 323 -> tileH = num(b, type, where, 0);
                case 273, 324 -> offsets = longs(b, type, where, n);
                case 279, 325 -> counts = longs(b, type, where, n);
                case 34264 -> transform = doubles(b, where, n);
                case 33550 -> scale = doubles(b, where, n);
                case 33922 -> tie = doubles(b, where, n);
                case 42113 -> {
                    try {
                        nodata = Float.parseFloat(new String(d, where, Math.max(0, n - 1), java.nio.charset.StandardCharsets.US_ASCII).trim());
                    } catch (NumberFormatException ignored) {
                        // no usable no-data value
                    }
                }
                default -> {
                }
            }
        }
        if (w <= 0 || h <= 0 || bits != 32 || format != 3 || compression != 1 || offsets == null || (long) w * h > 40_000_000) {
            return null;
        }
        double lon0, lat0, dLon, dLat;
        if (transform != null && transform.length >= 8) {
            dLon = transform[0];
            lon0 = transform[3];
            dLat = -transform[5];
            lat0 = transform[7];
        } else if (scale != null && tie != null && scale.length >= 2 && tie.length >= 6) {
            dLon = scale[0];
            dLat = scale[1];
            lon0 = tie[3] - tie[0] * dLon;
            lat0 = tie[4] + tie[1] * dLat;
        } else {
            return null;
        }
        float[] v = new float[w * h];
        boolean tiled = tileW > 0 && tileH > 0;
        int across = tiled ? (w + tileW - 1) / tileW : 1;
        for (int k = 0; k < offsets.length; k++) {
            int bw = tiled ? tileW : w, bh = tiled ? tileH : Math.min(rowsPerStrip, h);
            int x0 = tiled ? (k % across) * tileW : 0, y0 = tiled ? (k / across) * tileH : k * bh;
            long o = offsets[k];
            for (int r = 0; r < bh; r++) {
                for (int c = 0; c < bw; c++) {
                    long p = o + 4L * (r * bw + c);
                    int x = x0 + c, y = y0 + r;
                    if (x < w && y < h && p + 4 <= d.length) {
                        float f = b.getFloat((int) p);
                        v[y * w + x] = f == nodata || f < -1e30 || Float.isNaN(f) ? Float.NaN : f;
                    }
                }
            }
        }
        return new Grid(lat0, lon0, dLat, dLon, w, h, v);
    }

    private static int num(ByteBuffer b, int type, int where, int i) {
        return type == 3 ? b.getShort(where + 2 * i) & 0xFFFF : b.getInt(where + 4 * i);
    }

    private static long[] longs(ByteBuffer b, int type, int where, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = type == 3 ? b.getShort(where + 2 * i) & 0xFFFF : b.getInt(where + 4 * i) & 0xFFFFFFFFL;
        }
        return out;
    }

    private static double[] doubles(ByteBuffer b, int where, int n) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = b.getDouble(where + 8 * i);
        }
        return out;
    }

    // ------------------------------------------------------------------ saved with the map

    public void write(OutputStream out) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(out))) {
            o.writeInt(0x4C494431);                                      // "LID1"
            o.writeInt(tiles.size());
            for (Grid g : tiles) {
                o.writeDouble(g.lat0);
                o.writeDouble(g.lon0);
                o.writeDouble(g.dLat);
                o.writeDouble(g.dLon);
                o.writeInt(g.w);
                o.writeInt(g.h);
                for (float f : g.v) {
                    o.writeFloat(f);
                }
            }
        }
    }

    public static Lidar read(InputStream in) throws IOException {
        Lidar l = new Lidar();
        try (DataInputStream i = new DataInputStream(new GZIPInputStream(in))) {
            if (i.readInt() != 0x4C494431) {
                throw new IOException("not a heights file");
            }
            int n = i.readInt();
            for (int k = 0; k < n && k < 100_000; k++) {
                double lat0 = i.readDouble(), lon0 = i.readDouble(), dLat = i.readDouble(), dLon = i.readDouble();
                int w = i.readInt(), h = i.readInt();
                if (w <= 0 || h <= 0 || (long) w * h > 40_000_000) {
                    throw new IOException("a damaged heights file");
                }
                float[] v = new float[w * h];
                for (int p = 0; p < v.length; p++) {
                    v[p] = i.readFloat();
                }
                l.tiles.add(new Grid(lat0, lon0, dLat, dLon, w, h, v));
            }
        }
        return l;
    }
}
