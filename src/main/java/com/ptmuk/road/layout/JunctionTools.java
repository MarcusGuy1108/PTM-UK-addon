package com.ptmuk.road.layout;

import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;

/**
 * UK touches made to a junction in the plan, as ordinary roads you can change afterwards:
 * flared approaches (each road widening to two lanes each way for its last stretch, narrowing
 * back with a taper) and left-turn filters (a one-way slip road off each road onto the next one on
 * its left, cutting the corner).
 */
public final class JunctionTools {
    /** How far out a flare starts, and a filter leaves and joins its roads. */
    static final double FLARE = 60, FILTER = 62;

    private JunctionTools() {
    }

    /** Two lanes each way up to the junction on every one-lane road into it; what was done. */
    public static String flare(RoadPlan plan, int nodeId) {
        RoadPlan.Node n = plan.node(nodeId);
        if (n == null) {
            return "Pick a junction";
        }
        int done = 0;
        for (RoadPlan.Segment s : plan.segmentsAt(nodeId)) {
            RoadPlan.Node o = plan.node(s.a == nodeId ? s.b : s.a);
            double len = Math.hypot(o.x - n.x, o.z - n.z);
            if (s.style.dual || s.style.oneWay || s.style.lanes >= 2 || len < FLARE + 50) {
                continue;
            }
            double t = FLARE / len;
            RoadPlan.Node p = plan.addNode(Math.floor(n.x + (o.x - n.x) * t), Math.floor(n.z + (o.z - n.z) * t));
            if (s.a == nodeId) {
                s.a = p.id;
            } else {
                s.b = p.id;
            }
            s.bend *= 1 - t;
            s.built = false;
            RoadStyle wide = s.style.copy();
            wide.lanes = 2;
            wide.laneWidth = 4;
            wide.name = s.style.name + " (two lanes)";
            RoadPlan.Segment near = plan.addSegment(nodeId, p.id, wide);
            near.name = s.name;
            done++;
        }
        n.built = false;
        return done == 0 ? "No one-lane roads long enough to flare here (they need " + (int) (FLARE + 50) + " blocks)"
                : "Flared " + done + " roads to two lanes each way at the junction. Press Build.";
    }

    /** A left-turn filter from each road onto the road on its left; what was done. */
    public static String filters(RoadPlan plan, int nodeId) {
        RoadPlan.Node n = plan.node(nodeId);
        if (n == null) {
            return "Pick a junction";
        }
        Layout l = Layout.lines(plan);
        var arms = plan.segmentsAt(nodeId);
        int done = 0;
        for (RoadPlan.Segment a : arms) {
            double[] oa = out(plan, n, a);
            if (oa == null || a.style.oneWay && a.a == nodeId) {
                continue;                                    // nothing comes in along a one-way road leaving here
            }
            double fx = -oa[0], fz = -oa[1], lx = fz, lz = -fx;    // coming in; its left
            for (RoadPlan.Segment b : arms) {
                double[] ob = out(plan, n, b);
                if (b == a || ob == null || b.style.oneWay && b.b == nodeId || ob[0] * lx + ob[1] * lz < 0.6) {
                    continue;                                // not the road on its left (or one coming in only)
                }
                double[] d = along(l, a, nodeId, FILTER), m = along(l, b, nodeId, FILTER);
                if (d == null || m == null) {
                    continue;
                }
                // off the kerb side of the way in, onto the kerb side of the way out
                double ha = a.style.halfCarriageway() - 1.5, hb = b.style.halfCarriageway() - 1.5;
                RoadPlan.Node off = plan.addNode(Math.floor(d[0] + lx * ha), Math.floor(d[1] + lz * ha));
                RoadPlan.Node on = plan.addNode(Math.floor(m[0] + ob[1] * hb), Math.floor(m[1] - ob[0] * hb));
                off.junction = on.junction = RoadPlan.Junction.MERGE;
                RoadStyle slip = RoadStyle.residential();
                slip.name = "Left turn filter";
                slip.oneWay = true;
                slip.lanes = 1;
                slip.laneWidth = 4;
                slip.yellow = RoadStyle.Yellow.DOUBLE;
                slip.pavement = 2;
                RoadPlan.Segment f = plan.addSegment(off.id, on.id, slip);
                f.bend = 10;                                 // bowed out from the corner (+ is its left)
                done++;
            }
        }
        return done == 0 ? "No left turns here with room for a filter (the roads need " + (int) (FILTER + 30) + " blocks)"
                : "Added " + done + " left turn filters. Press Build.";
    }

    /** The way a road leaves node n (straight towards its other end), or null if it's too short for a filter. */
    private static double[] out(RoadPlan plan, RoadPlan.Node n, RoadPlan.Segment s) {
        RoadPlan.Node o = plan.node(s.a == n.id ? s.b : s.a);
        double dx = o.x - n.x, dz = o.z - n.z, len = Math.hypot(dx, dz);
        return len < FILTER + 30 ? null : new double[]{dx / len, dz / len};
    }

    /** The point on a road's centre line `dist` out from node id along it (its curve, bends and all). */
    private static double[] along(Layout l, RoadPlan.Segment s, int id, double dist) {
        Curve c = l.curves.get(s.id);
        if (c == null || c.length < dist + 10) {
            return null;
        }
        int i = c.index(s.a == id ? dist : c.length - dist);
        return new double[]{c.x[i] - 0.5, c.z[i] - 0.5};
    }
}
