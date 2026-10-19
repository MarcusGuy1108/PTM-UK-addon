package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.map.MapData;
import com.ptmuk.road.RoadBuilder;
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
        // nameplates: the side road's on its corner, the high street's on the other corner, each facing its own road
        helper.assertTrue(l.nameplates.stream().anyMatch(p -> p.name().equals("Church Road") && p.rot8() == 2 && p.x() > 100 && p.z() > 0 && p.z() < 30),
                "no Church Road nameplate on its corner: " + l.nameplates);
        helper.assertTrue(l.nameplates.stream().anyMatch(p -> p.name().equals("High Street") && p.rot8() == 4 && p.x() < 100 && p.x() > 70 && p.z() > 0),
                "no High Street nameplate on the other corner: " + l.nameplates);
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
        RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, 295, 194);
        town.winding = false;
        TownLayout.layOut(plan, town, RandomSource.create(1));
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

    /**
     * Junctions marked and signed the UK way: double broken give way lines at a side road, STOP
     * written before a stop line, LOOK RIGHT at a signal crossing, a bordered yellow box, and at
     * a roundabout a warning triangle, SLOW, hatched splitter islands with a raised nose and keep
     * left sign, chevrons on the island and a map-type direction sign on the way in; a mini roundabout's own blue sign.
     */
    @GameTest(template = "empty5x4x5")
    public static void marksAndSignsJunctionsTheUkWay(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        // a side road off a main road (give way), traffic lights, a stop junction
        RoadPlan.Node j = plan.addNode(100, 0);
        plan.addSegment(plan.addNode(0, 0).id, j.id, RoadStyle.mainRoad());
        plan.addSegment(j.id, plan.addNode(200, 0).id, RoadStyle.mainRoad());
        plan.addSegment(j.id, plan.addNode(100, 100).id, RoadStyle.residential());
        RoadPlan.Node c = plan.addNode(1000, 0), k = plan.addNode(2000, 0), r = plan.addNode(3000, 0), m = plan.addNode(4000, 0);
        c.junction = RoadPlan.Junction.SIGNALS;
        k.junction = RoadPlan.Junction.STOP;
        r.junction = RoadPlan.Junction.ROUNDABOUT;
        m.junction = RoadPlan.Junction.MINI_ROUNDABOUT;
        for (RoadPlan.Node n : new RoadPlan.Node[]{c, r, m}) {
            for (int[] d : new int[][]{{-100, 0}, {100, 0}, {0, -100}, {0, 100}}) {
                plan.addSegment(n.id, plan.addNode(n.x + d[0], d[1]).id, n == m && d[0] == 0 ? RoadStyle.residential() : RoadStyle.mainRoad());
            }
        }
        plan.addSegment(plan.addNode(1900, 0).id, k.id, RoadStyle.mainRoad());
        plan.addSegment(k.id, plan.addNode(2100, 0).id, RoadStyle.mainRoad());
        plan.addSegment(plan.addNode(2000, 100).id, k.id, RoadStyle.residential());
        Layout l = Layout.of(plan, Terrain.flat(64));

        // two rows of give way dashes across the side road's way in (heading north on its west half)
        long rows = java.util.stream.IntStream.rangeClosed(3, 25).filter(z -> count(l, 92, 99, z, z, Layout.M_HALF) >= 3).count();
        helper.assertTrue(rows == 2, "expected a double give way line at the side road, got " + rows + " rows");
        helper.assertTrue(words(l, "STOP", 1990, 2000, 3, 30), "no STOP before the stop line");
        helper.assertTrue(words(l, "LOOK RIGHT", 975, 1025, -25, 25), "no LOOK RIGHT at the crossings: " + l.words.values());
        int border = 0;
        for (int z = -15; z <= 15; z++) {
            for (int x = 985; x <= 1015; x++) {
                if (l.mark[l.index(x, z)] == (Layout.M_LINE | Layout.YELLOW) && (count(l, x - 1, x + 1, z, z, Layout.M_HATCH | Layout.YELLOW)
                        + count(l, x, x, z - 1, z + 1, Layout.M_HATCH | Layout.YELLOW)) > 0) {
                    border++;
                }
            }
        }
        helper.assertTrue(border >= 12, "no border round the yellow box: " + border);
        // the roundabout
        helper.assertTrue(words(l, "SLOW", 2940, 2985, -8, 0), "no SLOW on the way into the roundabout");
        helper.assertTrue(count(l, 2950, 3050, -50, 50, Layout.M_HATCH) >= 20, "no hatched splitter islands");
        long chevrons = l.fixtures.stream().filter(f -> f.type() == Layout.FixtureType.CHEVRON_SIGN && Math.abs(f.x() - 3000) < 15).count();
        helper.assertTrue(chevrons == 4, "expected a chevron board facing each way in, got " + chevrons);
        long keepLeft = l.fixtures.stream().filter(f -> f.type() == Layout.FixtureType.KEEP_LEFT_SIGN && Math.hypot(f.x() - 3000, f.z()) < 30).count();
        helper.assertTrue(keepLeft == 4, "expected a keep left sign on each splitter island's raised nose, got " + keepLeft);
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.ROUNDABOUT_AHEAD_SIGN && f.x() > 2900 && f.x() < 2950 && f.z() < 0 && f.rot8() == 6),
                "no roundabout warning triangle on the left well before the roundabout");
        Layout.DirectionSign ads = l.directionSigns.stream().filter(s -> s.roundabout() && s.x() < 3000 && Math.abs(s.z()) < 15).findFirst().orElse(null);
        helper.assertTrue(ads != null && ads.rot8() == 2 && ads.z() < 0 && ads.x() < 2975, "no direction sign on the left before the roundabout: " + ads);
        helper.assertTrue(ads.left().dz() < -0.9 && ads.ahead().dx() > 0.9 && ads.right().dz() > 0.9, "the sign's roads are the wrong way round: " + ads);
        // the mini roundabout has its blue sign, not a give way sign; no direction signs up its residential side roads
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.MINI_ROUNDABOUT_SIGN && Math.abs(f.x() - 4000) < 25), "no mini roundabout sign");
        helper.assertTrue(!has(l, f -> f.type() == Layout.FixtureType.GIVE_WAY_SIGN && Math.abs(f.x() - 4000) < 25), "a give way sign at the mini roundabout");
        helper.assertTrue(l.directionSigns.stream().noneMatch(s -> Math.abs(s.x() - 4000) < 10), "a direction sign up a residential road");
        helper.succeed();
    }

    /** A road from a roundabout to traffic lights keeps its centre line and stop line (the roundabout's island used to count as the lights). */
    @GameTest(template = "empty5x4x5")
    public static void roadFromRoundaboutToLightsKeepsItsLines(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Node r = plan.addNode(100, 0), c = plan.addNode(220, 0);
        r.junction = RoadPlan.Junction.ROUNDABOUT;
        c.junction = RoadPlan.Junction.SIGNALS;
        plan.addSegment(plan.addNode(0, 0).id, r.id, RoadStyle.mainRoad());
        plan.addSegment(r.id, c.id, RoadStyle.mainRoad());
        plan.addSegment(c.id, plan.addNode(320, 0).id, RoadStyle.mainRoad());
        for (RoadPlan.Node n : new RoadPlan.Node[]{r, c}) {
            plan.addSegment(n.id, plan.addNode(n.x, -120).id, RoadStyle.mainRoad());
            plan.addSegment(n.id, plan.addNode(n.x, 120).id, RoadStyle.mainRoad());
        }
        Layout l = Layout.of(plan, Terrain.flat(64));
        helper.assertTrue(count(l, 130, 195, -1, 1, Layout.M_LINE) >= 20, "the road lost its centre line");
        helper.assertTrue(count(l, 190, 207, -8, 0, Layout.M_STOP) >= 4, "no stop line at the lights");
        helper.assertTrue(l.directionSigns.stream().filter(s -> s.x() > 130 && s.x() < 190).count() == 2, "no direction signs along the road");
        helper.succeed();
    }

    /** Roads built without kerbs (flush edges) still get raised roundabout islands, signs and all. */
    @GameTest(template = "empty5x4x5")
    public static void roadsWithoutKerbsKeepRaisedIslands(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Node r = plan.addNode(0, 0);
        r.junction = RoadPlan.Junction.ROUNDABOUT;
        for (int[] d : new int[][]{{-100, 0}, {100, 0}, {0, -100}, {0, 100}}) {
            RoadStyle st = RoadStyle.mainRoad();
            st.kerbs = false;
            plan.addSegment(r.id, plan.addNode(d[0], d[1]).id, st);
        }
        Layout l = Layout.of(plan, Terrain.flat(64));
        float road = l.top[l.index(-40, -3)];
        for (int i = 0; i < l.kind.length; i++) {
            if (l.kind[i] == Layout.ISLAND_KERB || l.kind[i] == Layout.ISLAND) {
                helper.assertTrue(l.top[i] > road, "an island not raised at " + l.cellX(i) + ", " + l.cellZ(i));
            }
        }
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.KEEP_LEFT_SIGN), "no keep left sign without kerbs");
        helper.assertTrue(has(l, f -> f.type() == Layout.FixtureType.CHEVRON_SIGN), "no chevrons without kerbs");
        helper.assertTrue(l.directionSigns.size() == 4, "expected a direction sign on each way in, got " + l.directionSigns.size());
        helper.succeed();
    }

    /** What a direction sign says: the district that way, else the town centre; the station that way; else the road's name. */
    @GameTest(template = "empty5x4x5")
    public static void directionSignsSayWhereRoadsGo(GameTestHelper helper) {
        MapData around = new MapData();
        around.areas.add(new MapData.Area(MapData.AreaKind.DISTRICT, "Welling", 400, -50, 600, 50, 0));
        around.areas.add(new MapData.Area(MapData.AreaKind.DISTRICT, "Here", -30, -30, 30, 30, 0));
        around.places.add(new MapData.Place(MapData.Kind.STATION, "Bexleyheath", 10, -300));
        RoadPlan plan = new RoadPlan();
        plan.addSite(RoadPlan.SiteType.TOWN, -50, 400, 50, 500);
        Layout.DirectionSign s = new Layout.DirectionSign(-40, -8, 64, 2, 0, 0, true, new Layout.Exit("North Road", 0, -1),
                new Layout.Exit("East Road", 1, 0), new Layout.Exit(null, 0, 1));
        helper.assertTrue(RoadBuilder.destinations(around, plan.sites, s, s.ahead()).equals("Welling"), "ahead isn't Welling");
        helper.assertTrue(RoadBuilder.destinations(around, plan.sites, s, s.left()).equals("Bexleyheath"), "left isn't the station");
        helper.assertTrue(RoadBuilder.destinations(around, plan.sites, s, s.right()).equals("Town Centre"), "right isn't the town centre");
        helper.assertTrue(RoadBuilder.destinations(around, plan.sites, s, new Layout.Exit("Mill Lane", -1, 0)).equals("Mill Lane"),
                "a road leading nowhere named isn't signed by its name");
        helper.succeed();
    }

    private static boolean words(Layout l, String text, int x0, int x1, int z0, int z1) {
        return l.words.entrySet().stream().anyMatch(e -> e.getValue().equals(text) && l.mark[e.getKey()] == Layout.M_TEXT
                && l.cellX(e.getKey()) >= x0 && l.cellX(e.getKey()) <= x1 && l.cellZ(e.getKey()) >= z0 && l.cellZ(e.getKey()) <= z1);
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
