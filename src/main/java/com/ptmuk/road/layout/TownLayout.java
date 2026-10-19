package com.ptmuk.road.layout;

import com.ptmuk.building.PrefabCatalog;
import com.ptmuk.map.StreetNames;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;

/**
 * A town laid out into the plan from a Town site, as winding streets (WindingTown) or this grid: a
 * main road through it the long way (its middle the high street), residential streets either side
 * of it, the space between each pair just deep enough for two rows of houses back to back, and
 * cross streets joining them every so often. Every stretch of road gets a row of buildings each
 * side: shops on the high street, a church, pub and surgery at its ends, and houses everywhere
 * else. The streets get names. It all goes into the plan as ordinary roads and sites, so it can be
 * changed before it's built; nothing is laid out over ground kept clear, and the town site itself
 * becomes a green space, grass over whatever the streets and houses leave.
 */
public final class TownLayout {
    /** Cross streets, centre to centre. */
    private static final int BLOCK = 120;
    /** Clear of a crossing road's pavement at a corner. */
    private static final int CLEAR = 8;
    private static final int MIN_ROW = 9;

    /** A road the long way: where it is across the town and how it looks. */
    private record Street(int c, RoadStyle style, String name, boolean spine) {
    }

    private final RoadPlan plan;
    private final RoadPlan.Site town;
    private final RandomSource rand;
    private final boolean alongX;
    /** Along the town (the main road's way) and across it. */
    private final int len, wide;
    private final Set<String> names = new HashSet<>();
    /** Keep-clear sites over the town. */
    private final List<RoadPlan.Site> kept = new ArrayList<>();
    private int rows, streets;
    /** How deep the rows along the main road can be (shops, the church, houses). */
    private int spineRows;

    private TownLayout(RoadPlan plan, RoadPlan.Site town, RandomSource rand) {
        this.plan = plan;
        this.town = town;
        this.rand = rand;
        this.alongX = town.width() >= town.depth();
        this.len = alongX ? town.width() : town.depth();
        this.wide = alongX ? town.depth() : town.width();
        kept.addAll(kept(plan, town));
    }

    /** The keep-clear sites over a town. */
    static List<RoadPlan.Site> kept(RoadPlan plan, RoadPlan.Site town) {
        List<RoadPlan.Site> out = new ArrayList<>();
        for (RoadPlan.Site k : plan.sites) {
            if (k.type == RoadPlan.SiteType.KEEP_CLEAR && k.x1 >= town.x0 && k.x0 <= town.x1 && k.z1 >= town.z0 && k.z0 <= town.z1) {
                out.add(k);
            }
        }
        return out;
    }

    /** A road style for the town: with kerbs or not, as it's set. */
    static RoadStyle kerbed(RoadStyle st, RoadPlan.Site town) {
        st.kerbs = town.kerbs;
        return st;
    }

    /** Lay the town out (the Town site gives way to it); what it made, to tell the player. */
    public static String layOut(RoadPlan plan, RoadPlan.Site town, RandomSource rand) {
        if (Math.min(town.width(), town.depth()) < 80 || Math.max(town.width(), town.depth()) < 120) {
            return "A town needs at least 120 x 80 blocks";
        }
        String said;
        if (town.winding) {
            WindingTown t = new WindingTown(plan, town, rand);
            t.run();
            said = "Laid out " + t.streets + " streets and " + t.plots + " buildings. Press Build.";
        } else {
            TownLayout t = new TownLayout(plan, town, rand);
            t.run();
            said = "Laid out " + t.streets + " streets and " + t.rows + " rows. Press Build.";
        }
        town.type = RoadPlan.SiteType.GREEN;
        town.built = false;
        return said;
    }

    // ------------------------------------------------------------------ town to world

    private double x(double a, double c) {
        return (alongX ? town.x0 + a : town.x0 + c);
    }

    private double z(double a, double c) {
        return (alongX ? town.z0 + c : town.z0 + a);
    }

    private RoadPlan.Node node(double a, double c) {
        return plan.addNode(Math.floor(x(a, c)), Math.floor(z(a, c)));
    }

    // ------------------------------------------------------------------ the layout

    /** What houses go where: a mix has terraces near the middle, semis further out and detached at the edge. */
    private RoadPlan.SiteType houses(int ring) {
        return houses(town, ring);
    }

    static RoadPlan.SiteType houses(RoadPlan.Site town, int ring) {
        return switch (town.houses) {
            case 0 -> RoadPlan.SiteType.TERRACES;
            case 1 -> RoadPlan.SiteType.SEMIS;
            case 2 -> RoadPlan.SiteType.DETACHED;
            default -> ring <= 1 ? RoadPlan.SiteType.TERRACES : ring == 2 ? RoadPlan.SiteType.SEMIS : RoadPlan.SiteType.DETACHED;
        };
    }

    private int depth(RoadPlan.SiteType type) {
        return SiteLayout.depth(type) + 1;
    }

    private void run() {
        RoadStyle spineStyle = kerbed(town.mainRoad ? RoadStyle.mainRoad() : RoadStyle.residential(), town), res = kerbed(RoadStyle.residential(), town);
        spineRows = Math.max(depth(houses(1)), Math.max(town.highStreet ? depth(RoadPlan.SiteType.PARADE) : 0,
                town.civic ? depth(RoadPlan.SiteType.CIVIC) : 0));
        double spineHalf = spineStyle.halfOuter(), resHalf = res.halfOuter();
        int mid = wide / 2;
        String spineName = town.highStreet ? "High Street" : StreetNames.pick(rand, names);
        names.add("High Street");
        // the streets the long way: the spine, then outwards each side while there's room for the houses beyond
        List<Street> along = new ArrayList<>();
        along.add(new Street(mid, spineStyle, spineName, true));
        for (int side = -1; side <= 1; side += 2) {
            double c = mid + side * (spineHalf + spineRows + depth(houses(1)) + resHalf);
            for (int ring = 1; ; ring++) {
                double edge = side < 0 ? c - resHalf - depth(houses(ring + 1)) : c + resHalf + depth(houses(ring + 1));
                if (edge < 0 || edge > wide - 1) {
                    break;
                }
                along.add(new Street((int) Math.round(c), res, StreetNames.pick(rand, names), false));
                c += side * (2 * resHalf + 2 * depth(houses(ring + 1)));   // the same houses back to back between them
            }
        }
        along.sort((p, q) -> Integer.compare(p.c(), q.c()));
        // cross streets about a block apart (at least two, so the side streets have something to join), spread to fill the length
        List<Integer> cross = new ArrayList<>();
        int n = Math.max(2, Math.round(len / (float) BLOCK));
        for (int k = 0; k < n; k++) {
            cross.add((int) Math.round(len * (k + 0.5) / n));
        }
        List<String> crossNames = new ArrayList<>();
        for (int i = 0; i < cross.size(); i++) {
            crossNames.add(StreetNames.pick(rand, names));
        }
        // a cross street runs on past the outermost streets to near the edge (ready to carry on out of town); a plain
        // corner there would be drawn as a wide sweeping bend
        int cLo = 6, cHi = wide - 7;
        // nodes where the roads cross
        RoadPlan.Node[][] at = new RoadPlan.Node[along.size()][cross.size()];
        for (int i = 0; i < along.size(); i++) {
            for (int k = 0; k < cross.size(); k++) {
                at[i][k] = node(cross.get(k), along.get(i).c());
            }
        }
        // the streets the long way: the spine end to end, the others between the first and last cross streets
        for (int i = 0; i < along.size(); i++) {
            Street s = along.get(i);
            List<RoadPlan.Node> pts = new ArrayList<>();
            List<Integer> as = new ArrayList<>();
            if (s.spine()) {
                pts.add(node(0, s.c()));
                as.add(0);
            }
            for (int k = 0; k < cross.size(); k++) {
                pts.add(at[i][k]);
                as.add(cross.get(k));
            }
            if (s.spine()) {
                pts.add(node(len - 1, s.c()));
                as.add(len - 1);
            }
            for (int j = 0; j + 1 < pts.size(); j++) {
                RoadStyle st = s.style();
                boolean centre = s.spine() && town.highStreet && middle(as.get(j), as.get(j + 1));
                plan.addSegment(pts.get(j).id, pts.get(j + 1).id, centre ? kerbed(RoadStyle.busyHighStreet(), town) : st).name = s.name();
                rowsAlong(s, as.get(j), as.get(j + 1), j == 0 && s.spine() ? 0 : CLEAR, j + 2 == pts.size() && s.spine() ? 0 : CLEAR, ringOf(along, i, mid));
            }
            streets++;
        }
        // the cross streets, with rows along any stretch the long streets' rows leave room for (the dead ends)
        for (int k = 0; k < cross.size(); k++) {
            int a = cross.get(k);
            List<RoadPlan.Node> pts = new ArrayList<>();
            List<Integer> cs = new ArrayList<>();
            pts.add(node(a, cLo));
            cs.add(cLo);
            for (int i = 0; i < along.size(); i++) {
                pts.add(at[i][k]);
                cs.add(along.get(i).c());
            }
            pts.add(node(a, cHi));
            cs.add(cHi);
            for (int j = 0; j + 1 < pts.size(); j++) {
                plan.addSegment(pts.get(j).id, pts.get(j + 1).id, res).name = crossNames.get(k);
                int lo = cs.get(j), hi = cs.get(j + 1);
                double from = lo + corner(along, lo, +1), to = hi - corner(along, hi, -1);
                int ring = Math.abs((lo + hi) / 2 - mid) > wide / 4 ? 3 : 2;
                for (int side = -1; side <= 1; side += 2) {
                    RoadPlan.SiteType type = houses(ring);
                    double near = a + side * (res.halfOuter() + 1), far = near + side * depth(type);
                    if (to - from >= MIN_ROW) {
                        row(type, Math.min(near, far), Math.max(near, far), from, to, crossFront(side));
                    }
                }
            }
            streets++;
        }
    }

    /** In the middle half of the town's length: the high street. */
    private boolean middle(int a0, int a1) {
        double m = (a0 + a1) / 2.0;
        return m > len * 0.25 && m < len * 0.75;
    }

    /** 0 the spine, 1 the first streets out, and so on. */
    private static int ringOf(List<Street> along, int i, int mid) {
        int ring = 0;
        for (int j = 0; j < along.size(); j++) {
            if ((along.get(j).c() - mid) * (along.get(i).c() - mid) > 0 && Math.abs(along.get(j).c() - mid) <= Math.abs(along.get(i).c() - mid)) {
                ring++;
            }
        }
        return along.get(i).spine() ? 0 : ring;
    }

    /** How far a cross street's rows keep clear of a long street at c (its half width and its deepest rows), 0 at a dead end. */
    private double corner(List<Street> along, int c, int side) {
        for (Street s : along) {
            if (s.c() == c) {
                int rows = s.spine() ? spineRows : Math.max(depth(houses(1)), Math.max(depth(houses(2)), depth(houses(3))));
                return s.style().halfOuter() + rows + 1;
            }
        }
        return 0;
    }

    /** The rows each side of a stretch of a long street, from a0 to a1, clear of the corners. */
    private void rowsAlong(Street s, int a0, int a1, int clear0, int clear1, int ring) {
        RoadStyle crossStyle = RoadStyle.residential();
        double from = a0 + (clear0 == 0 ? 0 : crossStyle.halfOuter() + clear0), to = a1 - (clear1 == 0 ? 0 : crossStyle.halfOuter() + clear1);
        if (to - from < MIN_ROW) {
            return;
        }
        boolean high = s.spine() && town.highStreet && middle(a0, a1);
        boolean civicEnd = s.spine() && town.civic && !high && town.highStreet && (middle(a0 - BLOCK, a1 - BLOCK) || middle(a0 + BLOCK, a1 + BLOCK));
        for (int side = -1; side <= 1; side += 2) {
            RoadPlan.SiteType type = high ? RoadPlan.SiteType.PARADE : civicEnd && side < 0 ? RoadPlan.SiteType.CIVIC : houses(s.spine() ? 1 : ring + (side < 0 == s.c() < wide / 2 ? 1 : 0));
            double near = s.c() + side * (s.style().halfOuter() + 1), far = near + side * depth(type);
            if (Math.min(near, far) < 0 || Math.max(near, far) > wide - 1) {
                continue;
            }
            row(type, from, to, Math.min(near, far), Math.max(near, far), longFront(side));
        }
    }

    /** The side of a row facing the road (0 south, 1 west, 2 north, 3 east): rows along a long street... */
    private int longFront(int side) {
        // a row on the low-c side of its street faces up the c axis, and the other way round
        return alongX ? (side < 0 ? 0 : 2) : (side < 0 ? 3 : 1);
    }

    /** ...and rows along a cross street. */
    private int crossFront(int side) {
        return alongX ? (side < 0 ? 3 : 1) : (side < 0 ? 0 : 2);
    }

    /** A row site over a0..a1 along the town and c0..c1 across it, cut round any ground kept clear (bits too short for one of its buildings dropped). */
    private void row(RoadPlan.SiteType type, double a0, double a1, double c0, double c1, int front) {
        int x0 = (int) Math.round(Math.min(x(a0, c0), x(a1, c1))), x1 = (int) Math.round(Math.max(x(a0, c0), x(a1, c1))) - 1;
        int z0 = (int) Math.round(Math.min(z(a0, c0), z(a1, c1))), z1 = (int) Math.round(Math.max(z(a0, c0), z(a1, c1))) - 1;
        boolean xLong = front % 2 == 0;                          // facing south or north: the row runs along x
        List<int[]> spans = new ArrayList<>(List.of(xLong ? new int[]{x0, x1} : new int[]{z0, z1}));
        for (RoadPlan.Site k : kept) {
            if (xLong ? k.z1 < z0 || k.z0 > z1 : k.x1 < x0 || k.x0 > x1) {
                continue;
            }
            int lo = xLong ? k.x0 : k.z0, hi = xLong ? k.x1 : k.z1;
            List<int[]> left = new ArrayList<>();
            for (int[] sp : spans) {
                if (hi < sp[0] || lo > sp[1]) {
                    left.add(sp);
                    continue;
                }
                if (lo - 1 >= sp[0]) {
                    left.add(new int[]{sp[0], lo - 1});
                }
                if (hi + 1 <= sp[1]) {
                    left.add(new int[]{hi + 1, sp[1]});
                }
            }
            spans = left;
        }
        int shortest = Integer.MAX_VALUE;
        for (String id : SiteLayout.units(type)) {
            PrefabCatalog.Entry e = PrefabCatalog.get(id);
            shortest = Math.min(shortest, e == null ? MIN_ROW : e.width());
        }
        for (int[] sp : spans) {
            if (sp[1] - sp[0] + 1 < Math.max(MIN_ROW, shortest)) {
                continue;
            }
            RoadPlan.Site st = xLong ? plan.addSite(type, sp[0], z0, sp[1], z1) : plan.addSite(type, x0, sp[0], x1, sp[1]);
            st.front = front;
            st.brand = rand.nextInt(16);
            rows++;
        }
    }
}
