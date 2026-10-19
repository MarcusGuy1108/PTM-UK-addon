package com.ptmuk.road.layout;

import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadPlan.Junction;
import com.ptmuk.road.plan.RoadPlan.Node;
import com.ptmuk.road.plan.RoadPlan.Segment;
import com.ptmuk.road.plan.RoadPlan.Site;
import com.ptmuk.road.plan.RoadStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out, block by block, what a road plan becomes: carriageway, kerbs, verges and pavements,
 * roundabout islands, the height of every surface (roads levelled with smooth gradients in half
 * block steps, junctions flat), PTM2 road markings with their directions, street lights, and a
 * band round the edge where the ground is cut or filled to meet the road. Plain Java with no game
 * classes, so the planner screen previews exactly what the server will build.
 *
 * <p>Coordinates: x east, z south, as in the world. "Left" of a direction is as a driver facing
 * that way sees it; traffic keeps left, so on a road drawn from a to b the a-to-b lanes are on
 * the left (positive offset) side.
 */
public final class Layout {
    // what each column becomes
    public static final byte NONE = 0, ROAD = 1, KERB = 2, FOOTWAY = 3, VERGE = 4, ISLAND = 5, ISLAND_KERB = 6,
            RESERVATION = 7, BARRIER = 8, PAINT = 9, DOME = 10, BLEND = 11, TACTILE = 12, PLOT = 13;
    // markings (PTM2 blocks); YELLOW is or-ed in for yellow paint
    public static final byte M_NONE = 0, M_LINE = 1, M_DOUBLE = 2, M_HALF = 3, M_GIVE_WAY = 4, M_STOP = 5,
            M_TRIANGLE = 6, M_ZEBRA = 7, M_HATCH = 8, M_ZIGZAG = 9, M_ARROW = 10;
    public static final int YELLOW = 0x40;

    public static final int BLEND_WIDTH = 8;
    public static final double MAX_GRADE = 0.1;
    private static final double SMOOTH = 14;

    public enum FixtureType {
        STREET_LIGHT, SIGNAL,
        // on sites
        TREE, EV_CHARGER, PAY_MACHINE, PARKING_SIGN, TROLLEYS, BIN, BOLLARD, BENCH, DISABLED_BAY, PARENT_BAY, EV_BAY,
        ORDER_POINT, MENU_BOARD, PICNIC_TABLE
    }

    /** Something stood on the surface: x, z, the top of the surface under it and which way it faces (PTM2 rotation, 0 = south). */
    public record Fixture(FixtureType type, int x, int z, float top, int rot8, int level) {
    }

    public final int x0, z0, w, h;
    public final byte[] kind;
    public final float[] top;
    public final byte[] mark;
    public final byte[] markRot;
    /** For BLEND cells: how far they are from the road's footprint. */
    public final float[] blendDist;
    /** Columns that belong to something not built yet. */
    public final boolean[] dirty;
    public final List<Fixture> fixtures = new ArrayList<>();
    /** Effective junction type of each node (null = none). */
    public final Map<Integer, Junction> junctions = new HashMap<>();
    /** A traffic-light junction's controller zone (PTM2 intersection): the node and the area it covers. */
    public record SignalZone(int nodeId, int x0, int z0, int x1, int z1) {
    }

    public final List<SignalZone> signalZones = new ArrayList<>();
    /** Centre lines, for drawing the plan: per segment id. */
    public final Map<Integer, Curve> curves = new HashMap<>();
    /** Each site as laid out (full layouts only). */
    public final List<SiteLayout> sites = new ArrayList<>();

    /** A building on a site: its own frame (front facing out), the top of its floor and its brand. */
    public record SiteBuilding(int siteId, String kind, SiteFrame frame, int floor, int brand) {
    }

    public final List<SiteBuilding> buildings = new ArrayList<>();

    /**
     * A PTM2 traffic point for its AI cars: the middle of a lane at (x, z) standing on the surface
     * at {@code top}, the way the traffic goes (PTM2 rotation) and the points it can go on to.
     */
    public record TrafficPoint(int x, int z, float top, int rot8, List<Integer> next) {
    }

    public final List<TrafficPoint> traffic = new ArrayList<>();

    /** A give way junction or roundabout: the area PTM2 treats as the junction (no cars start off in it). */
    public record JunctionZone(int nodeId, int x0, int z0, int x1, int z1, boolean roundabout) {
    }

    public final List<JunctionZone> junctionZones = new ArrayList<>();

    // ------------------------------------------------------------------ working state

    private final RoadPlan plan;
    private final Terrain terrain;
    private final List<Seg> segs = new ArrayList<>();
    private final List<Jn> jns = new ArrayList<>();
    private final Map<Integer, Jn> jnByNode = new HashMap<>();
    private final Map<Integer, Integer> nodeHeight = new HashMap<>();
    private final Map<Integer, Integer> siteLevel = new HashMap<>();
    private final Map<Integer, RoadStyle> siteStyles = new HashMap<>();
    private final byte[] markPrio;

    private final float[] bestM;
    private final int[] owner;          // segment index, or -(junction index + 1)
    private final int[] ownerI;         // sample index along the owning segment
    private final int[] coverA, coverB; // first two segments whose carriageway covers the cell
    private final boolean[] carriage, island, closed, core;
    private final int[] coreOwner;
    private final int[] resSeg;
    private final float[] resD;
    private final int[] siteOwner;      // index into sites, or -1
    private final int[] blendFrom;      // BLEND cells: the edge cell they slope down (or up) to
    private Distance toRoad, toCore;

    private static final class Seg {
        Segment seg;
        RoadStyle st;
        Curve c;
        Node na, nb;
        double hc, halfRes;
        float[] prof;
        int index;
        /** Sample beyond which markings stop at each end (give way / stop lines), or -1. */
        int cutA = -1, cutB = -1;
        /** Sample where the road meets the junction itself, or -1. */
        int mouthA = -1, mouthB = -1;
        boolean minorA, minorB;
    }

    private static final class Jn {
        Node node;
        Junction type;
        List<Seg> arms = new ArrayList<>();
        double ri, ro, zone;
        int radius;
        RoadStyle widest;
        int index;
        /** A slip road merge: the road it joins (not split by it). */
        Seg host;
    }

    public static Layout of(RoadPlan plan, Terrain terrain) {
        return new Layout(plan, terrain, true);
    }

    /** Just the centre lines and junction types, quick enough to redo while a point is dragged. */
    public static Layout lines(RoadPlan plan) {
        return new Layout(plan, Terrain.flat(64), false);
    }

    private Layout(RoadPlan plan, Terrain terrain, boolean full) {
        this.plan = plan;
        this.terrain = terrain;
        prepare();
        if (!full) {
            segs.clear();
            jns.clear();
        }
        // the grid: everything the roads reach plus the blending margin
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Seg s : segs) {
            double r = s.st.halfOuter() + BLEND_WIDTH + 3;
            for (int i = 0; i < s.c.n; i++) {
                minX = Math.min(minX, s.c.x[i] - r);
                maxX = Math.max(maxX, s.c.x[i] + r);
                minZ = Math.min(minZ, s.c.z[i] - r);
                maxZ = Math.max(maxZ, s.c.z[i] + r);
            }
        }
        for (Jn j : jns) {
            double r = j.ro + j.widest.halfOuter() + BLEND_WIDTH + 3;
            minX = Math.min(minX, j.node.x - r);
            maxX = Math.max(maxX, j.node.x + r);
            minZ = Math.min(minZ, j.node.z - r);
            maxZ = Math.max(maxZ, j.node.z + r);
        }
        boolean anySites = full && !plan.sites.isEmpty();
        if (anySites) {
            for (Site st : plan.sites) {
                int r = BLEND_WIDTH + 3;
                minX = Math.min(minX, st.x0 - r);
                maxX = Math.max(maxX, st.x1 + 1 + r);
                minZ = Math.min(minZ, st.z0 - r);
                maxZ = Math.max(maxZ, st.z1 + 1 + r);
            }
        }
        if (segs.isEmpty() && jns.isEmpty() && !anySites) {
            minX = minZ = 0;
            maxX = maxZ = 1;
        }
        x0 = (int) Math.floor(minX);
        z0 = (int) Math.floor(minZ);
        w = Math.max(1, (int) Math.ceil(maxX) - x0 + 1);
        h = Math.max(1, (int) Math.ceil(maxZ) - z0 + 1);
        int size = w * h;
        kind = new byte[size];
        top = new float[size];
        mark = new byte[size];
        markRot = new byte[size];
        markPrio = new byte[size];
        blendDist = new float[size];
        dirty = new boolean[size];
        bestM = new float[size];
        owner = new int[size];
        ownerI = new int[size];
        coverA = new int[size];
        coverB = new int[size];
        carriage = new boolean[size];
        island = new boolean[size];
        closed = new boolean[size];
        core = new boolean[size];
        coreOwner = new int[size];
        resSeg = new int[size];
        resD = new float[size];
        siteOwner = new int[size];
        blendFrom = new int[size];
        java.util.Arrays.fill(siteOwner, -1);
        java.util.Arrays.fill(blendFrom, -1);
        java.util.Arrays.fill(bestM, Float.MAX_VALUE);
        java.util.Arrays.fill(owner, Integer.MIN_VALUE);
        java.util.Arrays.fill(coverA, -1);
        java.util.Arrays.fill(coverB, -1);
        java.util.Arrays.fill(coreOwner, -1);
        java.util.Arrays.fill(resSeg, -1);
        java.util.Arrays.fill(top, Float.NaN);
        if (segs.isEmpty() && jns.isEmpty() && !anySites) {
            return;
        }
        relaxHeights();
        profiles();
        splat();
        rings();
        closeCorners();
        classify();
        markings();
        lights();
        traffic();
        markDirty();
        for (Jn j : jns) {
            int cx = (int) Math.floor(j.node.x), cz = (int) Math.floor(j.node.z);
            if (j.type == Junction.SIGNALS) {
                int r = (int) Math.ceil(j.zone + j.widest.halfOuter() - j.widest.halfCarriageway() + 2);
                signalZones.add(new SignalZone(j.node.id, cx - r, cz - r, cx + r, cz + r));
            } else if (j.type != Junction.MERGE) {
                int r = (int) Math.ceil(j.zone);
                junctionZones.add(new JunctionZone(j.node.id, cx - r, cz - r, cx + r, cz + r, j.type == Junction.ROUNDABOUT));
            }
        }
    }

    public int index(int x, int z) {
        int i = x - x0, j = z - z0;
        return i < 0 || j < 0 || i >= w || j >= h ? -1 : j * w + i;
    }

    public int cellX(int idx) {
        return x0 + idx % w;
    }

    public int cellZ(int idx) {
        return z0 + idx / w;
    }

    // ------------------------------------------------------------------ the graph

    private void prepare() {
        siteLevels();
        Map<Integer, List<Segment>> at = new HashMap<>();
        for (Segment s : plan.segments) {
            at.computeIfAbsent(s.a, k -> new ArrayList<>()).add(s);
            at.computeIfAbsent(s.b, k -> new ArrayList<>()).add(s);
        }
        for (Node n : plan.nodes) {
            int deg = at.getOrDefault(n.id, List.of()).size();
            Junction t = n.junction == null ? Junction.AUTO : n.junction;
            if (t == Junction.AUTO) {
                t = deg >= 3 ? Junction.GIVE_WAY : null;
            } else if (t != Junction.ROUNDABOUT && t != Junction.MINI_ROUNDABOUT && deg < 3) {
                t = null;
            } else if (deg == 0) {
                t = null;
            }
            junctions.put(n.id, t);
            nodeHeight.put(n.id, nodeHeight(n));
            if (t != null) {
                Jn j = new Jn();
                j.node = n;
                j.type = t;
                j.index = jns.size();
                jns.add(j);
                jnByNode.put(n.id, j);
            }
        }
        for (Segment s : plan.segments) {
            Node a = plan.node(s.a), b = plan.node(s.b);
            if (a == null || b == null || Math.hypot(b.x - a.x, b.z - a.z) < 1) {
                continue;
            }
            Seg g = new Seg();
            g.seg = s;
            g.st = s.style;
            g.na = a;
            g.nb = b;
            g.hc = s.style.halfCarriageway();
            g.halfRes = s.style.dual ? s.style.reservationWidth() / 2.0 : 0;
            g.index = segs.size();
            segs.add(g);
            Jn ja = jnByNode.get(a.id), jb = jnByNode.get(b.id);
            if (ja != null) {
                ja.arms.add(g);
            }
            if (jb != null) {
                jb.arms.add(g);
            }
        }
        for (Jn j : jns) {
            size(j);
        }
        // curves: arms meet a junction straight on, a road carries on smoothly through a plain bend
        for (Seg g : segs) {
            double[] ta = tangent(g, g.na, g.nb), tb = tangent(g, g.nb, g.na);
            double len = Math.hypot(g.nb.x - g.na.x, g.nb.z - g.na.z);
            double cx = (g.nb.x - g.na.x) / len, cz = (g.nb.z - g.na.z) / len;
            double bend = g.seg.bend / 0.75;
            double bx = cz * bend, bz = -cx * bend;          // left of a to b
            double ax = g.na.x + 0.5, az = g.na.z + 0.5, ex = g.nb.x + 0.5, ez = g.nb.z + 0.5;
            g.c = Curve.bezier(ax, az, ax + ta[0] * len / 3 + bx, az + ta[1] * len / 3 + bz,
                    ex + tb[0] * len / 3 + bx, ez + tb[1] * len / 3 + bz, ex, ez);
            curves.put(g.seg.id, g.c);
        }
        merges();
    }

    private void size(Jn j) {
        RoadStyle widest = null;
        double maxHc = 0, maxLane = 4;
        List<Seg> all = new ArrayList<>(j.arms);
        if (j.host != null) {
            all.add(j.host);
        }
        for (Seg s : all) {
            if (widest == null || s.st.halfOuter() > widest.halfOuter()) {
                widest = s.st;
            }
            maxHc = Math.max(maxHc, s.hc);
            maxLane = Math.max(maxLane, s.st.laneWidth);
        }
        j.widest = widest == null ? RoadStyle.mainRoad() : widest;
        j.radius = 0;
        for (Seg s : all) {
            j.radius = Math.max(j.radius, s.st.cornerRadius);
        }
        if (j.radius == 0) {
            j.radius = 6;
        }
        if (j.type == Junction.ROUNDABOUT) {
            double ring = Math.max(7, Math.round(maxLane * 1.5));
            j.ro = j.node.radius > 0 ? j.node.radius : Math.max(12, Math.round(Math.max(6, maxHc) + ring));
            j.ri = Math.max(2, j.ro - ring);
            j.zone = j.ro + j.radius + maxHc;
        } else if (j.type == Junction.MERGE) {
            double slip = j.arms.isEmpty() ? 4 : j.arms.get(0).hc;
            j.zone = slip * 2 + j.radius + 1;
        } else {
            j.zone = maxHc + j.radius + 1;
        }
    }

    /**
     * A road that ends on top of another road (not at one of its points) is a slip road: it
     * merges into or leaves the side of that road without splitting it.
     */
    private void merges() {
        for (Node n : plan.nodes) {
            Junction asked = n.junction == null ? Junction.AUTO : n.junction;
            if (jnByNode.containsKey(n.id) || (asked != Junction.AUTO && asked != Junction.MERGE)) {
                continue;
            }
            Seg own = null;
            int deg = 0;
            for (Seg g : segs) {
                if (g.na.id == n.id || g.nb.id == n.id) {
                    own = g;
                    deg++;
                }
            }
            if (deg != 1) {
                continue;
            }
            double px = n.x + 0.5, pz = n.z + 0.5;
            Seg host = null;
            double best = Double.MAX_VALUE;
            for (Seg g : segs) {
                if (g == own) {
                    continue;
                }
                for (int i = 0; i < g.c.n; i += 2) {
                    double d = Math.hypot(g.c.x[i] - px, g.c.z[i] - pz);
                    if (d < g.hc + 0.5 && d < best) {
                        best = d;
                        host = g;
                    }
                }
            }
            if (host == null) {
                continue;
            }
            Jn j = new Jn();
            j.node = n;
            j.type = Junction.MERGE;
            j.index = jns.size();
            j.arms.add(own);
            j.host = host;
            size(j);
            jns.add(j);
            jnByNode.put(n.id, j);
            junctions.put(n.id, Junction.MERGE);
        }
    }

    /** Direction the road leaves node n towards node o. */
    private double[] tangent(Seg g, Node n, Node o) {
        double cx = o.x - n.x, cz = o.z - n.z, m = Math.hypot(cx, cz);
        cx /= m;
        cz /= m;
        if (junctions.get(n.id) != null) {
            return new double[]{cx, cz};
        }
        List<Segment> here = plan.segmentsAt(n.id);
        if (here.size() != 2) {
            return new double[]{cx, cz};
        }
        Segment other = here.get(0) == g.seg ? here.get(1) : here.get(0);
        Node p = plan.node(other.a == n.id ? other.b : other.a);
        if (p == null) {
            return new double[]{cx, cz};
        }
        double px = n.x - p.x, pz = n.z - p.z, pm = Math.hypot(px, pz);
        if (pm < 1e-6) {
            return new double[]{cx, cz};
        }
        double tx = cx + px / pm, tz = cz + pz / pm, tm = Math.hypot(tx, tz);
        return tm < 1e-6 ? new double[]{cx, cz} : new double[]{tx / tm, tz / tm};
    }

    private int nodeHeight(Node n) {
        if (!Double.isNaN(n.y)) {
            return (int) Math.round(n.y);
        }
        // a road that ends at a site meets it level
        for (Site st : plan.sites) {
            if (n.x >= st.x0 - 2 && n.x <= st.x1 + 2 && n.z >= st.z0 - 2 && n.z <= st.z1 + 2) {
                return siteLevel(st.id);
            }
        }
        int bx = (int) Math.floor(n.x), bz = (int) Math.floor(n.z);
        double sum = 0;
        int k = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                sum += terrain.groundTop(bx + dx, bz + dz);
                k++;
            }
        }
        return (int) Math.round(sum / k);
    }

    /** Outer radius of a roundabout's circulating carriageway (0 if the node isn't one). */
    public double roundaboutRadius(int nodeId) {
        Jn j = jnByNode.get(nodeId);
        return j == null || j.type != Junction.ROUNDABOUT ? 0 : j.ro;
    }

    public int heightOf(int nodeId) {
        return nodeHeight.getOrDefault(nodeId, 64);
    }

    /** The level of a site's tarmac: as set, or the middle height of the ground under it. */
    public int siteLevel(int siteId) {
        return siteLevel.getOrDefault(siteId, 64);
    }

    private void siteLevels() {
        for (Site st : plan.sites) {
            if (!Double.isNaN(st.y)) {
                siteLevel.put(st.id, (int) Math.round(st.y));
                continue;
            }
            List<Integer> hs = new ArrayList<>();
            int step = Math.max(1, Math.max(st.width(), st.depth()) / 12);
            for (int x = st.x0; x <= st.x1; x += step) {
                for (int z = st.z0; z <= st.z1; z += step) {
                    hs.add(terrain.groundTop(x, z));
                }
            }
            Collections.sort(hs);
            siteLevel.put(st.id, hs.isEmpty() ? 64 : hs.get(hs.size() / 2));
        }
    }

    // ------------------------------------------------------------------ heights along each road

    private double flatZone(Node n) {
        Jn j = jnByNode.get(n.id);
        return j == null || j.type == Junction.MERGE ? 0 : j.zone + 2;
    }

    /** How far each end of a road stays level with its junction, shortened on short roads. */
    private double[] zones(Seg g) {
        double za = flatZone(g.na), zb = flatZone(g.nb), room = g.c.length * 0.6;
        if (za + zb > room && za + zb > 0) {
            double k = room / (za + zb);
            za *= k;
            zb *= k;
        }
        return new double[]{za, zb};
    }

    /**
     * Ground-level heights for the points can be too far apart for the road between them to
     * climb at a sensible gradient (a junction on a hillside, say), so pull them together.
     * Heights the plan fixes (set by hand, or kept from an earlier build) don't move.
     */
    private void relaxHeights() {
        Map<Integer, Double> hgt = new HashMap<>();
        for (Node n : plan.nodes) {
            hgt.put(n.id, (double) nodeHeight.getOrDefault(n.id, 64));
        }
        for (int iter = 0; iter < 40; iter++) {
            boolean moved = false;
            for (Seg g : segs) {
                double[] z = zones(g);
                double ramp = Math.max(2, g.c.length - z[0] - z[1]);
                double ha = hgt.get(g.na.id), hb = hgt.get(g.nb.id);
                double excess = Math.abs(hb - ha) - MAX_GRADE * ramp;
                if (excess <= 0.01) {
                    continue;
                }
                boolean fa = !Double.isNaN(g.na.y), fb = !Double.isNaN(g.nb.y);
                double dir = Math.signum(hb - ha);
                if (fa && fb) {
                    continue;
                } else if (fa) {
                    hgt.put(g.nb.id, hb - dir * excess);
                } else if (fb) {
                    hgt.put(g.na.id, ha + dir * excess);
                } else {
                    hgt.put(g.na.id, ha + dir * excess / 2);
                    hgt.put(g.nb.id, hb - dir * excess / 2);
                }
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
        for (Node n : plan.nodes) {
            nodeHeight.put(n.id, (int) Math.round(hgt.get(n.id)));
        }
    }

    private void profiles() {
        List<Seg> later = new ArrayList<>();
        for (Seg g : segs) {
            Jn ja = jnByNode.get(g.na.id), jb = jnByNode.get(g.nb.id);
            if ((ja != null && ja.type == Junction.MERGE) || (jb != null && jb.type == Junction.MERGE)) {
                later.add(g);
            } else {
                profile(g);
            }
        }
        for (Jn j : jns) {
            if (j.type == Junction.MERGE && j.host.prof != null && Double.isNaN(j.node.y)) {
                Curve hc = j.host.c;
                int bestI = 0;
                double best = Double.MAX_VALUE;
                for (int i = 0; i < hc.n; i++) {
                    double d = Math.hypot(hc.x[i] - j.node.x - 0.5, hc.z[i] - j.node.z - 0.5);
                    if (d < best) {
                        best = d;
                        bestI = i;
                    }
                }
                nodeHeight.put(j.node.id, (int) Math.floor(j.host.prof[bestI]));
            }
        }
        for (Seg g : later) {
            profile(g);
        }
    }

    private void profile(Seg g) {
        {
            Curve c = g.c;
            int n = c.n;
            double[] raw = new double[n];
            for (int i = 0; i < n; i++) {
                raw[i] = terrain.groundTop((int) Math.floor(c.x[i]), (int) Math.floor(c.z[i]));
            }
            // moving average
            double[] pre = new double[n + 1];
            for (int i = 0; i < n; i++) {
                pre[i + 1] = pre[i] + raw[i];
            }
            int win = (int) Math.round(SMOOTH / Curve.STEP);
            double[] hgt = new double[n];
            boolean[] pinned = new boolean[n];
            double ha = heightOf(g.na.id), hb = heightOf(g.nb.id);
            double[] zz = zones(g);
            double za = zz[0], zb = zz[1];
            for (int i = 0; i < n; i++) {
                int lo = Math.max(0, i - win), hi = Math.min(n, i + win + 1);
                hgt[i] = (pre[hi] - pre[lo]) / (hi - lo);
                double s = c.s(i);
                if (s <= za || i == 0) {
                    hgt[i] = ha;
                    pinned[i] = true;
                } else if (s >= c.length - zb || i == n - 1) {
                    hgt[i] = hb;
                    pinned[i] = true;
                }
            }
            double g1 = MAX_GRADE * Curve.STEP;
            for (int pass = 0; pass < 4; pass++) {
                for (int i = 1; i < n; i++) {
                    if (!pinned[i]) {
                        hgt[i] = Math.max(hgt[i - 1] - g1, Math.min(hgt[i - 1] + g1, hgt[i]));
                    }
                }
                for (int i = n - 2; i >= 0; i--) {
                    if (!pinned[i]) {
                        hgt[i] = Math.max(hgt[i + 1] - g1, Math.min(hgt[i + 1] + g1, hgt[i]));
                    }
                }
            }
            g.prof = new float[n];
            for (int i = 0; i < n; i++) {
                g.prof[i] = (float) (Math.round(hgt[i] * 2) / 2.0);
            }
        }
    }

    // ------------------------------------------------------------------ the carriageway

    private void splat() {
        for (Seg g : segs) {
            Curve c = g.c;
            Jn ja = jnByNode.get(g.na.id), jb = jnByNode.get(g.nb.id);
            double reach = g.hc;
            for (int i = 0; i < c.n; i++) {
                double lx = c.leftX(i), lz = c.leftZ(i);
                for (double d = -reach + 0.125; d < reach; d += 0.25) {
                    double px = c.x[i] + lx * d, pz = c.z[i] + lz * d;
                    int idx = index((int) Math.floor(px), (int) Math.floor(pz));
                    if (idx < 0) {
                        continue;
                    }
                    if (insideIsland(ja, idx) || insideIsland(jb, idx)) {
                        continue;
                    }
                    double ad = Math.abs(d);
                    if (g.st.dual && ad < g.halfRes) {
                        if (resSeg[idx] < 0 || ad < Math.abs(resD[idx])) {
                            resSeg[idx] = g.index;
                            resD[idx] = (float) d;
                        }
                        continue;
                    }
                    float m = (float) (ad - g.hc);
                    if (m < bestM[idx]) {
                        bestM[idx] = m;
                        owner[idx] = g.index;
                        ownerI[idx] = i;
                    }
                    carriage[idx] = true;
                    if (coverA[idx] < 0) {
                        coverA[idx] = g.index;
                    } else if (coverA[idx] != g.index && coverB[idx] < 0) {
                        coverB[idx] = g.index;
                    }
                }
            }
        }
    }

    private boolean insideIsland(Jn j, int idx) {
        if (j == null || j.type != Junction.ROUNDABOUT) {
            return false;
        }
        double dx = cellX(idx) + 0.5 - (j.node.x + 0.5), dz = cellZ(idx) + 0.5 - (j.node.z + 0.5);
        return Math.hypot(dx, dz) < j.ri + 1;
    }

    private void rings() {
        for (Jn j : jns) {
            if (j.type != Junction.ROUNDABOUT && j.type != Junction.MINI_ROUNDABOUT) {
                continue;
            }
            double cx = j.node.x + 0.5, cz = j.node.z + 0.5;
            int r = (int) Math.ceil(Math.max(j.ro, 2) + 1);
            for (int x = (int) Math.floor(cx) - r; x <= (int) Math.floor(cx) + r; x++) {
                for (int z = (int) Math.floor(cz) - r; z <= (int) Math.floor(cz) + r; z++) {
                    int idx = index(x, z);
                    if (idx < 0) {
                        continue;
                    }
                    double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                    if (j.type == Junction.ROUNDABOUT) {
                        if (d <= j.ri) {
                            island[idx] = true;
                            carriage[idx] = false;
                            coreOwner[idx] = j.index;
                        } else if (d <= j.ro) {
                            carriage[idx] = true;
                            owner[idx] = -(j.index + 1);
                            bestM[idx] = -100;
                        }
                    }
                }
            }
        }
    }

    /** Round the kerbs where roads meet: close the carriageway with a disc of the corner radius. */
    private void closeCorners() {
        for (Jn j : jns) {
            int r = j.radius;
            int reach = (int) Math.ceil(j.zone + r + 3);
            int cx = (int) Math.floor(j.node.x), cz = (int) Math.floor(j.node.z);
            int lx0 = Math.max(0, cx - reach - x0), lz0 = Math.max(0, cz - reach - z0);
            int lx1 = Math.min(w - 1, cx + reach - x0), lz1 = Math.min(h - 1, cz + reach - z0);
            int lw = lx1 - lx0 + 1, lh = lz1 - lz0 + 1;
            if (lw <= 0 || lh <= 0) {
                continue;
            }
            boolean[] f = new boolean[lw * lh];
            for (int z = 0; z < lh; z++) {
                for (int x = 0; x < lw; x++) {
                    f[z * lw + x] = carriage[(lz0 + z) * w + lx0 + x];
                }
            }
            Distance dil = Distance.of(f, lw, lh);
            boolean[] outside = new boolean[lw * lh];
            for (int i = 0; i < outside.length; i++) {
                outside[i] = dil.dist[i] > r;
            }
            Distance ero = Distance.of(outside, lw, lh);
            double ncx = j.node.x + 0.5, ncz = j.node.z + 0.5;
            for (int z = 0; z < lh; z++) {
                for (int x = 0; x < lw; x++) {
                    int idx = (lz0 + z) * w + lx0 + x;
                    double d = Math.hypot(lx0 + x + x0 + 0.5 - ncx, lz0 + z + z0 + 0.5 - ncz);
                    if (d > j.zone || island[idx] || (resSeg[idx] >= 0 && !carriage[idx])) {
                        continue;                        // a reservation only opens where a road crosses it
                    }
                    if (!carriage[idx] && ero.dist[z * lw + x] > r) {
                        carriage[idx] = true;
                        closed[idx] = true;
                        owner[idx] = -(j.index + 1);
                        bestM[idx] = -50;
                    }
                    if (carriage[idx] && (closed[idx] || coverB[idx] >= 0 || owner[idx] < 0)) {
                        core[idx] = true;
                        coreOwner[idx] = j.index;
                    }
                }
            }
            if (j.type == Junction.MINI_ROUNDABOUT) {
                for (int z = 0; z < lh; z++) {
                    for (int x = 0; x < lw; x++) {
                        int idx = (lz0 + z) * w + lx0 + x;
                        double d = Math.hypot(lx0 + x + x0 + 0.5 - ncx, lz0 + z + z0 + 0.5 - ncz);
                        if (d <= 1.6 && carriage[idx]) {
                            core[idx] = true;
                            coreOwner[idx] = j.index;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ kerbs, pavements and heights

    private RoadStyle styleAt(int idx) {
        int o = owner[idx];
        if (o >= 0) {
            return segs.get(o).st;
        }
        if (o != Integer.MIN_VALUE) {
            return jns.get(-o - 1).widest;
        }
        return RoadStyle.mainRoad();
    }

    /** The style of the road a column belongs to (the nearest road, for kerbs and pavements). */
    public RoadStyle styleOf(int idx) {
        if (siteOwner[idx] >= 0) {
            Site st = sites.get(siteOwner[idx]).site;
            return siteStyles.computeIfAbsent(st.id, k -> {
                RoadStyle r = RoadStyle.mainRoad();
                r.footSurface = st.type == RoadPlan.SiteType.CAR_PARK ? "ptmuk:paving_slabs" : "ptmuk:block_paving_grey";
                return r;
            });
        }
        if (carriage[idx]) {
            return styleAt(idx);
        }
        int n = toRoad == null ? -1 : toRoad.nearest[idx];
        return n >= 0 ? styleAt(n) : RoadStyle.mainRoad();
    }

    private float roadHeight(int idx) {
        int o = owner[idx];
        if (o >= 0) {
            Seg g = segs.get(o);
            return g.prof[Math.min(g.prof.length - 1, ownerI[idx])];
        }
        if (o != Integer.MIN_VALUE) {
            return heightOf(jns.get(-o - 1).node.id);
        }
        return 64;
    }

    private void classify() {
        int size = w * h;
        toRoad = Distance.of(carriage, w, h);
        toCore = Distance.of(core, w, h);
        for (int idx = 0; idx < size; idx++) {
            if (carriage[idx]) {
                kind[idx] = ROAD;
                top[idx] = roadHeight(idx);
            }
        }
        // mini roundabouts: a painted white dome in the middle
        for (Jn j : jns) {
            if (j.type != Junction.MINI_ROUNDABOUT) {
                continue;
            }
            int idx = index((int) Math.floor(j.node.x), (int) Math.floor(j.node.z));
            double cx = j.node.x + 0.5, cz = j.node.z + 0.5;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int i = index((int) Math.floor(j.node.x) + dx, (int) Math.floor(j.node.z) + dz);
                    if (i >= 0 && carriage[i] && Math.hypot(cellX(i) + 0.5 - cx, cellZ(i) + 0.5 - cz) <= 1.6) {
                        kind[i] = PAINT;
                    }
                }
            }
            if (idx >= 0 && carriage[idx]) {
                kind[idx] = DOME;
            }
        }
        for (int idx = 0; idx < size; idx++) {
            if (carriage[idx]) {
                continue;
            }
            int near = toRoad.nearest[idx];
            double t = toRoad.dist[idx];
            if (near < 0) {
                continue;
            }
            float base = top[near];
            if (island[idx]) {
                Jn j = jns.get(coreOwner[idx]);
                double d = Math.hypot(cellX(idx) + 0.5 - (j.node.x + 0.5), cellZ(idx) + 0.5 - (j.node.z + 0.5));
                boolean edge = j.ri - d < 1.2;
                kind[idx] = edge ? ISLAND_KERB : ISLAND;
                float hgt = heightOf(j.node.id);
                top[idx] = edge ? hgt + 0.5f : (float) Math.ceil(hgt + 0.5f);
                continue;
            }
            if (resSeg[idx] >= 0) {
                Seg g = segs.get(resSeg[idx]);
                kind[idx] = Math.abs(resD[idx]) < 0.5 && g.st.reservationWidth() >= 3 ? BARRIER : RESERVATION;
                top[idx] = (float) Math.ceil(base);
                continue;
            }
            RoadStyle st = styleAt(near);
            double baseBand = st.kerbs ? 1.5 : 0.5;
            if (st.kerbs && t < 1.5) {
                kind[idx] = KERB;
                top[idx] = base + 0.5f;
            } else if (t < baseBand + st.verge) {
                kind[idx] = VERGE;
                top[idx] = (float) Math.ceil(base + (st.kerbs ? 0.5f : 0f));
            } else if (t < baseBand + st.verge + st.footway()) {
                kind[idx] = FOOTWAY;
                top[idx] = base + (st.kerbs ? 0.5f : 0f);
            }
        }
        applySites();
        // the band round the outside where the ground is cut or built up to meet the road
        boolean[] foot = new boolean[size];
        for (int idx = 0; idx < size; idx++) {
            foot[idx] = kind[idx] != NONE;
        }
        Distance toFoot = Distance.of(foot, w, h);
        for (int idx = 0; idx < size; idx++) {
            if (!foot[idx] && toFoot.dist[idx] <= BLEND_WIDTH && toFoot.nearest[idx] >= 0) {
                kind[idx] = BLEND;
                top[idx] = (float) Math.ceil(top[toFoot.nearest[idx]]);
                blendDist[idx] = (float) toFoot.dist[idx];
                blendFrom[idx] = toFoot.nearest[idx];
            }
        }
    }

    // ------------------------------------------------------------------ markings

    private static int rot8(double tx, double tz) {
        double yaw = Math.toDegrees(Math.atan2(-tx, tz));
        return (((int) Math.round(yaw / 45.0)) % 8 + 8) % 8;
    }

    private void setMark(int idx, int kindAndColour, int rot, int prio) {
        if (idx < 0 || (kind[idx] != ROAD) || prio < markPrio[idx]) {
            return;
        }
        mark[idx] = (byte) kindAndColour;
        markRot[idx] = (byte) rot;
        markPrio[idx] = (byte) prio;
    }

    private record Line(double d, int kind, int on, int off, boolean kerbside, int prio, boolean centre) {
        Line(double d, int kind, int on, int off, boolean kerbside, int prio) {
            this(d, kind, on, off, kerbside, prio, false);
        }
    }

    private List<Line> lines(RoadStyle st, double hc) {
        List<Line> out = new ArrayList<>();
        int lw = st.laneWidth;
        int laneOn = st.laneLinesSolid ? 1 : 3, laneOff = st.laneLinesSolid ? 0 : 5;
        boolean edge = st.edgeLines && st.yellow == RoadStyle.Yellow.NONE;      // yellows take the edge
        if (st.oneWay) {
            double left = -hc;
            for (int k = 1; k < st.lanes; k++) {
                out.add(new Line(left + k * (lw + 1) - 0.5, M_LINE, laneOn, laneOff, false, 2));
            }
        } else {
            double inner = st.dual ? st.reservationWidth() / 2.0 : 0.5;
            if (!st.dual && st.centre != RoadStyle.Centre.NONE) {
                switch (st.centre) {
                    case DASHED -> out.add(new Line(0, M_LINE, 3, 5, false, 3, true));
                    case HAZARD -> out.add(new Line(0, M_LINE, 6, 2, false, 3, true));
                    default -> out.add(new Line(0, M_DOUBLE, 1, 0, false, 3));
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 1; k < st.lanes; k++) {
                    out.add(new Line(side * (inner - 0.5 + k * (lw + 1)), M_LINE, laneOn, laneOff, false, 2));
                }
                if (st.dual && st.edgeLines) {
                    out.add(new Line(side * (inner + 0.5), M_LINE, 1, 0, true, 1));
                }
            }
        }
        if (edge) {
            out.add(new Line(hc - 0.5, M_LINE, 1, 0, true, 1));
            out.add(new Line(-(hc - 0.5), M_LINE, 1, 0, true, 1));
        }
        return out;
    }

    /** Cells along a line at a sideways offset, one block thick and joined corner to corner. */
    private List<int[]> chain(Curve c, double d, int from, int to) {
        List<int[]> cells = new ArrayList<>();
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (int i = from; i <= to; i++) {
            int cx = (int) Math.floor(c.x[i] + c.leftX(i) * d), cz = (int) Math.floor(c.z[i] + c.leftZ(i) * d);
            if (cx == lastX && cz == lastZ) {
                continue;
            }
            if (lastX != Integer.MIN_VALUE && (Math.abs(cx - lastX) > 1 || Math.abs(cz - lastZ) > 1)) {
                int steps = Math.max(Math.abs(cx - lastX), Math.abs(cz - lastZ));
                for (int k = 1; k < steps; k++) {
                    cells.add(new int[]{lastX + Math.round((cx - lastX) * (float) k / steps),
                            lastZ + Math.round((cz - lastZ) * (float) k / steps), i});
                }
            }
            cells.add(new int[]{cx, cz, i});
            lastX = cx;
            lastZ = cz;
        }
        // take out the inside cell of each L so the line steps diagonally
        List<int[]> thin = new ArrayList<>();
        for (int k = 0; k < cells.size(); k++) {
            if (k > 0 && k < cells.size() - 1 && !thin.isEmpty()) {
                int[] p = thin.get(thin.size() - 1), q = cells.get(k + 1);
                if (Math.abs(q[0] - p[0]) == 1 && Math.abs(q[1] - p[1]) == 1) {
                    continue;
                }
            }
            thin.add(cells.get(k));
        }
        return thin;
    }

    private void markings() {
        // which arms give way, and where their lines stop
        for (Jn j : jns) {
            List<Seg> majors = new ArrayList<>();
            if (j.type == Junction.GIVE_WAY || j.type == Junction.STOP) {
                majors = majorPair(j);
            }
            for (Seg g : j.arms) {
                boolean atA = g.na.id == j.node.id;
                boolean minor = !majors.contains(g);
                if (atA) {
                    g.minorA = minor;
                } else {
                    g.minorB = minor;
                }
                if (minor) {
                    mouth(j, g, atA);
                }
            }
        }
        for (Seg g : segs) {
            Curve c = g.c;
            for (Line line : lines(g.st, g.hc)) {
                int from = Math.max(0, g.cutA), to = g.cutB >= 0 ? g.cutB : c.n - 1;
                if (from >= to) {
                    continue;
                }
                for (int[] cell : chain(c, line.d, from, to)) {
                    int idx = index(cell[0], cell[1]);
                    if (idx < 0 || kind[idx] != ROAD) {
                        continue;
                    }
                    int i = cell[2];
                    if (suppressed(g, idx, line)) {
                        continue;
                    }
                    if (line.centre) {
                        if (!centreOn(g, i, line)) {
                            continue;
                        }
                    } else if (line.off > 0 && !dashOn(c.s(i), c.length, line.on, line.off)) {
                        continue;
                    }
                    setMark(idx, line.kind, rot8(c.tx[i], c.tz[i]), line.prio);
                }
            }
        }
        for (Jn j : jns) {
            for (Seg g : j.arms) {
                boolean atA = g.na.id == j.node.id;
                int cut = atA ? g.cutA : g.cutB;
                if (cut >= 0 && j.type != Junction.MERGE) {
                    giveWay(j, g, atA, cut);
                }
            }
        }
        yellows();
    }

    /**
     * The centre line, UK style: ordinary dashes along the road, long "hazard" dashes for the
     * last stretch before a junction, solid up to a stop line at traffic lights.
     */
    private boolean centreOn(Seg g, int i, Line line) {
        Curve c = g.c;
        double s = c.s(i);
        double toJunction = Double.MAX_VALUE;
        boolean signals = false;
        for (int end = 0; end < 2; end++) {
            Node n = end == 0 ? g.na : g.nb;
            Jn j = jnByNode.get(n.id);
            if (j == null || j.type == Junction.MERGE) {
                continue;
            }
            int cut = end == 0 ? g.cutA : g.cutB;
            double edge = cut >= 0 ? c.s(cut) : (end == 0 ? Math.min(c.length, j.zone) : Math.max(0, c.length - j.zone));
            double d = end == 0 ? s - edge : edge - s;
            if (d < toJunction) {
                toJunction = d;
                signals = j.type == Junction.SIGNALS;
            }
        }
        if (toJunction < 0) {
            return (-toJunction % 8) >= 2;               // carried through a junction as a hazard line
        }
        if (signals && toJunction < 6) {
            return true;
        }
        if (toJunction < 30) {
            return (toJunction % 8) >= 2;
        }
        return dashOn(s, c.length, line.on, line.off);
    }

    /**
     * Yellow lines along the edge of the carriageway, following it round corners: single along the
     * road, double (no waiting at any time) near junctions, as each road's style asks. With kerbs
     * they run beside the kerb; without, along the last block of road before the verge, pavement
     * or ground.
     */
    private void yellows() {
        int size = w * h;
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int idx = 0; idx < size; idx++) {
            if (kind[idx] != ROAD || siteOwner[idx] >= 0) {
                continue;                                  // no yellow lines inside car parks
            }
            int x = idx % w, z = idx / w;
            int kerbDir = -1;
            for (int k = 0; k < 4; k++) {
                int nx = x + nb[k][0], nz = z + nb[k][1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                    continue;
                }
                byte nk = kind[nz * w + nx];
                if (nk == ISLAND_KERB || nk == RESERVATION || nk == BARRIER) {
                    kerbDir = -1;
                    break;
                }
                if (nk == KERB || nk == TACTILE || nk == FOOTWAY || nk == VERGE || nk == BLEND || nk == NONE) {
                    kerbDir = k;
                }
            }
            if (kerbDir < 0) {
                continue;
            }
            RoadStyle st = styleOf(idx);
            if (st.yellow == null || st.yellow == RoadStyle.Yellow.NONE) {
                continue;
            }
            boolean nearJunction = toCore.dist[idx] < 16 || owner[idx] < 0;
            boolean dbl = st.yellow == RoadStyle.Yellow.DOUBLE || (st.yellow == RoadStyle.Yellow.AUTO && nearJunction);
            int rot = kerbDir < 2 ? 0 : 2;                 // along the kerb
            setMark(idx, (dbl ? M_DOUBLE : M_LINE) | YELLOW, rot, 1);
        }
    }

    /** Dashes start half a gap from each end, so where two roads join the gap still lines up. */
    private static boolean dashOn(double s, double length, int on, int off) {
        double e = Math.min(s, length - s) - off / 2.0;
        return e >= 0 && (e % (on + off)) < on;
    }

    /** Lines that would run into a junction stop at it, except the main road's through a give-way junction. */
    private boolean suppressed(Seg g, int idx, Line line) {
        int co = coreOwner[idx];
        double dc = toCore.dist[idx];
        if (co < 0 && dc > 1.01) {
            return false;
        }
        int jIndex = co >= 0 ? co : coreOwner[toCore.nearest[idx]];
        if (jIndex < 0) {
            return false;
        }
        Jn j = jns.get(jIndex);
        boolean atA = g.na.id == j.node.id, atB = g.nb.id == j.node.id;
        if (!atA && !atB) {
            // the road a slip road joins keeps its lane lines; only its edge line breaks
            return co >= 0 && (j.type != Junction.MERGE || line.kerbside);
        }
        boolean minor = atA ? g.minorA : g.minorB;
        if (minor) {
            return true;
        }
        // a main road through a priority junction: its centre and lane lines carry on
        return line.kerbside && co >= 0;
    }

    /** The two arms that carry straight on through a give-way junction. */
    private List<Seg> majorPair(Jn j) {
        Node n = j.node;
        if (n.majorA >= 0 && n.majorB >= 0) {
            List<Seg> chosen = new ArrayList<>();
            for (Seg g : j.arms) {
                if (g.seg.id == n.majorA || g.seg.id == n.majorB) {
                    chosen.add(g);
                }
            }
            if (chosen.size() == 2) {
                return chosen;
            }
        }
        double best = -1e9;
        List<Seg> pair = new ArrayList<>();
        for (int a = 0; a < j.arms.size(); a++) {
            for (int b = a + 1; b < j.arms.size(); b++) {
                Seg sa = j.arms.get(a), sb = j.arms.get(b);
                double[] da = outward(sa, n), db = outward(sb, n);
                double score = -(da[0] * db[0] + da[1] * db[1]) + 0.05 * (sa.hc + sb.hc);
                if (score > best) {
                    best = score;
                    pair = List.of(sa, sb);
                }
            }
        }
        return pair;
    }

    private static double[] outward(Seg g, Node n) {
        Curve c = g.c;
        if (g.na.id == n.id) {
            return new double[]{c.tx[0], c.tz[0]};
        }
        return new double[]{-c.tx[c.n - 1], -c.tz[c.n - 1]};
    }

    /** Find where an arm runs into the junction and stop its lines a block before. */
    private void mouth(Jn j, Seg g, boolean atA) {
        Curve c = g.c;
        int side = atA ? -1 : 1;                       // the lanes heading into the junction
        double probe;
        if (g.st.oneWay) {
            probe = 0;
            if (atA) {
                return;                                  // one-way away from this junction
            }
        } else {
            probe = side * (g.st.dual ? (g.halfRes + g.hc) / 2 : g.hc / 2);
        }
        int step = atA ? -1 : 1;
        int start = atA ? c.n - 1 : 0, end = atA ? 0 : c.n - 1;
        for (int i = start; i != end + step; i += step) {
            int idx = index((int) Math.floor(c.x[i] + c.leftX(i) * probe), (int) Math.floor(c.z[i] + c.leftZ(i) * probe));
            if (idx < 0) {
                continue;
            }
            boolean hit = (core[idx] && coreOwner[idx] == j.index) || island[idx]
                    || (toCore.dist[idx] <= 1.01 && toCore.nearest[idx] >= 0 && coreOwner[toCore.nearest[idx]] == j.index);
            if (hit) {
                if (j.type == Junction.SIGNALS) {
                    // traffic lights: the crossing goes where the kerbs are still straight, the stop line before it
                    i = flareStart(g, atA, i);
                }
                double blocks = j.type == Junction.SIGNALS ? 6 : 1;
                int back = (int) Math.round(blocks / Curve.STEP);
                int cut = Math.max(0, Math.min(c.n - 1, i - step * back));
                if (atA) {
                    g.cutA = cut;
                    g.mouthA = i;
                } else {
                    g.cutB = cut;
                    g.mouthB = i;
                }
                return;
            }
        }
    }

    /** Coming along an arm towards a junction, the sample where the kerb starts curving away round a corner. */
    private int flareStart(Seg g, boolean atA, int mouth) {
        Curve c = g.c;
        int step = atA ? -1 : 1;
        int start = atA ? c.n - 1 : 0;
        for (int i = start; i != mouth; i += step) {
            for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
                double d = sideSign * (g.hc + 0.5);
                int idx = index((int) Math.floor(c.x[i] + c.leftX(i) * d), (int) Math.floor(c.z[i] + c.leftZ(i) * d));
                if (idx >= 0 && carriage[idx] && (closed[idx] || core[idx])) {
                    return i;
                }
            }
        }
        return mouth;
    }

    private void giveWay(Jn j, Seg g, boolean atA, int i) {
        Curve c = g.c;
        int side = atA ? -1 : 1;
        double inner, outer = g.hc - 0.01;
        if (g.st.oneWay) {
            inner = -g.hc + 0.01;
            side = 1;
        } else {
            inner = g.st.dual ? g.halfRes : 0;
        }
        boolean stop = j.type == Junction.STOP || j.type == Junction.SIGNALS;
        int kindAcross = stop ? M_STOP : M_GIVE_WAY;
        double lx = c.leftX(i), lz = c.leftZ(i);
        int across = rot8(lx, lz);
        int inbound = atA ? rot8(-c.tx[i], -c.tz[i]) : rot8(c.tx[i], c.tz[i]);
        int lastIdx = -1;
        for (double d = inner; d <= outer; d += 0.25) {
            double sd = side * d;
            int idx = index((int) Math.floor(c.x[i] + lx * sd), (int) Math.floor(c.z[i] + lz * sd));
            if (idx >= 0 && idx != lastIdx) {
                // PTM2 draws a stop line across its direction, give-way dashes along theirs
                setMark(idx, kindAcross, stop ? inbound : across, 6);
                lastIdx = idx;
            }
        }
        if (j.type == Junction.SIGNALS) {
            crossing(j, g, atA);
            // a pole and head each side of the approach at the stop line, facing the traffic
            // a couple of blocks back from the line, where the kerb is still straight; step out to it
            int back = (int) Math.round(2 / Curve.STEP);
            int k0 = Math.max(0, Math.min(c.n - 1, atA ? i + back : i - back));
            double kx = c.leftX(k0), kz = c.leftZ(k0);
            for (int sideSign : new int[]{side, -side}) {
                int kerbs = 0;
                for (double d = g.hc - 1; d < g.hc + j.radius + 8; d += 0.5) {
                    double sd = sideSign * d;
                    int idx = index((int) Math.floor(c.x[k0] + kx * sd), (int) Math.floor(c.z[k0] + kz * sd));
                    if (idx < 0 || kind[idx] == NONE || kind[idx] == BLEND || (kind[idx] == KERB && ++kerbs > 6)) {
                        break;
                    }
                    if (kind[idx] == FOOTWAY || kind[idx] == VERGE) {
                        signalAt(cellX(idx), cellZ(idx), top[idx], inbound);
                        break;
                    }
                }
            }
            return;
        }
        // a give-way triangle in each lane coming up to the line
        if (j.type == Junction.GIVE_WAY || j.type == Junction.ROUNDABOUT || j.type == Junction.MINI_ROUNDABOUT) {
            int back = (int) Math.round(7 / Curve.STEP);
            int ti = atA ? i + back : i - back;
            if (ti < 0 || ti >= c.n) {
                return;
            }
            int lanes = g.st.lanes;
            for (int k = 0; k < lanes; k++) {
                double centre;
                if (g.st.oneWay) {
                    centre = -g.hc + k * (g.st.laneWidth + 1) + g.st.laneWidth / 2.0;
                } else {
                    centre = side * (inner + k * (g.st.laneWidth + 1) + g.st.laneWidth / 2.0);
                }
                int idx = index((int) Math.floor(c.x[ti] + c.leftX(ti) * centre), (int) Math.floor(c.z[ti] + c.leftZ(ti) * centre));
                setMark(idx, M_TRIANGLE, inbound, 5);
            }
        }
    }

    /**
     * A signal-controlled pedestrian crossing across an arm between the stop line and the
     * junction: two rows of studs (dashes) right across the road, with red blister paving at the
     * kerbs each end.
     */
    private void crossing(Jn j, Seg g, boolean atA) {
        Curve c = g.c;
        int mouth = atA ? g.mouthA : g.mouthB;
        if (mouth < 0) {
            return;
        }
        int blk = (int) Math.round(1 / Curve.STEP);
        int dir = atA ? 1 : -1;                          // samples away from the junction
        int r1 = mouth + dir * blk, r2 = mouth + dir * 4 * blk;
        if (r2 < 0 || r2 >= c.n) {
            return;
        }
        for (int row : new int[]{r1, r2}) {
            double lx = c.leftX(row), lz = c.leftZ(row);
            int across = rot8(lx, lz);
            for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
                for (double d = 0; d < g.hc + j.radius + 4; d += 0.25) {
                    int idx = index((int) Math.floor(c.x[row] + lx * d * sideSign), (int) Math.floor(c.z[row] + lz * d * sideSign));
                    if (idx < 0 || kind[idx] != ROAD) {
                        break;
                    }
                    setMark(idx, M_HALF, across, 7);
                }
            }
        }
        // dropped kerbs with red tactile paving where the crossing meets each pavement
        for (int k = Math.min(r1, r2); k <= Math.max(r1, r2); k += 2) {
            double lx = c.leftX(k), lz = c.leftZ(k);
            for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
                int paved = 0;
                for (double d = g.hc - 1; d < g.hc + j.radius + 8 && paved < 3; d += 0.5) {
                    int idx = index((int) Math.floor(c.x[k] + lx * d * sideSign), (int) Math.floor(c.z[k] + lz * d * sideSign));
                    if (idx < 0 || kind[idx] == NONE || kind[idx] == BLEND || kind[idx] == VERGE) {
                        break;
                    }
                    if (kind[idx] == KERB || kind[idx] == FOOTWAY || kind[idx] == TACTILE) {
                        if (kind[idx] != TACTILE) {
                            kind[idx] = TACTILE;
                            paved++;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ street lights

    private void lights() {
        for (Seg g : segs) {
            RoadStyle st = g.st;
            if (!st.lights || st.footway() + st.verge == 0) {
                continue;
            }
            Curve c = g.c;
            double back = g.hc + st.kerbWidth() + st.verge + 0.5;
            int n = 0;
            for (double s = st.lightSpacing / 2.0; s < c.length; s += st.lightSpacing / 2.0, n++) {
                int i = c.index(s);
                int side = n % 2 == 0 ? 1 : -1;
                double lx = c.leftX(i) * side, lz = c.leftZ(i) * side;
                int idx = index((int) Math.floor(c.x[i] + lx * back), (int) Math.floor(c.z[i] + lz * back));
                if (idx < 0 || (kind[idx] != FOOTWAY && kind[idx] != VERGE) || toCore.dist[idx] < 5) {
                    continue;
                }
                if (tooClose(cellX(idx), cellZ(idx))) {
                    continue;
                }
                fixtures.add(new Fixture(FixtureType.STREET_LIGHT, cellX(idx), cellZ(idx), top[idx], rot8(-lx, -lz), 0));
            }
        }
    }

    /** A signal head here, or a second head higher up an existing pole on the same corner. */
    private void signalAt(int x, int z, float t, int rot) {
        for (Fixture f : fixtures) {
            if (f.type() == FixtureType.SIGNAL && Math.abs(f.x() - x) <= 2 && Math.abs(f.z() - z) <= 2) {
                if (f.rot8() == rot) {
                    return;
                }
                int level = 0;
                for (Fixture o : fixtures) {
                    if (o.type() == FixtureType.SIGNAL && o.x() == f.x() && o.z() == f.z()) {
                        level = Math.max(level, o.level() + 1);
                    }
                }
                if (level <= 1) {
                    fixtures.add(new Fixture(FixtureType.SIGNAL, f.x(), f.z(), f.top(), rot, level));
                }
                return;
            }
        }
        fixtures.add(new Fixture(FixtureType.SIGNAL, x, z, t, rot, 0));
    }

    private boolean tooClose(int x, int z) {
        return tooClose(x, z, 6);
    }

    private boolean tooClose(int x, int z, int r) {
        for (Fixture f : fixtures) {
            if (Math.abs(f.x() - x) <= r && Math.abs(f.z() - z) <= r) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ traffic points (PTM2's AI cars)

    /** Where a slip road joins its road, how far along the road the cars merge in (or peel off before). */
    private static final double MERGE_RUN = 10;
    /** Room at a dead end to turn round in. */
    private static final double TURN = 4;

    /** One end of a lane at a node: its point, which lane it is (0 by the kerb) of how many, and which way it's going there. */
    private record LaneEnd(Seg seg, int point, int lane, int lanes, double dx, double dz) {
    }

    /** A lane's point where a slip road joins or leaves it. */
    private record SplitKey(Seg seg, boolean ab, int lane, int sample) {
    }

    /**
     * A point down the middle of every lane (traffic keeps left), joined lane by lane: on through
     * plain bends, across junctions to the lanes each one can turn into (left from the left lane,
     * right from the right one), round roundabouts clockwise, onto and off the roads slip roads
     * join, and round at dead ends. PTM2 draws a straight lane between points more than 32 apart
     * or in line, and curves the rest to fit its eight directions, so the points are spaced to
     * suit: far apart on the straight, close together round bends.
     */
    private void traffic() {
        Map<Long, Integer> at = new HashMap<>();
        Map<Integer, Integer> degree = new HashMap<>();
        for (Seg g : segs) {
            degree.merge(g.na.id, 1, Integer::sum);
            degree.merge(g.nb.id, 1, Integer::sum);
        }
        Map<Seg, List<Integer>> splits = new HashMap<>();
        for (Jn j : jns) {
            if (j.type == Junction.MERGE && j.host != null) {
                int im = mergeSample(j), d = (int) Math.round(MERGE_RUN / Curve.STEP);
                splits.computeIfAbsent(j.host, k -> new ArrayList<>()).addAll(List.of(j.host.c.index((im - d) * Curve.STEP),
                        j.host.c.index((im + d) * Curve.STEP)));
            }
        }
        Map<SplitKey, Integer> splitPoints = new HashMap<>();
        Map<Integer, List<LaneEnd>> ins = new HashMap<>(), outs = new HashMap<>();
        for (Seg g : segs) {
            Curve c = g.c;
            for (int dir = 0; dir < (g.st.oneWay ? 1 : 2); dir++) {
                boolean ab = dir == 0;
                Node from = ab ? g.na : g.nb, to = ab ? g.nb : g.na;
                int i0 = laneStart(g, ab, degree), i1 = laneEnd(g, !ab, degree), step = ab ? 1 : -1;
                if ((i1 - i0) * step < 4) {
                    continue;
                }
                int count = (i1 - i0) * step + 1;
                for (int k = 0; k < g.st.lanes; k++) {
                    double off = laneOffset(g.st, k) * step;
                    double[] px = new double[count], pz = new double[count];
                    for (int m = 0; m < count; m++) {
                        int i = i0 + m * step;
                        px[m] = c.x[i] + c.leftX(i) * off;
                        pz[m] = c.z[i] + c.leftZ(i) * off;
                    }
                    boolean[] keep = com.ptmuk.road.OsmMap.simplify(px, pz, 0, count - 1, 0.5);
                    for (int s : splits.getOrDefault(g, List.of())) {
                        int m = (s - i0) * step;
                        if (m >= 0 && m < count) {
                            keep[m] = true;
                        }
                    }
                    List<Integer> ms = new ArrayList<>();
                    for (int m = 0; m < count; m++) {
                        if (!keep[m]) {
                            continue;
                        }
                        // PTM2 would bend a 10 to 32 block piece to fit its eight directions: cut it into short straight ones
                        if (!ms.isEmpty()) {
                            int last = ms.get(ms.size() - 1);
                            double len = Math.hypot(px[m] - px[last], pz[m] - pz[last]);
                            int parts = len >= 10 && len <= 32 ? (int) Math.ceil(len / 9) : 1;
                            for (int p = 1; p < parts; p++) {
                                ms.add(last + (m - last) * p / parts);
                            }
                        }
                        ms.add(m);
                    }
                    int first = -1, prev = -1;
                    for (int m : ms) {
                        int i = i0 + m * step;
                        int p = trafficPoint(at, px[m], pz[m], c.tx[i] * step, c.tz[i] * step);
                        if (p < 0) {
                            continue;
                        }
                        if (prev >= 0) {
                            link(prev, p);
                        }
                        if (first < 0) {
                            first = p;
                        }
                        prev = p;
                        if (splits.getOrDefault(g, List.of()).contains(i)) {
                            splitPoints.put(new SplitKey(g, ab, k, i), p);
                        }
                    }
                    if (first < 0 || first == prev) {
                        continue;
                    }
                    outs.computeIfAbsent(from.id, x -> new ArrayList<>())
                            .add(new LaneEnd(g, first, k, g.st.lanes, c.tx[i0] * step, c.tz[i0] * step));
                    ins.computeIfAbsent(to.id, x -> new ArrayList<>())
                            .add(new LaneEnd(g, prev, k, g.st.lanes, c.tx[i1] * step, c.tz[i1] * step));
                }
            }
        }
        java.util.Set<Integer> nodes = new java.util.HashSet<>(ins.keySet());
        nodes.addAll(outs.keySet());
        for (int id : nodes) {
            List<LaneEnd> in = ins.getOrDefault(id, List.of()), out = outs.getOrDefault(id, List.of());
            Jn j = jnByNode.get(id);
            if (j != null && j.type == Junction.ROUNDABOUT) {
                ring(at, j, in, out);
            } else if (j != null && j.type == Junction.MERGE) {
                int im = mergeSample(j), d = (int) Math.round(MERGE_RUN / Curve.STEP);
                Curve h = j.host.c;
                for (LaneEnd e : in) {          // joining: onto the kerb lane going the same way, a little further on
                    boolean ab = e.dx() * h.tx[im] + e.dz() * h.tz[im] > 0;
                    Integer p = splitPoints.get(new SplitKey(j.host, ab, 0, h.index((ab ? im + d : im - d) * Curve.STEP)));
                    if (p != null) {
                        link(e.point(), p);
                    }
                }
                for (LaneEnd o : out) {         // leaving: off the kerb lane going its way, a little before
                    boolean ab = o.dx() * h.tx[im] + o.dz() * h.tz[im] > 0;
                    Integer p = splitPoints.get(new SplitKey(j.host, ab, 0, h.index((ab ? im - d : im + d) * Curve.STEP)));
                    if (p != null) {
                        link(p, o.point());
                    }
                }
            } else {
                boolean deadEnd = j == null && degree.getOrDefault(id, 0) == 1, plain = j == null;
                for (LaneEnd e : in) {
                    for (LaneEnd o : out) {
                        if (deadEnd ? o.seg() == e.seg() && o.lane() == e.lane() : o.seg() != e.seg() && turns(e, o, plain)) {
                            link(e.point(), o.point());
                        }
                    }
                }
            }
        }
    }

    /** Whether a car can go from one lane into another at a junction (or straight on through a plain bend). */
    private static boolean turns(LaneEnd e, LaneEnd o, boolean plain) {
        double dot = e.dx() * o.dx() + e.dz() * o.dz();
        if (plain || dot > 0.7) {
            return o.lane() == Math.min(e.lane(), o.lanes() - 1);
        }
        if (dot < -0.85) {
            return false;                                   // straight back the way it came
        }
        boolean left = o.dx() * e.dz() - o.dz() * e.dx() > 0;
        return left ? e.lane() == 0 && o.lane() == 0 : e.lane() == e.lanes() - 1 && o.lane() == o.lanes() - 1;
    }

    /** Round a roundabout clockwise: a ring of points, joined from every way in and to every way out. */
    private void ring(Map<Long, Integer> at, Jn j, List<LaneEnd> in, List<LaneEnd> out) {
        double rc = (j.ri + j.ro) / 2, cx = j.node.x + 0.5, cz = j.node.z + 0.5;
        int m = Math.max(8, (int) Math.round(2 * Math.PI * rc / 8));
        int[] ring = new int[m];
        for (int i = 0; i < m; i++) {
            double a = 2 * Math.PI * i / m;             // z is south, so a rising angle goes clockwise on the map
            ring[i] = trafficPoint(at, cx + Math.cos(a) * rc, cz + Math.sin(a) * rc, -Math.sin(a), Math.cos(a));
        }
        for (int i = 0; i < m; i++) {
            if (ring[i] >= 0 && ring[(i + 1) % m] >= 0) {
                link(ring[i], ring[(i + 1) % m]);
            }
        }
        for (LaneEnd e : in) {
            int r = ring[Math.floorMod((int) Math.ceil(angle(e.point(), cx, cz) * m / (2 * Math.PI)), m)];
            if (r >= 0) {
                link(e.point(), r);
            }
        }
        for (LaneEnd o : out) {
            int r = ring[Math.floorMod((int) Math.floor(angle(o.point(), cx, cz) * m / (2 * Math.PI)), m)];
            if (r >= 0) {
                link(r, o.point());
            }
        }
    }

    private double angle(int point, double cx, double cz) {
        TrafficPoint p = traffic.get(point);
        return Math.atan2(p.z() + 0.5 - cz, p.x() + 0.5 - cx);
    }

    /** The sample on a slip road's host road nearest where it joins. */
    private static int mergeSample(Jn j) {
        Curve h = j.host.c;
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < h.n; i++) {
            double d = Math.hypot(h.x[i] - (j.node.x + 0.5), h.z[i] - (j.node.z + 0.5));
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** How far left of the way it goes the middle of a lane is from the road's centre line (lane 0 is by the kerb). */
    private static double laneOffset(RoadStyle st, int lane) {
        double kerb = st.oneWay ? st.directionWidth() / 2.0 : (st.dual ? st.reservationWidth() / 2.0 : 0.5) + st.directionWidth();
        return kerb - lane * (st.laneWidth + 1) - st.laneWidth / 2.0;
    }

    /** The sample where lanes leaving one end of a road start: the edge of the junction, or turning room in from a dead end. */
    private int laneStart(Seg g, boolean atA, Map<Integer, Integer> degree) {
        int mouth = atA ? g.mouthA : g.mouthB;
        return mouth >= 0 ? mouth : end(g, atA, degree);
    }

    /** The sample where lanes coming into one end of a road stop: a block short of the give way or stop line. */
    private int laneEnd(Seg g, boolean atA, Map<Integer, Integer> degree) {
        int cut = atA ? g.cutA : g.cutB, back = (int) Math.round(1 / Curve.STEP);
        return cut >= 0 ? Math.max(0, Math.min(g.c.n - 1, cut + (atA ? back : -back))) : end(g, atA, degree);
    }

    private int end(Seg g, boolean atA, Map<Integer, Integer> degree) {
        Node n = atA ? g.na : g.nb;
        if (!jnByNode.containsKey(n.id) && degree.getOrDefault(n.id, 0) == 1 && !g.st.oneWay) {
            return g.c.index(atA ? TURN : g.c.length - TURN);
        }
        return atA ? 0 : g.c.n - 1;
    }

    /** The point on the road at (x, z), going the way (tx, tz): a new one, or the one already in that block. */
    private int trafficPoint(Map<Long, Integer> at, double x, double z, double tx, double tz) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), idx = index(bx, bz);
        if (idx < 0 || kind[idx] != ROAD || Float.isNaN(top[idx])) {
            return -1;
        }
        return at.computeIfAbsent(((long) bx << 32) ^ (bz & 0xFFFFFFFFL), k -> {
            traffic.add(new TrafficPoint(bx, bz, top[idx], rot8(tx, tz), new ArrayList<>()));
            return traffic.size() - 1;
        });
    }

    private void link(int from, int to) {
        List<Integer> next = traffic.get(from).next();
        if (from != to && !next.contains(to)) {
            next.add(to);
        }
    }

    // ------------------------------------------------------------------ what still needs building

    private boolean builtOwner(int o) {
        if (o >= 0) {
            return segs.get(o).seg.built;
        }
        if (o != Integer.MIN_VALUE) {
            return jns.get(-o - 1).node.built;
        }
        return true;
    }

    private void markDirty() {
        int size = w * h;
        for (int idx = 0; idx < size; idx++) {
            if (kind[idx] == NONE) {
                continue;
            }
            int so = siteOwner[idx] >= 0 ? siteOwner[idx] : blendFrom[idx] >= 0 ? siteOwner[blendFrom[idx]] : -1;
            if (so >= 0) {
                dirty[idx] = !sites.get(so).site.built;
                continue;
            }
            int near = carriage[idx] ? idx : toRoad.nearest[idx];
            if (near < 0) {
                near = idx;
            }
            int o = island[idx] && coreOwner[idx] >= 0 ? -(coreOwner[idx] + 1) : owner[near];
            dirty[idx] = !builtOwner(o);
        }
        for (Jn j : jns) {
            if (j.node.built) {
                continue;
            }
            double r = j.zone + j.widest.halfOuter() + BLEND_WIDTH;
            int cx = (int) Math.floor(j.node.x), cz = (int) Math.floor(j.node.z);
            for (int x = cx - (int) r; x <= cx + (int) r; x++) {
                for (int z = cz - (int) r; z <= cz + (int) r; z++) {
                    int idx = index(x, z);
                    if (idx >= 0 && kind[idx] != NONE && Math.hypot(x - cx, z - cz) <= r) {
                        dirty[idx] = true;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ sites

    private static final Map<SiteLayout.ItemType, FixtureType> SITE_FIXTURES = Map.ofEntries(
            Map.entry(SiteLayout.ItemType.LAMP, FixtureType.STREET_LIGHT),
            Map.entry(SiteLayout.ItemType.TREE, FixtureType.TREE),
            Map.entry(SiteLayout.ItemType.EV_CHARGER, FixtureType.EV_CHARGER),
            Map.entry(SiteLayout.ItemType.PAY_MACHINE, FixtureType.PAY_MACHINE),
            Map.entry(SiteLayout.ItemType.PARKING_SIGN, FixtureType.PARKING_SIGN),
            Map.entry(SiteLayout.ItemType.TROLLEYS, FixtureType.TROLLEYS),
            Map.entry(SiteLayout.ItemType.BIN, FixtureType.BIN),
            Map.entry(SiteLayout.ItemType.BOLLARD, FixtureType.BOLLARD),
            Map.entry(SiteLayout.ItemType.BENCH, FixtureType.BENCH),
            Map.entry(SiteLayout.ItemType.DISABLED_BAY, FixtureType.DISABLED_BAY),
            Map.entry(SiteLayout.ItemType.PARENT_BAY, FixtureType.PARENT_BAY),
            Map.entry(SiteLayout.ItemType.EV_BAY, FixtureType.EV_BAY),
            Map.entry(SiteLayout.ItemType.ORDER_POINT, FixtureType.ORDER_POINT),
            Map.entry(SiteLayout.ItemType.MENU_BOARD, FixtureType.MENU_BOARD),
            Map.entry(SiteLayout.ItemType.PICNIC_TABLE, FixtureType.PICNIC_TABLE));

    /** A road reaching a site, in world terms: the side (0 south, 1 west, 2 north, 3 east), where on it and how wide. */
    private record Way(int side, int x, int z, int width) {
    }

    /** Where the roads reach a site: where their centre lines cross its edge, or end just outside it. */
    private List<Way> ways(Site st) {
        List<Way> out = new ArrayList<>();
        for (Seg g : segs) {
            Curve c = g.c;
            int width = (int) Math.ceil(g.hc * 2) + 2;
            boolean prev = st.contains(c.x[0], c.z[0]);
            for (int i = 1; i < c.n; i++) {
                boolean in = st.contains(c.x[i], c.z[i]);
                if (in != prev) {
                    int k = in ? i : i - 1;
                    addWay(out, st, c.x[k], c.z[k], width);
                }
                prev = in;
            }
            for (Node n : new Node[]{g.na, g.nb}) {
                double x = n.x + 0.5, z = n.z + 0.5;
                double dx = Math.max(Math.max(st.x0 - x, x - (st.x1 + 1)), 0), dz = Math.max(Math.max(st.z0 - z, z - (st.z1 + 1)), 0);
                if (!st.contains(x, z) && Math.hypot(dx, dz) <= 3) {
                    addWay(out, st, Math.max(st.x0, Math.min(st.x1 + 0.5, x)), Math.max(st.z0, Math.min(st.z1 + 0.5, z)), width);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.width, a.width));
        return out;
    }

    private static void addWay(List<Way> out, Site st, double x, double z, int width) {
        double[] dist = {st.z1 + 1 - z, x - st.x0, z - st.z0, st.x1 + 1 - x};
        int side = 0;
        for (int k = 1; k < 4; k++) {
            if (dist[k] < dist[side]) {
                side = k;
            }
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (Way w : out) {
            if (w.side == side && Math.abs(w.x - bx) + Math.abs(w.z - bz) < Math.max(w.width, width)) {
                return;
            }
        }
        out.add(new Way(side, bx, bz, width));
    }

    /** The side of a site that faces the road: as set, or the side the widest road reaches. */
    public int siteFront(Site st) {
        if (st.front >= 0) {
            return st.front & 3;
        }
        List<Way> w = ways(st);
        return w.isEmpty() ? 0 : w.get(0).side;
    }

    private void applySites() {
        for (Site st : plan.sites) {
            List<Way> ways = ways(st);
            int front = st.front >= 0 ? st.front & 3 : ways.isEmpty() ? 0 : ways.get(0).side;
            SiteFrame frame = new SiteFrame(st.x0, st.z0, st.x1, st.z1, front);
            List<SiteLayout.Entrance> ins = new ArrayList<>();
            for (Way w : ways) {
                int side = frame.local(w.side);
                int along = side == SiteFrame.FRONT || side == SiteFrame.BACK ? frame.u(w.x, w.z) : frame.v(w.x, w.z);
                ins.add(new SiteLayout.Entrance(side, along, w.width));
            }
            SiteLayout sl = new SiteLayout(st, frame, ins);
            int si = sites.size();
            sites.add(sl);
            int level = siteLevel(st.id);
            for (int v = 0; v < sl.d; v++) {
                for (int u = 0; u < sl.w; u++) {
                    int i = sl.at(u, v);
                    int idx = index(frame.x(u, v), frame.z(u, v));
                    if (idx < 0 || carriage[idx]) {
                        continue;
                    }
                    kind[idx] = sl.kind[i];
                    top[idx] = level + sl.rise[i];
                    mark[idx] = sl.mark[i];
                    markRot[idx] = (byte) (2 * frame.world(sl.markDir[i]));
                    markPrio[idx] = 1;
                    siteOwner[idx] = si;
                }
            }
            for (SiteLayout.Item it : sl.items) {
                int x = frame.x(it.u(), it.v()), z = frame.z(it.u(), it.v());
                int idx = index(x, z);
                if (idx >= 0 && siteOwner[idx] == si) {
                    fixtures.add(new Fixture(SITE_FIXTURES.get(it.type()), x, z, top[idx], 2 * frame.world(it.dir()), 0));
                }
            }
            for (SiteLayout.Building b : sl.buildings) {
                buildings.add(new SiteBuilding(st.id, b.kind(), frame.sub(b.u0(), b.v0(), b.u1(), b.v1(), b.dir()), level + 1, b.brand()));
            }
        }
    }

    /** The site a column belongs to, or null. */
    public SiteLayout siteAt(int idx) {
        return idx < 0 || siteOwner[idx] < 0 ? null : sites.get(siteOwner[idx]);
    }

    public boolean anythingToBuild() {
        for (boolean d : dirty) {
            if (d) {
                return true;
            }
        }
        return false;
    }
}
