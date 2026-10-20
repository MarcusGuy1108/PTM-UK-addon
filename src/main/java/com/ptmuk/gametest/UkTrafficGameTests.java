package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.Terrain;
import com.ptmuk.road.layout.TownLayout;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * PTM2 traffic points for the roads the Road Planner builds: a main road with a side road off
 * it, carrying on to a roundabout. Cars keep left, can turn both ways at the junction, go round
 * the roundabout clockwise to every exit, and never run out of road.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkTrafficGameTests {
    private UkTrafficGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void trafficKeepsLeftTurnsAndGoesRoundRoundabouts(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Node w = plan.addNode(0, 0), j = plan.addNode(100, 0), e = plan.addNode(200, 0), s = plan.addNode(100, 100);
        RoadPlan.Node r = plan.addNode(300, 0), n = plan.addNode(300, -100), x = plan.addNode(400, 0), y = plan.addNode(300, 100);
        r.junction = RoadPlan.Junction.ROUNDABOUT;
        for (RoadPlan.Node[] p : new RoadPlan.Node[][]{{w, j}, {j, e}, {e, r}, {j, s}, {r, n}, {r, x}, {r, y}}) {
            plan.addSegment(p[0].id, p[1].id, p[1] == s ? RoadStyle.residential() : RoadStyle.mainRoad());
        }
        Layout layout = Layout.of(plan, Terrain.flat(64));
        List<Layout.TrafficPoint> t = layout.traffic;
        helper.assertTrue(t.size() > 20, "too few traffic points: " + t.size());
        for (Layout.TrafficPoint p : t) {
            helper.assertTrue(!p.next().isEmpty(), "cars would run out of road at " + p.x() + ", " + p.z());
        }
        // keep left: eastbound on the north side of the centre line (z 0.5), westbound on the south
        for (Layout.TrafficPoint p : t) {
            if (p.x() < 90 && Math.abs(p.z()) < 10) {
                helper.assertTrue(p.rot8() == 6 ? p.z() < 0 : p.rot8() == 2 && p.z() > 0,
                        "lane on the wrong side at " + p.x() + ", " + p.z() + " going " + p.rot8());
            }
        }
        // out of the side road both ways (from its last point before the give way line), and into it off the main road
        int out = nearest(helper, t, p -> p.rot8() == 4 && Math.abs(p.x() - 100) < 10 && p.z() > 3, 100, 0);
        helper.assertTrue(goesTo(t, out, 2), "can't turn left out of the side road");
        helper.assertTrue(goesTo(t, out, 6), "can't turn right out of the side road");
        helper.assertTrue(goesTo(t, nearest(helper, t, p -> p.rot8() == 2 && p.z() > 0 && p.z() < 10 && p.x() > 100, 100, 0), 0),
                "can't turn left into the side road");
        helper.assertTrue(goesTo(t, nearest(helper, t, p -> p.rot8() == 6 && p.z() < 0 && p.z() > -10 && p.x() < 100, 100, 0), 0),
                "can't turn right into the side road");
        // PTM2 junction areas (no cars start off in them): the give way junction and the roundabout, marked as one
        helper.assertTrue(layout.junctionZones.size() == 2, "expected 2 junction areas, got " + layout.junctionZones.size());
        helper.assertTrue(layout.junctionZones.stream().anyMatch(z -> z.roundabout() && z.x0() < 300 && z.x1() > 300), "no roundabout area");
        // into the roundabout from the north and round it (clockwise) to every exit
        int in = nearest(helper, t, p -> p.rot8() == 0 && Math.abs(p.x() - 300) < 10 && p.z() < -5, 300, 0);
        Predicate<Layout.TrafficPoint> near = p -> Math.hypot(p.x() - 300, p.z()) < 30;
        helper.assertTrue(reaches(t, in, near, p -> p.rot8() == 6 && p.x() > 310), "can't take the east exit");
        helper.assertTrue(reaches(t, in, near, p -> p.rot8() == 0 && p.z() > 10), "can't take the south exit");
        helper.assertTrue(reaches(t, in, near, p -> p.rot8() == 2 && p.x() < 290), "can't take the west exit");
        int rings = 0;
        for (Layout.TrafficPoint p : t) {
            double px = p.x() + 0.5 - 300.5, pz = p.z() + 0.5 - 0.5;
            for (int q : p.next()) {
                double qx = t.get(q).x() + 0.5 - 300.5, qz = t.get(q).z() + 0.5 - 0.5;
                if (Math.hypot(px, pz) < 14 && Math.hypot(qx, qz) < 14) {
                    rings++;
                    helper.assertTrue(px * qz - pz * qx > 0, "anticlockwise round the roundabout at " + p.x() + ", " + p.z());
                }
            }
        }
        helper.assertTrue(rings >= 8, "no ring round the roundabout");
        helper.succeed();
    }

    /**
     * Round a winding town every lane can be got into and goes somewhere, and every link goes on
     * ahead: none back to a point behind (PTM2 loops round to reach it, which looked like a U-turn
     * at junctions), except turning round at a dead end.
     */
    @GameTest(template = "empty5x4x5")
    public static void trafficOnlyTurnsRoundAtDeadEnds(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, 519, 419);
        TownLayout.layOut(plan, town, RandomSource.create(5));
        List<Layout.TrafficPoint> t = Layout.of(plan, Terrain.flat(64)).traffic;
        helper.assertTrue(t.size() > 200, "too few traffic points: " + t.size());
        Set<Integer> reached = new HashSet<>();
        t.forEach(p -> reached.addAll(p.next()));
        helper.assertTrue(reached.size() == t.size(), (t.size() - reached.size()) + " lanes no car can get into");
        for (Layout.TrafficPoint p : t) {
            helper.assertTrue(!p.next().isEmpty(), "cars would run out of road at " + p.x() + ", " + p.z());
            for (int n : p.next()) {
                Layout.TrafficPoint q = t.get(n);
                double dx = q.x() - p.x(), dz = q.z() - p.z(), len = Math.hypot(dx, dz);
                double ahead = Math.min(dx * -Math.sin(p.rot8() * Math.PI / 4) + dz * Math.cos(p.rot8() * Math.PI / 4),
                        dx * -Math.sin(q.rot8() * Math.PI / 4) + dz * Math.cos(q.rot8() * Math.PI / 4)) / len;
                int turn = Math.abs(Math.floorMod(q.rot8() - p.rot8() + 4, 8) - 4);
                helper.assertTrue(ahead > -0.2 || turn >= 3 && len < 10,
                        "a link back from " + p.x() + ", " + p.z() + " to " + q.x() + ", " + q.z());
            }
        }
        helper.succeed();
    }

    /** The point matching nearest (x, z). */
    private static int nearest(GameTestHelper helper, List<Layout.TrafficPoint> t, Predicate<Layout.TrafficPoint> what, int x, int z) {
        int best = -1;
        for (int i = 0; i < t.size(); i++) {
            Layout.TrafficPoint p = t.get(i);
            if (what.test(p) && (best < 0 || Math.hypot(p.x() - x, p.z() - z) < Math.hypot(t.get(best).x() - x, t.get(best).z() - z))) {
                best = i;
            }
        }
        if (best < 0) {
            helper.fail("no traffic point where expected near " + x + ", " + z);
        }
        return best;
    }

    /** Whether a point leads straight on to one going the given way. */
    private static boolean goesTo(List<Layout.TrafficPoint> t, int from, int rot8) {
        return t.get(from).next().stream().anyMatch(q -> t.get(q).rot8() == rot8);
    }

    /** Whether a point leads, through points `within`, to one that's `what`. */
    private static boolean reaches(List<Layout.TrafficPoint> t, int from, Predicate<Layout.TrafficPoint> within, Predicate<Layout.TrafficPoint> what) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> todo = new ArrayDeque<>(List.of(from));
        while (!todo.isEmpty()) {
            int i = todo.pop();
            if (!seen.add(i)) {
                continue;
            }
            if (i != from && what.test(t.get(i))) {
                return true;
            }
            if (i == from || within.test(t.get(i))) {
                todo.addAll(t.get(i).next());
            }
        }
        return false;
    }
}
