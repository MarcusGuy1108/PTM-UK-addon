package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.Terrain;
import com.ptmuk.road.layout.TownLayout;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.function.Predicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * What makes the Road Planner's streets look like London's: signs with the give way and stop
 * lines (and no entry where a one-way street comes out), nameplates on the corners, a zebra
 * crossing with Belisha beacons on a high street, and a yellow box at traffic lights.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkStreetGameTests {
    private UkStreetGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void dressesStreetsTheLondonWay(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        // a high street with a side road off it (give way), the high street long enough for a zebra
        RoadPlan.Node w = plan.addNode(0, 0), j = plan.addNode(100, 0), e = plan.addNode(200, 0), s = plan.addNode(100, 100);
        plan.addSegment(w.id, j.id, RoadStyle.busyHighStreet()).name = "High Street";
        plan.addSegment(j.id, e.id, RoadStyle.busyHighStreet()).name = "High Street";
        plan.addSegment(j.id, s.id, RoadStyle.residential()).name = "Church Road";
        // traffic lights
        RoadPlan.Node c = plan.addNode(1000, 0);
        c.junction = RoadPlan.Junction.SIGNALS;
        for (int[] d : new int[][]{{-100, 0}, {100, 0}, {0, -100}, {0, 100}}) {
            plan.addSegment(c.id, plan.addNode(1000 + d[0], d[1]).id, RoadStyle.mainRoad());
        }
        // a stop junction where a one-way street comes out
        RoadPlan.Node k = plan.addNode(2000, 0);
        k.junction = RoadPlan.Junction.STOP;
        plan.addSegment(plan.addNode(1900, 0).id, k.id, RoadStyle.mainRoad());
        plan.addSegment(k.id, plan.addNode(2100, 0).id, RoadStyle.mainRoad());
        plan.addSegment(plan.addNode(2000, 100).id, k.id, RoadStyle.oneWayStreet());
        Layout l = Layout.of(plan, Terrain.flat(64));

        // signs: give way on the left (west) of the side road coming up to the high street, stop and no entry at the one-way street
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.GIVE_WAY_SIGN && f.rot8() == 4 && f.x() < 100 && f.x() > 85 && f.z() > 5 && f.z() < 30),
                "no give way sign by the side road's line");
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.STOP_SIGN && f.rot8() == 4 && Math.abs(f.x() - 2000) < 15 && f.z() > 5 && f.z() < 30),
                "no stop sign by the one-way street's line");
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.NO_ENTRY_SIGN && f.rot8() == 0 && Math.abs(f.x() - 2000) < 20 && Math.abs(f.z()) < 25),
                "no no-entry sign where the one-way street comes out");
        // nameplates: the side road's on its corner facing the high street traffic, the high street's facing the side road
        helper.assertTrue(l.nameplates.stream().anyMatch(p -> p.name().equals("Church Road") && p.rot8() == 6 && p.x() > 100 && p.z() > 0 && p.z() < 30),
                "no Church Road nameplate on its corner: " + l.nameplates);
        helper.assertTrue(l.nameplates.stream().anyMatch(p -> p.name().equals("High Street") && p.rot8() == 0 && p.x() < 100 && p.x() > 70 && p.z() > 0),
                "no High Street nameplate facing the side road: " + l.nameplates);
        helper.assertTrue(l.nameplates.stream().noneMatch(p -> Math.abs(p.x() - 100) < 30 && p.z() < 0),
                "a nameplate on the side of the high street with no road off it");
        // a zebra on the high street: stripes, zig-zags and a Belisha beacon each side
        helper.assertTrue(count(l, 10, 90, -10, 10, Layout.M_ZEBRA) >= 10, "no zebra stripes on the high street");
        helper.assertTrue(count(l, 0, 95, -10, 10, Layout.M_ZIGZAG) >= 20, "no zig-zags by the zebra");
        long beacons = l.fixtures.stream().filter(f -> f.type() == Layout.FixtureType.BELISHA_BEACON && f.x() < 95).count();
        helper.assertTrue(beacons == 2, "expected a Belisha beacon each side of the zebra, got " + beacons);
        // a yellow box at the traffic lights
        helper.assertTrue(count(l, 990, 1010, -10, 10, Layout.M_HATCH | Layout.YELLOW) >= 30, "no yellow box at the traffic lights");
        helper.succeed();
    }

    /** A town's high street keeps its centre line and zebra up to traffic lights at one end (the stop line used to land at the other end). */
    @GameTest(template = "empty5x4x5")
    public static void highStreetKeepsItsLinesUpToTrafficLights(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        TownLayout.layOut(plan, plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, 295, 194), RandomSource.create(1));
        for (RoadPlan.Node n : plan.nodes) {
            if (plan.segmentsAt(n.id).size() == 4 && n.x > 150) {
                n.junction = RoadPlan.Junction.SIGNALS;
            }
        }
        Layout l = Layout.of(plan, Terrain.flat(64));
        helper.assertTrue(count(l, 100, 190, 80, 115, Layout.M_LINE) >= 20, "the high street lost its centre line");
        helper.assertTrue(count(l, 100, 190, 80, 115, Layout.M_ZEBRA) >= 10, "the high street lost its zebra");
        helper.succeed();
    }

    private static boolean has(Layout l, Predicate<Layout.Fixture> what) {
        return l.fixtures.stream().anyMatch(what);
    }

    private static int count(Layout l, int x0, int x1, int z0, int z1, int mark) {
        int n = 0;
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                int idx = l.index(x, z);
                if (idx >= 0 && l.mark[idx] == mark) {
                    n++;
                }
            }
        }
        return n;
    }
}
