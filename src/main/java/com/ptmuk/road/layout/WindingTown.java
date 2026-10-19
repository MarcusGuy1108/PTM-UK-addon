package com.ptmuk.road.layout;

import com.ptmuk.building.PrefabCatalog;
import com.ptmuk.map.StreetNames;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;

/**
 * A town of winding streets, the way old British towns grew: a main road through it the long way,
 * bending a little; side streets off it at odd angles, bending as they go, some running on into
 * another street (so blocks and crescents form) and the rest ending as closes; and shorter streets
 * off those. Streets keep far enough apart for two gardens back to back. Every street is lined
 * with buildings one plot each, facing the road and stepping round the bends as close behind the
 * pavement as they go: shops along the middle of the main road, a church, pub and surgery either
 * side of them, houses everywhere else (terraces near the main road, semis, then detached).
 */
final class WindingTown {
    private static final double STEP_MIN = 30, STEP_MAX = 44;
    /** Behind the pavement on each side: room for a house and its garden. */
    private static final double ROW = 26;
    /** Junctions along a street at least this far apart, and from its ends. */
    private static final double JUNCTION_GAP = 34;
    /** A plain point of a street this near where a junction would go becomes the junction. */
    private static final double SNAP = 10;

    /** A point on a street; shared by the streets that meet there. */
    private static final class Pt {
        final double x, z;
        /** Where streets meet; or where a street leaves town (neither can be run into end on). */
        boolean junction, out;

        Pt(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class Street {
        final List<Pt> pts = new ArrayList<>();
        final RoadStyle style;
        final int level;
        String name;

        Street(RoadStyle style, int level) {
            this.style = style;
            this.level = level;
        }

        double length() {
            double l = 0;
            for (int i = 0; i + 1 < pts.size(); i++) {
                l += Math.hypot(pts.get(i + 1).x - pts.get(i).x, pts.get(i + 1).z - pts.get(i).z);
            }
            return l;
        }
    }

    /** A place along a street: the point, the way the street heads there, the piece it's on and how far along. */
    private record At(double x, double z, double tx, double tz, int piece, double s) {
    }

    private final RoadPlan plan;
    private final RoadPlan.Site town;
    private final RandomSource rand;
    private final boolean alongX;
    private final RoadStyle res;
    private final List<Street> all = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private final List<RoadPlan.Site> kept;
    /** What crowds() says when it's ground kept clear in the way. */
    private static final Street KEPT = new Street(RoadStyle.residential(), -1);
    private Street spine;
    int streets, plots;

    WindingTown(RoadPlan plan, RoadPlan.Site town, RandomSource rand) {
        this.plan = plan;
        this.town = town;
        this.rand = rand;
        this.alongX = town.width() >= town.depth();
        this.res = TownLayout.kerbed(RoadStyle.residential(), town);
        this.kept = TownLayout.kept(plan, town);
    }

    void run() {
        spine();
        // side streets off the main road, then shorter ones off those
        for (int level = 1; level <= 2; level++) {
            for (Street parent : new ArrayList<>(all)) {
                if (parent.level != level - 1) {
                    continue;
                }
                // off alternate sides, so each side's are a pair of gardens apart; now and then a crossroads.
                // Streets off side streets start far enough out for houses between them and the main road
                double len = parent.length();
                int side = rand.nextBoolean() ? 1 : -1;
                double first = level == 1 ? JUNCTION_GAP + 10 + rand.nextDouble() * 20 : apart(spine, res) + 10 + rand.nextDouble() * 10;
                for (double s = first; s < len - JUNCTION_GAP - 6; s += 34 + rand.nextDouble() * 12) {
                    branch(parent, s, side, level, false);
                    if (rand.nextFloat() < 0.2f) {
                        branch(parent, s, -side, level, true);
                        s += 34 + rand.nextDouble() * 12;
                    } else {
                        side = -side;
                    }
                }
            }
        }
        roads();
        plots();
    }

    // ------------------------------------------------------------------ the main road

    /** Town coordinates (along its length, across it) to the world. */
    private double wx(double a, double c) {
        return alongX ? town.x0 + a : town.x0 + c;
    }

    private double wz(double a, double c) {
        return alongX ? town.z0 + c : town.z0 + a;
    }

    private void spine() {
        double len = alongX ? town.width() : town.depth(), wide = alongX ? town.depth() : town.width();
        spine = new Street(TownLayout.kerbed(town.mainRoad ? RoadStyle.mainRoad() : RoadStyle.residential(), town), 0);
        spine.name = town.highStreet ? "High Street" : StreetNames.pick(rand, names);
        names.add("High Street");
        double a = 0, c = wide / 2 + (rand.nextDouble() - 0.5) * wide * 0.16, heading = (rand.nextDouble() - 0.5) * 0.4;
        spine.pts.add(new Pt(wx(a, c), wz(a, c)));
        while (a < len - 1) {
            double step = STEP_MIN + rand.nextDouble() * (STEP_MAX - STEP_MIN);
            heading += (rand.nextDouble() - 0.5) * 0.35;
            // drift back towards the middle, never far off the long way
            heading -= Math.signum(c - wide / 2) * Math.max(0, Math.abs(c - wide / 2) - wide * 0.12) / wide;
            heading = Math.max(-0.38, Math.min(0.38, heading));
            if (a + step * Math.cos(heading) > len - 1 - STEP_MIN * 0.6) {
                c += (len - 1 - a) * Math.tan(heading);
                a = len - 1;
            } else {
                a += step * Math.cos(heading);
                c += step * Math.sin(heading);
            }
            spine.pts.add(new Pt(wx(a, c), wz(a, c)));
        }
        all.add(spine);
    }

    // ------------------------------------------------------------------ side streets

    private boolean inside(double x, double z, double margin) {
        return x >= town.x0 + margin && x <= town.x1 + 1 - margin && z >= town.z0 + margin && z <= town.z1 + 1 - margin;
    }

    /** Where the way from (x0, z0) towards (x1, z1) crosses the edge of town (if it's far enough to be worth going), or null. */
    private double[] leaves(double x0, double z0, double x1, double z1) {
        double t = 1;
        double[][] edges = {{town.x0, x1 - x0, x0}, {town.x1 + 1, x1 - x0, x0}, {town.z0, z1 - z0, z0}, {town.z1 + 1, z1 - z0, z0}};
        for (double[] e : edges) {
            if (Math.abs(e[1]) > 1e-9) {
                double f = (e[0] - e[2]) / e[1];
                if (f > 0 && f < t) {
                    t = f;
                }
            }
        }
        double x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t;
        return Math.hypot(x - x0, z - z0) < 10 ? null : new double[]{x, z};
    }

    /** How far apart the middles of two streets have to stay for houses back to back between them. */
    private double apart(Street a, RoadStyle b) {
        return a.style.halfOuter() + b.halfOuter() + 2 * ROW;
    }

    /** The place s along a street. */
    private static At at(Street st, double s) {
        double run = 0;
        for (int i = 0; i + 1 < st.pts.size(); i++) {
            Pt p = st.pts.get(i), q = st.pts.get(i + 1);
            double l = Math.hypot(q.x - p.x, q.z - p.z);
            if (run + l >= s || i + 2 == st.pts.size()) {
                double f = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (s - run) / l));
                return new At(p.x + (q.x - p.x) * f, p.z + (q.z - p.z) * f, (q.x - p.x) / l, (q.z - p.z) / l, i, s);
            }
            run += l;
        }
        Pt p = st.pts.get(0);
        return new At(p.x, p.z, 1, 0, 0, 0);
    }

    /** How far along a street its point i is. */
    private static double along(Street st, int i) {
        double run = 0;
        for (int k = 0; k < i; k++) {
            run += Math.hypot(st.pts.get(k + 1).x - st.pts.get(k).x, st.pts.get(k + 1).z - st.pts.get(k).z);
        }
        return run;
    }

    /** Whether a junction can go s along a street: clear of its ends and its other junctions (a crossroads shares one). */
    private static boolean room(Street st, double s, boolean cross) {
        double len = st.length();
        if (s < JUNCTION_GAP || s > len - JUNCTION_GAP) {
            return false;
        }
        for (int i = 0; i < st.pts.size(); i++) {
            double d = Math.abs(along(st, i) - s);
            if (st.pts.get(i).junction && d < JUNCTION_GAP && !(cross && d < SNAP)) {
                return false;
            }
        }
        return true;
    }

    /** The point s along a street made a junction: a point already there (a crossroads shares its junction), or a new one put in. */
    private static Pt join(Street st, double s) {
        for (int i = 1; i + 1 < st.pts.size(); i++) {
            if (Math.abs(along(st, i) - s) < SNAP) {
                st.pts.get(i).junction = true;
                return st.pts.get(i);
            }
        }
        At a = at(st, s);
        Pt p = new Pt(a.x(), a.z());
        p.junction = true;
        st.pts.add(a.piece() + 1, p);
        return p;
    }

    /** The nearest point of a street to (x, z): {distance, how far along, the way it heads x, z}. */
    private static double[] nearest(Street st, double x, double z) {
        double best = Double.MAX_VALUE, bestS = 0, tx = 1, tz = 0, run = 0;
        for (int i = 0; i + 1 < st.pts.size(); i++) {
            Pt p = st.pts.get(i), q = st.pts.get(i + 1);
            double dx = q.x - p.x, dz = q.z - p.z, l2 = dx * dx + dz * dz, l = Math.sqrt(l2);
            double f = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - p.x) * dx + (z - p.z) * dz) / l2));
            double d = Math.hypot(p.x + dx * f - x, p.z + dz * f - z);
            if (d < best) {
                best = d;
                bestS = run + f * l;
                tx = dx / l;
                tz = dz / l;
            }
            run += l;
        }
        return new double[]{best, bestS, tx, tz};
    }

    /**
     * The first street that the stretch from (x0, z0) to (x1, z1) comes too near, or null. Never
     * nearer than its own width beside another road, except where they meet: at the junction it
     * leaves from (`leave`, heading off to `sideSign` of the road there) and the one it ends at
     * (`reach`), if any. Not near enough for houses back to back between them, either, except by
     * the junction it leaves from (it heads well away from the streets there, so the houses fit in
     * the angle between) or a street over the other side of the road it leaves.
     */
    private Street crowds(double x0, double z0, double x1, double z1, RoadStyle style, Street from, At leave, int sideSign, double[] reach) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        // ground kept clear counts as a street in the way (one that can't be joined)
        for (RoadPlan.Site k : kept) {
            double m = style.halfOuter() + 2;
            for (double t = 0; t <= len; t += 3) {
                double x = x0 + (x1 - x0) * t / Math.max(len, 1e-9), z = z0 + (z1 - z0) * t / Math.max(len, 1e-9);
                if (x >= k.x0 - m && x <= k.x1 + 1 + m && z >= k.z0 - m && z <= k.z1 + 1 + m) {
                    return KEPT;
                }
            }
        }
        for (Street st : all) {
            double need = apart(st, style), hard = st.style.halfOuter() + style.halfOuter() + 4;
            boolean atLeave = nearest(st, leave.x(), leave.z())[0] < 1, atReach = reach != null && nearest(st, reach[0], reach[1])[0] < 1;
            for (double t = 0; t <= len; t += 3) {
                double x = x0 + (x1 - x0) * t / Math.max(len, 1e-9), z = z0 + (z1 - z0) * t / Math.max(len, 1e-9);
                double[] r = nearest(st, x, z);
                if (r[0] >= need) {
                    continue;
                }
                double dl = Math.hypot(x - leave.x(), z - leave.z()), dr = reach == null ? Double.MAX_VALUE : Math.hypot(x - reach[0], z - reach[1]);
                if (r[0] < hard) {
                    if (atLeave && dl < 2 * hard + 2 || atReach && dr < 2 * hard + 2) {
                        continue;
                    }
                    return st;
                }
                if (atLeave || atReach && dr < need) {
                    continue;
                }
                At q = at(st, r[1]);
                if (dl < need && (side(leave, q.x(), q.z()) * sideSign < 0 || nearest(from, q.x(), q.z())[0] < 2)) {
                    continue;
                }
                return st;
            }
        }
        return null;
    }

    /** Which side of the road at `a` a point is: + left, - right. */
    private static double side(At a, double x, double z) {
        return (x - a.x()) * a.tz() - (z - a.z()) * a.tx();
    }

    /**
     * A street off `parent` s along it on one side, bending as it goes until it reaches the edge of
     * town or comes too near another street. At the edge it may run on out of town, or turn along
     * the edge. Near another street it runs into it if it meets it squarely enough where there's
     * room for a junction, or carries on into it end to end if that's a close (so the two make a
     * crescent), or else stops: a close of its own. Too short to be worth it: nothing.
     */
    private void branch(Street parent, double s, int side, int level, boolean cross) {
        if (!room(parent, s, cross)) {
            return;
        }
        At p = at(parent, s);
        double h0 = Math.atan2(-p.tx() * side, p.tz() * side) + (rand.nextDouble() - 0.5) * 0.9, h = h0;
        double edge = res.halfOuter() + ROW * 0.7;
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{p.x(), p.z()});
        Street onto = null;
        double ontoS = 0;
        Pt endOn = null;
        boolean out = false, turned = false;
        int steps = level == 1 ? 6 : 3;
        for (int k = 0; k < steps; k++) {
            double[] cur = path.get(path.size() - 1);
            double step = STEP_MIN + rand.nextDouble() * (STEP_MAX - STEP_MIN);
            if (k > 0) {
                h = Math.max(h0 - 0.6, Math.min(h0 + 0.6, h + (rand.nextDouble() - 0.5) * 0.75));
            }
            double nx = cur[0] + Math.cos(h) * step, nz = cur[1] + Math.sin(h) * step;
            if (!inside(nx, nz, edge)) {
                if (level != 1 || k == 0) {
                    break;
                }
                // run on out of town, or turn along the edge (once), or stop
                double[] edgeAt = !turned && rand.nextFloat() < 0.35f ? leaves(cur[0], cur[1], nx, nz) : null;
                if (edgeAt != null && crowds(cur[0], cur[1], edgeAt[0], edgeAt[1], res, parent, p, side, null) == null) {
                    path.add(edgeAt);
                    out = true;
                    break;
                }
                if (turned || rand.nextFloat() < 0.4f) {
                    break;
                }
                turned = true;
                h0 = h = h + (rand.nextBoolean() ? 1 : -1) * (1.2 + rand.nextDouble() * 0.3);
                steps += 3;
                continue;
            }
            Street near = crowds(cur[0], cur[1], nx, nz, res, parent, p, side, null);
            if (near == null) {
                path.add(new double[]{nx, nz});
                continue;
            }
            if (near == KEPT) {
                break;
            }
            // carry on into a close end on: a crescent
            for (Pt end : List.of(near.pts.get(0), near.pts.get(near.pts.size() - 1))) {
                double dx = end.x - cur[0], dz = end.z - cur[1], d = Math.hypot(dx, dz);
                boolean ahead = d > 1e-6 && (dx * Math.cos(h) + dz * Math.sin(h)) / d > 0.5;
                if (!end.junction && !end.out && ahead && d <= apart(near, res) + STEP_MAX && Math.hypot(end.x - p.x(), end.z - p.z()) >= apart(near, res)
                        && crowds(cur[0], cur[1], end.x, end.z, res, parent, p, side, new double[]{end.x, end.z}) == null) {
                    endOn = end;
                    onto = near;
                    break;
                }
            }
            if (endOn != null) {
                break;
            }
            // or into its side, if it meets it squarely enough, near enough, with room
            double[] q = nearest(near, nx, nz);
            At meet = at(near, q[1]);
            double dx = meet.x() - cur[0], dz = meet.z() - cur[1], d = Math.hypot(dx, dz);
            boolean square = d > 1e-6 && Math.abs(dx * meet.tz() - dz * meet.tx()) / d > 0.77;
            // (stopping where it first came too near, it's a house's depth or so off it; not so near where it left that it would make a sliver)
            if (square && d <= apart(near, res) + STEP_MAX && Math.hypot(meet.x() - p.x(), meet.z() - p.z()) >= apart(near, res)
                    && room(near, q[1], false) && (near != parent || Math.abs(q[1] - s) > 2 * JUNCTION_GAP)
                    && crowds(cur[0], cur[1], meet.x(), meet.z(), res, parent, p, side, new double[]{meet.x(), meet.z()}) == null) {
                onto = near;
                ontoS = q[1];
            }
            break;
        }
        double length = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            length += Math.hypot(path.get(i + 1)[0] - path.get(i)[0], path.get(i + 1)[1] - path.get(i)[1]);
        }
        if (onto == null && length < STEP_MIN * (level == 1 ? 1 : 2)) {
            return;
        }
        Street st = new Street(res, level);
        st.name = endOn != null ? onto.name : StreetNames.pick(rand, names);
        st.pts.add(join(parent, s));
        for (int i = 1; i < path.size(); i++) {
            st.pts.add(new Pt(path.get(i)[0], path.get(i)[1]));
        }
        st.pts.get(st.pts.size() - 1).out = out;
        if (endOn != null) {
            st.pts.add(endOn);
            endOn.junction = true;          // nothing else runs into it now
        } else if (onto != null) {
            st.pts.add(join(onto, ontoS));
        }
        all.add(st);
    }

    // ------------------------------------------------------------------ into the plan

    /** Which street each new road belongs to, and how far along that street it starts. */
    private final Map<RoadPlan.Segment, Street> streetOf = new LinkedHashMap<>();
    private final Map<RoadPlan.Segment, Double> startOf = new LinkedHashMap<>();

    private void roads() {
        Map<Pt, RoadPlan.Node> nodes = new IdentityHashMap<>();
        for (Street st : all) {
            double total = st.length(), run = 0;
            for (int i = 0; i + 1 < st.pts.size(); i++) {
                Pt a = st.pts.get(i), b = st.pts.get(i + 1);
                double l = Math.hypot(b.x - a.x, b.z - a.z);
                boolean high = st == spine && town.highStreet && run + l / 2 > total * 0.25 && run + l / 2 < total * 0.75;
                RoadPlan.Node na = nodes.computeIfAbsent(a, p -> plan.addNode(Math.floor(p.x), Math.floor(p.z)));
                RoadPlan.Node nb = nodes.computeIfAbsent(b, p -> plan.addNode(Math.floor(p.x), Math.floor(p.z)));
                RoadPlan.Segment seg = plan.addSegment(na.id, nb.id, high ? TownLayout.kerbed(RoadStyle.busyHighStreet(), town) : st.style);
                seg.name = st.name;
                streetOf.put(seg, st);
                startOf.put(seg, run);
                run += l;
            }
            streets++;
        }
    }

    // ------------------------------------------------------------------ the buildings

    /** The town's ground as a grid: what roads, junctions and ground kept clear rule out, and what's taken. */
    private boolean[] blocked, taken;

    private int cell(int x, int z) {
        return x < town.x0 || z < town.z0 || x > town.x1 || z > town.z1 ? -1 : (z - town.z0) * town.width() + (x - town.x0);
    }

    private void block(double cx, double cz, double r) {
        for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
            for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
                int c = cell(x, z);
                if (c >= 0 && Math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r) {
                    blocked[c] = true;
                }
            }
        }
    }

    /** The plot from x0, z0 to x1, z1 is all free ground in the town. */
    private boolean free(int x0, int z0, int x1, int z1) {
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                int c = cell(x, z);
                if (c < 0 || blocked[c] || taken[c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void plots() {
        int size = town.width() * town.depth();
        blocked = new boolean[size];
        taken = new boolean[size];
        Layout lines = Layout.lines(plan);
        // every road's footprint (the plan's own roads through town too), and round each junction its corners
        for (RoadPlan.Segment seg : plan.segments) {
            Curve c = lines.curves.get(seg.id);
            if (c == null) {
                continue;
            }
            for (int i = 0; i < c.n; i += 2) {
                block(c.x[i], c.z[i], seg.style.halfOuter() + 1.2);
            }
        }
        for (RoadPlan.Node n : plan.nodes) {
            List<RoadPlan.Segment> at = plan.segmentsAt(n.id);
            if (at.size() >= 3) {
                RoadStyle w = at.get(0).style;
                for (RoadPlan.Segment s : at) {
                    w = s.style.halfOuter() > w.halfOuter() ? s.style : w;
                }
                block(n.x + 0.5, n.z + 0.5, w.halfCarriageway() + w.cornerRadius + w.pavement + 1);
            }
        }
        for (RoadPlan.Site k : kept) {
            for (int z = k.z0; z <= k.z1; z++) {
                for (int x = k.x0; x <= k.x1; x++) {
                    int c = cell(x, z);
                    if (c >= 0) {
                        blocked[c] = true;
                    }
                }
            }
        }
        for (RoadPlan.Site o : plan.sites) {
            if (o != town && o.type.ground()) {
                for (int z = o.z0; z <= o.z1; z++) {
                    for (int x = o.x0; x <= o.x1; x++) {
                        int c = cell(x, z);
                        if (c >= 0) {
                            taken[c] = true;
                        }
                    }
                }
            }
        }
        double total = spine.length();
        int[] civic = {0};
        for (Map.Entry<RoadPlan.Segment, Street> e : streetOf.entrySet()) {
            RoadPlan.Segment seg = e.getKey();
            Street st = e.getValue();
            Curve c = lines.curves.get(seg.id);
            if (c == null) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                // along each side of a street the same house six at a time; shops all different
                int pick = rand.nextInt(16), placed = 0;
                for (int i = 0; i < c.n; i += 4) {
                    double lx = c.leftX(i) * side, lz = c.leftZ(i) * side, off = seg.style.halfOuter() + 1;
                    double px = c.x[i] + lx * off, pz = c.z[i] + lz * off;
                    int front = Math.abs(lx) > Math.abs(lz) ? (lx > 0 ? 1 : 3) : (lz > 0 ? 2 : 0);
                    double f = (startOf.get(seg) + c.s(i)) / total;
                    RoadPlan.SiteType type = st != spine ? TownLayout.houses(town, ring(px, pz))
                            : town.highStreet && f > 0.25 && f < 0.75 ? RoadPlan.SiteType.PARADE
                            : town.civic && civic[0] < SiteLayout.CIVIC_UNITS.size() && (f > 0.1 && f <= 0.25 || f >= 0.75 && f < 0.9) ? RoadPlan.SiteType.CIVIC
                            : TownLayout.houses(town, 1);
                    List<String> units = SiteLayout.units(type);
                    int first = type == RoadPlan.SiteType.CIVIC ? civic[0] : type == RoadPlan.SiteType.PARADE ? pick + placed : pick + placed / 6;
                    for (int k = 0; k < units.size(); k++) {
                        int u = Math.floorMod(first + k, units.size());
                        if (plot(type, units.get(u), px, pz, front)) {
                            placed++;
                            if (type == RoadPlan.SiteType.CIVIC) {
                                civic[0]++;
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /** How far out from the main road (0 on it, 3 the edge of town), for which houses go where. */
    private int ring(double x, double z) {
        double wide = alongX ? town.depth() : town.width();
        double f = nearest(spine, x, z)[0] / (wide / 2);
        return f < 0.4 ? 1 : f < 0.75 ? 2 : 3;
    }

    /**
     * One building's plot with its front at (px, pz) facing the road on its `front` side, pushed
     * back off the road as far as it has to be (on a bend or a road at an angle, its near corner
     * would be in the road); whether there was room.
     */
    private boolean plot(RoadPlan.SiteType type, String unit, double px, double pz, int front) {
        PrefabCatalog.Entry e = PrefabCatalog.get(unit);
        if (e == null) {
            return false;
        }
        int w = e.width(), d = e.depth() + 1;
        for (int back = 0; back <= w / 2 + 3; back++) {
            int x0, z0, x1, z1;
            switch (front) {
                case 0 -> {                                     // the road to the south
                    x0 = (int) Math.round(px - w / 2.0);
                    z1 = (int) Math.floor(pz) - back;
                    x1 = x0 + w - 1;
                    z0 = z1 - d + 1;
                }
                case 2 -> {
                    x0 = (int) Math.round(px - w / 2.0);
                    z0 = (int) Math.floor(pz) + back;
                    x1 = x0 + w - 1;
                    z1 = z0 + d - 1;
                }
                case 1 -> {                                     // the road to the west
                    z0 = (int) Math.round(pz - w / 2.0);
                    x0 = (int) Math.floor(px) + back;
                    z1 = z0 + w - 1;
                    x1 = x0 + d - 1;
                }
                default -> {
                    z0 = (int) Math.round(pz - w / 2.0);
                    x1 = (int) Math.floor(px) - back;
                    z1 = z0 + w - 1;
                    x0 = x1 - d + 1;
                }
            }
            if (!free(x0, z0, x1, z1)) {
                continue;
            }
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    taken[cell(x, z)] = true;
                }
            }
            RoadPlan.Site st = plan.addSite(type, x0, z0, x1, z1);
            st.front = front;
            st.brand = SiteLayout.units(type).indexOf(unit);
            plots++;
            return true;
        }
        return false;
    }
}
