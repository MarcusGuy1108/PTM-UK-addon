package com.ptmuk.road.layout;

import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * A motorway junction made where a road meets a motorway (or any dual carriageway): the motorway
 * goes under (Segment.under) and everything else is raised over it on bridges, with a slip road off
 * and on for each way, joining it with tapers. Three layouts:
 * <ul>
 * <li>a two-bridge roundabout over the motorway, the slips and the road joining it;</li>
 * <li>an oval roundabout: stretched along the road across the motorway, its straight sides the
 * two bridges, the road and the slips joining its ends either side;</li>
 * <li>a dumbbell: a roundabout each side, joined by one bridge, the slips joining the one on their side;</li>
 * <li>a diamond: traffic lights each side where the slips meet the road, joined by one bridge.</li>
 * </ul>
 * Auto picks one to suit: a big road square on gets the big roundabout or the oval, a big road
 * at a slant the oval (it lies along the road), a minor road the dumbbell, and where there's
 * little room (other roads or ground kept clear close by) the compact diamond; between those it
 * varies from junction to junction.
 */
public final class Interchange {
    public enum Kind {
        AUTO("auto"), ROUNDABOUT("roundabout over it"), OVAL("oval roundabout"), DUMBBELL("dumbbell"), DIAMOND("diamond (lights)");

        public final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    /** Where the slips leave and join the motorway, beyond the junction itself. */
    private static final double SLIP_RUN = 110;

    private Interchange() {
    }

    /** Turn the junction at a node into a motorway junction; what was made, or why not. */
    public static String make(RoadPlan plan, int nodeId, Kind kind) {
        RoadPlan.Node n = plan.node(nodeId);
        if (n == null) {
            return "Pick the point where the road meets the motorway";
        }
        List<RoadPlan.Segment> arms = plan.segmentsAt(nodeId);
        // the motorway: the pair of arms most nearly straight through, dual carriageways first
        RoadPlan.Segment ma = null, mb = null;
        double best = -1e9;
        for (int i = 0; i < arms.size(); i++) {
            for (int j = i + 1; j < arms.size(); j++) {
                double[] a = away(plan, n, arms.get(i)), b = away(plan, n, arms.get(j));
                double score = -(a[0] * b[0] + a[1] * b[1]) + (arms.get(i).style.dual ? 1 : 0) + (arms.get(j).style.dual ? 1 : 0);
                if (score > best) {
                    best = score;
                    ma = arms.get(i);
                    mb = arms.get(j);
                }
            }
        }
        if (ma == null || arms.size() < 3 || !(ma.style.dual || mb.style.dual)) {
            return "A motorway junction goes where a road meets a dual carriageway: pick a point on one with a road off it";
        }
        List<RoadPlan.Segment> local = new ArrayList<>(arms);
        local.remove(ma);
        local.remove(mb);
        RoadStyle motorway = (ma.style.dual ? ma : mb).style;
        double[] da = away(plan, n, ma);
        double ux = -da[0], uz = -da[1];                       // along the motorway, from ma's side to mb's
        double lx = uz, lz = -ux;                              // the left of that way (where its traffic keeps)
        double[] dl = away(plan, n, local.get(0));
        double skew = Math.abs(dl[0] * ux + dl[1] * uz);       // 0 square on, 1 along it
        boolean big = local.stream().anyMatch(s -> s.style.dual || s.style.lanes >= 2 || s.style.laneWidth >= 6);
        boolean tight = crowded(plan, n, ma, mb, local);
        if (kind == Kind.AUTO) {
            kind = tight ? Kind.DIAMOND : !big && Math.floorMod(nodeId, 3) != 0 ? Kind.DUMBBELL
                    : skew > 0.5 ? (big ? Kind.OVAL : Kind.DUMBBELL)
                    : switch (Math.floorMod(nodeId, 4)) {
                        case 0 -> Kind.DIAMOND;
                        case 1 -> Kind.OVAL;
                        default -> Kind.ROUNDABOUT;
                    };
        }
        if (kind == Kind.OVAL && skew > 0.87) {
            return "The road runs too nearly along the motorway for an oval roundabout across it";
        }
        double cHalf = motorway.halfOuter();
        double ovalR = 24, sinA = Math.sqrt(Math.max(0, 1 - skew * skew));
        // the oval's straight sides reach over the motorway (further where the road crosses it at a slant)
        double ovalE = (cHalf + 12) / Math.max(0.5, sinA);
        double reach = (kind == Kind.ROUNDABOUT ? radius(cHalf) : kind == Kind.OVAL ? ovalR + ovalE * skew : 20) + 46, run = reach + SLIP_RUN;
        if (length(plan, n, ma) < run + 30 || length(plan, n, mb) < run + 30) {
            return "The motorway needs to run on at least " + (int) (run + 30) + " blocks each way from the junction for its slip roads";
        }
        // the motorway: its stretch through the junction goes under; its two sides end where that starts
        RoadPlan.Node m1 = plan.addNode(n.x - ux * reach, n.z - uz * reach), m2 = plan.addNode(n.x + ux * reach, n.z + uz * reach);
        reattach(ma, nodeId, m1.id);
        reattach(mb, nodeId, m2.id);
        RoadPlan.Segment through = plan.addSegment(m1.id, m2.id, motorway);
        through.under = true;
        through.name = ma.name;
        RoadStyle slip = RoadStyle.slipRoad();
        double edge = motorway.halfCarriageway() - 2;       // on the carriageway near its edge: a slip road merge
        String made;
        if (kind == Kind.ROUNDABOUT) {
            double r = radius(cHalf);
            n.junction = RoadPlan.Junction.ROUNDABOUT;
            n.radius = (int) r;
            n.y = Double.NaN;
            // the slips meet the ring between the bridges and the road, a quarter each side of the motorway
            double along = r + 20, side = r * 1.3;
            slips(plan, n.id, n.id, n.id, n.id, n, ux, uz, lx, lz, run, edge, along, side, slip);
            made = "a roundabout over the motorway (two bridges)";
        } else if (kind == Kind.OVAL) {
            // stretched along the road: its ends either side of the motorway, its straight sides the bridges
            double ax = dl[0] * lx + dl[1] * lz >= 0 ? dl[0] : -dl[0], az = dl[0] * lx + dl[1] * lz >= 0 ? dl[1] : -dl[1];
            n.junction = RoadPlan.Junction.ROUNDABOUT;
            n.radius = (int) ovalR;
            n.stretch = ovalE;
            n.axis = Math.atan2(az, ax);
            n.y = Double.NaN;
            // each slip comes in to the end on its side, a little to the side of the road there
            double out = ovalE + ovalR + 20, aside = ovalR;
            double[][] bends = {{ax * out - ux * aside, az * out - uz * aside}, {ax * out + ux * aside, az * out + uz * aside},
                    {-ax * out + ux * aside, -az * out + uz * aside}, {-ax * out - ux * aside, -az * out - uz * aside}};
            boolean[] offs = {true, false, true, false};
            double[][] merges = {{-run, edge}, {run, edge}, {run, -edge}, {-run, -edge}};
            for (int k = 0; k < 4; k++) {
                slip(plan, n, merges[k][0], merges[k][1], bends[k][0] * ux + bends[k][1] * uz, bends[k][0] * lx + bends[k][1] * lz, n.id,
                        ux, uz, lx, lz, slip, offs[k]);
            }
            made = "an oval roundabout over the motorway (two bridges)";
        } else {
            // a junction each side of the motorway joined by a bridge; each road off goes to the one on its side
            double off = cHalf + 30;
            RoadPlan.Node left = plan.addNode(n.x + lx * off, n.z + lz * off), right = plan.addNode(n.x - lx * off, n.z - lz * off);
            RoadPlan.Junction j = kind == Kind.DUMBBELL ? RoadPlan.Junction.ROUNDABOUT : RoadPlan.Junction.SIGNALS;
            left.junction = right.junction = j;
            left.radius = right.radius = kind == Kind.DUMBBELL ? 16 : 0;
            RoadStyle link = local.get(0).style.dual ? RoadStyle.mainRoad() : local.get(0).style;
            plan.addSegment(left.id, right.id, link).name = local.get(0).name;
            for (RoadPlan.Segment s : local) {
                double[] d = away(plan, n, s);
                reattach(s, nodeId, d[0] * lx + d[1] * lz >= 0 ? left.id : right.id);
            }
            plan.nodes.remove(n);
            double along = 34, side = off;
            slips(plan, left.id, left.id, right.id, right.id, n, ux, uz, lx, lz, run, edge, along, side, slip);
            made = kind == Kind.DUMBBELL ? "a dumbbell (a roundabout each side of the motorway)" : "a diamond (traffic lights each side of the motorway)";
        }
        return "Made " + made + ". Press Build.";
    }

    /** The ring's outer radius: wide enough that the slips meet it clear of the motorway under it. */
    private static double radius(double motorwayHalf) {
        return Math.max(30, Math.round(motorwayHalf * 2.2));
    }

    /**
     * The four slip roads: off before the junction and on after it, each way, from and to the
     * motorway's carriageway on that way's side, bending out and up to the junction (offA, onA on
     * the left of the way along, offB, onB the other side).
     */
    private static void slips(RoadPlan plan, int offA, int onA, int offB, int onB, RoadPlan.Node n, double ux, double uz, double lx, double lz,
            double run, double edge, double along, double side, RoadStyle slip) {
        // way along (+u), keeping to its left: off on the -u side, on at the +u side
        slip(plan, n, -run, edge, -along, side, offA, ux, uz, lx, lz, slip, true);
        slip(plan, n, run, edge, along, side, onA, ux, uz, lx, lz, slip, false);
        // the other way (-u), keeping to the right of +u: off on the +u side, on at the -u side
        slip(plan, n, run, -edge, along, -side, offB, ux, uz, lx, lz, slip, true);
        slip(plan, n, -run, -edge, -along, -side, onB, ux, uz, lx, lz, slip, false);
    }

    private static void slip(RoadPlan plan, RoadPlan.Node n, double a0, double c0, double a1, double c1, int to, double ux, double uz,
            double lx, double lz, RoadStyle slip, boolean off) {
        RoadPlan.Node merge = plan.addNode(Math.floor(n.x + ux * a0 + lx * c0), Math.floor(n.z + uz * a0 + lz * c0));
        merge.junction = RoadPlan.Junction.MERGE;
        RoadPlan.Node bend = plan.addNode(n.x + ux * a1 + lx * c1, n.z + uz * a1 + lz * c1);
        if (off) {
            plan.addSegment(merge.id, bend.id, slip);
            plan.addSegment(bend.id, to, slip);
        } else {
            plan.addSegment(to, bend.id, slip);
            plan.addSegment(bend.id, merge.id, slip);
        }
    }

    /** Whether other roads or ground kept clear are close enough that a big junction wouldn't fit. */
    private static boolean crowded(RoadPlan plan, RoadPlan.Node n, RoadPlan.Segment ma, RoadPlan.Segment mb, List<RoadPlan.Segment> local) {
        double room = 110;
        for (RoadPlan.Node o : plan.nodes) {
            boolean mine = o.id == n.id || o.id == other(ma, n.id) || o.id == other(mb, n.id)
                    || local.stream().anyMatch(s -> other(s, n.id) == o.id);
            if (!mine && Math.hypot(o.x - n.x, o.z - n.z) < room) {
                return true;
            }
        }
        for (RoadPlan.Site st : plan.sites) {
            double cx = Math.max(st.x0, Math.min(st.x1, n.x)), cz = Math.max(st.z0, Math.min(st.z1, n.z));
            if (st.type != RoadPlan.SiteType.GREEN && st.type != RoadPlan.SiteType.DISTRICT && Math.hypot(cx - n.x, cz - n.z) < room) {
                return true;
            }
        }
        return false;
    }

    private static int other(RoadPlan.Segment s, int id) {
        return s.a == id ? s.b : s.a;
    }

    private static void reattach(RoadPlan.Segment s, int from, int to) {
        if (s.a == from) {
            s.a = to;
        } else {
            s.b = to;
        }
        s.built = false;
    }

    /** The way a road leaves node n (towards its other end, straight). */
    private static double[] away(RoadPlan plan, RoadPlan.Node n, RoadPlan.Segment s) {
        RoadPlan.Node o = plan.node(other(s, n.id));
        double dx = o.x - n.x, dz = o.z - n.z, l = Math.max(1e-9, Math.hypot(dx, dz));
        return new double[]{dx / l, dz / l};
    }

    private static double length(RoadPlan plan, RoadPlan.Node n, RoadPlan.Segment s) {
        RoadPlan.Node o = plan.node(other(s, n.id));
        return Math.hypot(o.x - n.x, o.z - n.z);
    }
}
