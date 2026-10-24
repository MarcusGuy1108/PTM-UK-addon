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
 * side of them, houses everywhere else (terraces near the main road, semis, then detached). Last,
 * streets are run into whatever big gaps are left. Traffic lights go where streets cross the main
 * road; a big town gets more of them, mini roundabouts, deeper back streets and main roads out.
 * A town drawn as an outline fills that shape: its main road runs down the middle of it the long
 * way (whichever way that is), and nothing goes outside it.
 */
final class WindingTown {
    private static final double STEP_MIN = 30, STEP_MAX = 44;
    /** Behind the pavement on each side: room for a house and its garden. */
    private static final double ROW = 26;
    /** Junctions along a street at least this far apart, and from its ends. */
    private static final double JUNCTION_GAP = 34;
    /** A plain point of a street this near where a junction would go becomes the junction. */
    private static final double SNAP = 10;
    /** Traffic lights along a big town's main road about this far apart at least. */
    private static final double SIGNALS_APART = 170;

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
    /** The town's own frame: (a, c), along it the long way and across it, is the world point o + a u + c v. */
    private final double ox, oz, ux, uz, vx, vz, len, wide;
    /** A drawn town: how far each block of its box is inside the outline (0 outside it); null for a plain box. */
    private final double[] room;
    private final RoadStyle res;
    private final List<Street> all = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private final List<RoadPlan.Site> kept;
    /** What crowds() says when it's ground kept clear in the way. */
    private static final Street KEPT = new Street(RoadStyle.residential(), -1);
    private Street spine;
    int streets, plots;
    /** False when a drawn outline has nowhere wide enough for the main road and its houses (nothing is laid then). */
    boolean fits = true;

    WindingTown(RoadPlan plan, RoadPlan.Site town, RandomSource rand) {
        this.plan = plan;
        this.town = town;
        this.rand = rand;
        this.res = TownLayout.kerbed(RoadStyle.residential(), town);
        this.kept = TownLayout.kept(plan, town);
        if (town.outline == null) {
            boolean alongX = town.width() >= town.depth();
            ox = town.x0;
            oz = town.z0;
            ux = alongX ? 1 : 0;
            uz = alongX ? 0 : 1;
            vx = uz;
            vz = ux;
            len = alongX ? town.width() : town.depth();
            wide = alongX ? town.depth() : town.width();
            room = null;
            return;
        }
        // the long way is the outline's main axis (its points are spaced evenly round it, so they weigh it fairly)
        int[] o = town.outline;
        int n = o.length / 2;
        double cx = 0, cz = 0;
        for (int i = 0; i < n; i++) {
            cx += o[2 * i];
            cz += o[2 * i + 1];
        }
        cx /= n;
        cz /= n;
        double sxx = 0, szz = 0, sxz = 0;
        for (int i = 0; i < n; i++) {
            double dx = o[2 * i] - cx, dz = o[2 * i + 1] - cz;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }
        double th = 0.5 * Math.atan2(2 * sxz, sxx - szz);
        ux = Math.cos(th);
        uz = Math.sin(th);
        vx = -uz;
        vz = ux;
        double amin = Double.MAX_VALUE, amax = -Double.MAX_VALUE, cmin = Double.MAX_VALUE, cmax = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double dx = o[2 * i] - cx, dz = o[2 * i + 1] - cz, a = dx * ux + dz * uz, c = dx * vx + dz * vz;
            amin = Math.min(amin, a);
            amax = Math.max(amax, a);
            cmin = Math.min(cmin, c);
            cmax = Math.max(cmax, c);
        }
        ox = cx + amin * ux + cmin * vx;
        oz = cz + amin * uz + cmin * vz;
        len = amax - amin;
        wide = cmax - cmin;
        // how far in each block is: the distance to the nearest block outside (round the box, everything is)
        int w = town.width(), h = town.depth(), pw = w + 2;
        boolean[] in = town.mask(), out = new boolean[pw * (h + 2)];
        java.util.Arrays.fill(out, true);
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                out[(z + 1) * pw + x + 1] = !in[z * w + x];
            }
        }
        Distance d = Distance.of(out, pw, h + 2);
        room = new double[w * h];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                room[z * w + x] = d.dist[(z + 1) * pw + x + 1];
            }
        }
    }

    void run() {
        spine();
        if (!fits) {
            return;
        }
        // side streets off the main road, then shorter ones off those (and in a big town off those too)
        int levels = spine.length() >= 2 * TownLayout.BIG ? 4 : spine.length() >= TownLayout.BIG ? 3 : 2;
        for (int level = 1; level <= levels; level++) {
            for (Street parent : new ArrayList<>(all)) {
                if (parent.level != level - 1) {
                    continue;
                }
                // off alternate sides, so each side's are a pair of gardens apart; now and then a crossroads.
                // Streets off side streets start far enough out for houses between them and the main road
                double len = parent.length();
                int side = rand.nextBoolean() ? 1 : -1;
                double first = level == 1 ? JUNCTION_GAP + 10 + rand.nextDouble() * 20
                        : (level == 2 ? spine.style : res).halfOuter() + res.halfOuter() + 2 * ROW + 10 + rand.nextDouble() * 10;
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
        fill();
        roads();
        plots();
    }

    /**
     * Streets into the big gaps the others left (where side streets fanned apart): off the street
     * nearest the biggest gap, towards it, again and again until no gap is left that one fits.
     */
    private void fill() {
        int cell = 6, gw = town.width() / cell + 1, gh = town.depth() / cell + 1;
        double gap = 2 * res.halfOuter() + 2 * ROW;
        boolean[] failed = new boolean[gw * gh];
        for (int tries = 0; tries < 400; tries++) {
            boolean[] road = new boolean[gw * gh];
            for (Street st : all) {
                for (int i = 0; i + 1 < st.pts.size(); i++) {
                    Pt p = st.pts.get(i), q = st.pts.get(i + 1);
                    int n = (int) Math.ceil(Math.hypot(q.x - p.x, q.z - p.z) / (cell / 2.0)) + 1;
                    for (int k = 0; k <= n; k++) {
                        int cx = (int) ((p.x + (q.x - p.x) * k / n - town.x0) / cell), cz = (int) ((p.z + (q.z - p.z) * k / n - town.z0) / cell);
                        if (cx >= 0 && cz >= 0 && cx < gw && cz < gh) {
                            road[cz * gw + cx] = true;
                        }
                    }
                }
            }
            // how far each cell is from a street: straight across a box, the way round inside a drawn outline
            int[] from = room == null ? null : new int[gw * gh];
            double[] dist = room == null ? Distance.of(road, gw, gh).dist : wayRound(road, gw, gh, cell, from);
            int best = -1;
            for (int i = 0; i < gw * gh; i++) {
                double x = town.x0 + (i % gw + 0.5) * cell, z = town.z0 + (i / gw + 0.5) * cell;
                if (failed[i] || !inside(x, z, ROW) || keptAt(x, z) || Double.isInfinite(dist[i])) {
                    continue;
                }
                if (best < 0 || dist[i] > dist[best]) {
                    best = i;
                }
            }
            if (best < 0 || dist[best] * cell < gap) {
                return;
            }
            double x = town.x0 + (best % gw + 0.5) * cell, z = town.z0 + (best / gw + 0.5) * cell;
            Street near = null;
            double[] q = null;
            for (Street st : all) {
                double[] n = nearest(st, x, z);
                if (q == null || n[0] < q[0]) {
                    q = n;
                    near = st;
                }
            }
            At a = at(near, q[1]);
            int side = (x - a.x()) * a.tz() - (z - a.z()) * a.tx() > 0 ? 1 : -1;     // the gap's side of it (left is tz, -tx)
            // a close that stops short of it carries on into it; otherwise a street off the nearest one, from as near
            // it as there's room for a junction
            int before = all.size();
            double len = near.length();
            if (q[1] > len - 1 && extend(near, x, z)) {
                continue;
            }
            for (int k = 0; k < 9 && all.size() == before; k++) {
                double ds = (k + 1) / 2 * 16.0 * (k % 2 == 0 ? 1 : -1);
                branch(near, Math.max(JUNCTION_GAP + 1, Math.min(len - JUNCTION_GAP - 1, q[1] + ds)), side, Math.max(2, near.level + 1), false);
            }
            if (all.size() == before && from != null) {
                wayStreet(best, from, gw, cell);             // round a corner of the outline: along the way there
            }
            if (all.size() == before) {
                int r = (int) Math.ceil(gap / cell);
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int cx = best % gw + dx, cz = best / gw + dz;
                        if (cx >= 0 && cz >= 0 && cx < gw && cz < gh && dx * dx + dz * dz <= r * r) {
                            failed[cz * gw + cx] = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * A drawn town: how far each cell is from a street going round inside the outline (cells, not
     * blocks; infinite where it can't be reached), and in `from` the next cell on the way back to
     * the street (-1 on one). The last stretch along the edge costs more, so ways keep off it.
     */
    private double[] wayRound(boolean[] road, int gw, int gh, int cell, int[] from) {
        double[] dist = new double[gw * gh];
        java.util.Arrays.fill(dist, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(from, -1);
        java.util.PriorityQueue<double[]> open = new java.util.PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        for (int i = 0; i < gw * gh; i++) {
            if (road[i]) {
                dist[i] = 0;
                open.add(new double[]{0, i});
            }
        }
        double edge = res.halfOuter() + ROW * 0.7;
        while (!open.isEmpty()) {
            double[] top = open.poll();
            int i = (int) top[1];
            if (top[0] > dist[i]) {
                continue;
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = i % gw + dx, cz = i / gw + dz, j = cz * gw + cx;
                    if ((dx == 0 && dz == 0) || cx < 0 || cz < 0 || cx >= gw || cz >= gh) {
                        continue;
                    }
                    double x = town.x0 + (cx + 0.5) * cell, z = town.z0 + (cz + 0.5) * cell;
                    if (!inside(x, z, 1)) {
                        continue;
                    }
                    double d = top[0] + (dx != 0 && dz != 0 ? Math.sqrt(2) : 1) * (inside(x, z, edge) ? 1 : 4);
                    if (d < dist[j]) {
                        dist[j] = d;
                        from[j] = i;
                        open.add(new double[]{d, j});
                    }
                }
            }
        }
        return dist;
    }

    /**
     * A street to the cell `best` from the street nearest it the way round inside a drawn town's
     * outline, along that way: on from the end of a close there, or off the street as a side road.
     */
    private void wayStreet(int best, int[] from, int gw, int cell) {
        List<double[]> way = new ArrayList<>();
        for (int i = best; i >= 0; i = from[i]) {
            way.add(new double[]{town.x0 + (i % gw + 0.5) * cell, town.z0 + (i / gw + 0.5) * cell});
        }
        java.util.Collections.reverse(way);                     // from the street to the gap
        if (way.size() < 4) {
            return;
        }
        List<double[]> pts = new ArrayList<>();
        for (int k = 5; k < way.size() - 2; k += 5) {
            pts.add(way.get(k));
        }
        pts.add(way.get(way.size() - 1));
        double[] start = way.get(0);
        Street near = null;
        double[] q = null;
        for (Street st : all) {
            double[] n = nearest(st, start[0], start[1]);
            if (q == null || n[0] < q[0]) {
                q = n;
                near = st;
            }
        }
        double len = near.length();
        Pt end = q[1] < len / 2 ? near.pts.get(0) : near.pts.get(near.pts.size() - 1);
        boolean onEnd = near != spine && !end.junction && !end.out && Math.hypot(end.x - start[0], end.z - start[1]) < SNAP + cell;
        double s = onEnd ? (q[1] < len / 2 ? 0 : len) : q[1];
        if (!onEnd) {
            // a junction where there's room for one, as near as can be
            double found = Double.NaN;
            for (int k = 0; k < 9 && Double.isNaN(found); k++) {
                double t = q[1] + (k + 1) / 2 * 16.0 * (k % 2 == 0 ? 1 : -1);
                if (room(near, t, false)) {
                    found = t;
                }
            }
            if (Double.isNaN(found)) {
                return;
            }
            s = found;
        }
        At leave = at(near, s);
        int sideSign = side(leave, pts.get(0)[0], pts.get(0)[1]) > 0 ? 1 : -1;
        List<double[]> kept = new ArrayList<>();
        double[] cur = {leave.x(), leave.z()};
        for (double[] p : pts) {
            if (crowds(cur[0], cur[1], p[0], p[1], res, near, leave, sideSign, null) != null) {
                break;
            }
            kept.add(p);
            cur = p;
        }
        double length = 0;
        double[] prev = {leave.x(), leave.z()};
        for (double[] p : kept) {
            length += Math.hypot(p[0] - prev[0], p[1] - prev[1]);
            prev = p;
        }
        if (length < STEP_MIN) {
            return;
        }
        if (onEnd) {
            for (double[] p : kept) {
                if (end == near.pts.get(0)) {
                    near.pts.add(0, new Pt(p[0], p[1]));
                } else {
                    near.pts.add(new Pt(p[0], p[1]));
                }
            }
            return;
        }
        Street st = new Street(res, Math.max(2, near.level + 1));
        st.name = StreetNames.pick(rand, names);
        st.pts.add(join(near, s));
        for (double[] p : kept) {
            st.pts.add(new Pt(p[0], p[1]));
        }
        all.add(st);
    }

    /** Carry a close on from its end, bending towards (gx, gz), until it comes near another street or the edge; whether it went on at all. */
    private boolean extend(Street st, double gx, double gz) {
        Pt end = st.pts.get(st.pts.size() - 1), prev = st.pts.get(st.pts.size() - 2);
        if (st == spine || end.junction || end.out) {
            return false;
        }
        double h = Math.atan2(end.z - prev.z, end.x - prev.x);
        if (Math.cos(Math.atan2(gz - end.z, gx - end.x) - h) < 0.3) {
            return false;                                    // it's not ahead
        }
        At leave = new At(end.x, end.z, Math.cos(h), Math.sin(h), st.pts.size() - 2, st.length());
        int added = 0;
        for (int k = 0; k < 4; k++) {
            Pt cur = st.pts.get(st.pts.size() - 1);
            double turn = Math.atan2(Math.sin(Math.atan2(gz - cur.z, gx - cur.x) - h), Math.cos(Math.atan2(gz - cur.z, gx - cur.x) - h));
            h += Math.max(-0.35, Math.min(0.35, turn * 0.5 + (rand.nextDouble() - 0.5) * 0.3));
            double step = STEP_MIN + rand.nextDouble() * (STEP_MAX - STEP_MIN), nx = cur.x + Math.cos(h) * step, nz = cur.z + Math.sin(h) * step;
            if (!inside(nx, nz, res.halfOuter() + ROW * 0.7) || crowds(cur.x, cur.z, nx, nz, res, st, leave, 0, null) != null) {
                break;
            }
            st.pts.add(new Pt(nx, nz));
            added++;
        }
        return added > 0;
    }

    private boolean keptAt(double x, double z) {
        return kept.stream().anyMatch(k -> x >= k.x0 && x <= k.x1 + 1 && z >= k.z0 && z <= k.z1 + 1);
    }

    // ------------------------------------------------------------------ the main road

    /** Town coordinates (along its length, across it) to the world. */
    private double wx(double a, double c) {
        return ox + a * ux + c * vx;
    }

    private double wz(double a, double c) {
        return oz + a * uz + c * vz;
    }

    /**
     * The middle of the town across it at a, and how wide it is there: the whole width of a box;
     * for a drawn town, the stretch across the outline that c is in (or the widest), null if none.
     */
    private double[] across(double a, double c) {
        if (room == null) {
            return new double[]{wide / 2, wide};
        }
        double[] best = null;
        double start = Double.NaN;
        for (double cc = 0; cc <= wide + 2; cc += 2) {
            boolean in = cc <= wide && inside(wx(a, cc), wz(a, cc), 1);
            if (in && Double.isNaN(start)) {
                start = cc;
            } else if (!in && !Double.isNaN(start)) {
                double[] span = {(start + cc - 2) / 2, cc - 2 - start};
                boolean holds = c >= start && c <= cc - 2;
                if (best == null || holds || best[2] == 0 && span[1] > best[1]) {
                    best = new double[]{span[0], span[1], holds ? 1 : 0};
                }
                start = Double.NaN;
            }
        }
        return best;
    }

    private void spine() {
        spine = new Street(TownLayout.kerbed(town.mainRoad ? RoadStyle.mainRoad() : RoadStyle.residential(), town), 0);
        spine.name = town.highStreet ? "High Street" : StreetNames.pick(rand, names);
        names.add("High Street");
        // a drawn town's main road runs between where the shape is first and last wide enough for it
        double a0 = 0, a1 = len - 1, minWide = 2 * (res.halfOuter() + ROW);
        if (room != null) {
            while (a0 < len && (across(a0, wide / 2) == null || across(a0, wide / 2)[1] < minWide)) {
                a0 += 2;
            }
            while (a1 > a0 && (across(a1, wide / 2) == null || across(a1, wide / 2)[1] < minWide)) {
                a1 -= 2;
            }
        }
        if (room != null && a1 - a0 < STEP_MIN) {
            fits = false;
            return;
        }
        double[] mid0 = across(a0, wide / 2);
        if (mid0 == null) {
            mid0 = new double[]{wide / 2, wide};
        }
        double a = a0, c = mid0[0] + (rand.nextDouble() - 0.5) * mid0[1] * 0.16, heading = (rand.nextDouble() - 0.5) * 0.4;
        spine.pts.add(new Pt(wx(a, c), wz(a, c)));
        while (a < a1) {
            double step = STEP_MIN + rand.nextDouble() * (STEP_MAX - STEP_MIN);
            heading += (rand.nextDouble() - 0.5) * 0.35;
            // drift back towards the middle, never far off the long way
            double[] mid = across(a, c);
            if (mid == null) {
                break;
            }
            heading -= Math.signum(c - mid[0]) * Math.max(0, Math.abs(c - mid[0]) - mid[1] * 0.12) / mid[1];
            heading = Math.max(-0.38, Math.min(0.38, heading));
            double na, nc;
            if (a + step * Math.cos(heading) > a1 - STEP_MIN * 0.6) {
                nc = c + (a1 - a) * Math.tan(heading);
                na = a1;
            } else {
                na = a + step * Math.cos(heading);
                nc = c + step * Math.sin(heading);
            }
            if (room != null && !inside(wx(na, nc), wz(na, nc), res.halfOuter() + 2)) {
                break;                                        // a neck in the outline: the main road ends here
            }
            a = na;
            c = nc;
            spine.pts.add(new Pt(wx(a, c), wz(a, c)));
        }
        if (room != null) {
            // run on to the edge of the outline at both ends, ready to carry on out of town
            for (int end = 0; end < 2; end++) {
                Pt tip = end == 0 ? spine.pts.get(0) : spine.pts.get(spine.pts.size() - 1);
                Pt next = end == 0 ? spine.pts.get(Math.min(1, spine.pts.size() - 1)) : spine.pts.get(Math.max(0, spine.pts.size() - 2));
                double dx = tip.x - next.x, dz = tip.z - next.z, l = Math.hypot(dx, dz);
                if (l < 1e-6) {
                    dx = end == 0 ? -ux : ux;
                    dz = end == 0 ? -uz : uz;
                    l = 1;
                }
                double t = 0;
                while (t < 200 && inside(tip.x + dx / l * (t + 1), tip.z + dz / l * (t + 1), spine.style.halfOuter())) {   // the whole road stays inside
                    t++;
                }
                if (t >= 6) {
                    Pt p = new Pt(tip.x + dx / l * t, tip.z + dz / l * t);
                    p.out = true;
                    spine.pts.add(end == 0 ? 0 : spine.pts.size(), p);
                }
            }
        }
        all.add(spine);
    }

    // ------------------------------------------------------------------ side streets

    private boolean inside(double x, double z, double margin) {
        if (room == null) {
            return x >= town.x0 + margin && x <= town.x1 + 1 - margin && z >= town.z0 + margin && z <= town.z1 + 1 - margin;
        }
        int cx = (int) Math.floor(x) - town.x0, cz = (int) Math.floor(z) - town.z0;
        return cx >= 0 && cz >= 0 && cx < town.width() && cz < town.depth() && room[cz * town.width() + cx] >= Math.max(margin, 1);
    }

    /** Where the way from (x0, z0) towards (x1, z1) crosses the edge of town (if it's far enough to be worth going), or null. */
    private double[] leaves(double x0, double z0, double x1, double z1) {
        if (room != null) {
            // a drawn town's edge: the last point along the way with the whole road still inside the outline
            double l = Math.hypot(x1 - x0, z1 - z0), t = 0;
            while (t + 1 <= l && inside(x0 + (x1 - x0) * (t + 1) / l, z0 + (z1 - z0) * (t + 1) / l, res.halfOuter())) {
                t++;
            }
            return t < 10 ? null : new double[]{x0 + (x1 - x0) * t / l, z0 + (z1 - z0) * t / l};
        }
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
        // a side street off the main road can reach the edge of town; the rest are shorter
        int steps = level == 1 ? Math.max(6, (int) Math.ceil(wide / 2 / STEP_MIN)) : level == 2 && wide > 500 ? 5 : 3;
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
                RoadPlan.Segment seg = plan.addSegment(na.id, nb.id, high ? TownLayout.kerbed(RoadStyle.busyHighStreet(), town)
                        : through(st) ? TownLayout.kerbed(RoadStyle.mainRoad(), town) : st.style);
                seg.name = st.name;
                streetOf.put(seg, st);
                startOf.put(seg, run);
                run += l;
            }
            streets++;
        }
        // traffic lights on the main road: a small town's crossroads nearest the middle; a big town's crossroads and
        // then its longest side streets, one set every SIGNALS_APART or so. Mini roundabouts where back streets cross
        boolean big = spine.length() >= TownLayout.BIG;
        Map<Pt, Double> side = new IdentityHashMap<>();
        for (Street st : all) {
            if (st.level == 1) {
                side.merge(st.pts.get(0), st.length() + (plan.segmentsAt(nodes.get(st.pts.get(0)).id).size() >= 4 ? 10000 : 0), Math::max);
            } else if (big && st.level == 2 && plan.segmentsAt(nodes.get(st.pts.get(0)).id).size() >= 4) {
                nodes.get(st.pts.get(0)).junction = RoadPlan.Junction.MINI_ROUNDABOUT;
            }
        }
        List<Pt> lights = new ArrayList<>(side.keySet());
        double mid = spine.length() / 2;
        lights.sort((a, b) -> big ? Double.compare(side.get(b), side.get(a))
                : Double.compare(Math.abs(along(spine, spine.pts.indexOf(a)) - mid), Math.abs(along(spine, spine.pts.indexOf(b)) - mid)));
        List<Double> chosen = new ArrayList<>();
        for (Pt p : lights) {
            double s = along(spine, spine.pts.indexOf(p));
            if (big ? chosen.stream().allMatch(c -> Math.abs(c - s) >= SIGNALS_APART) : chosen.isEmpty() && side.get(p) >= 10000) {
                chosen.add(s);
                nodes.get(p).junction = RoadPlan.Junction.SIGNALS;
            }
        }
    }

    /** In a big town, a long side street off the main road that runs on out of town: a main road itself. */
    private boolean through(Street st) {
        return st.level == 1 && spine.length() >= TownLayout.BIG && st.style != spine.style && st.length() >= wide * 0.3
                && st.pts.get(st.pts.size() - 1).out;
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
        for (int c = 0; room != null && c < size; c++) {
            blocked[c] |= room[c] < 1;                       // outside a drawn town
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
