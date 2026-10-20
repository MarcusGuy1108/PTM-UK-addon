package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.SiteLayout;
import com.ptmuk.road.layout.Terrain;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Road Planner's sites, laid out on flat ground with a road coming up to the south edge:
 * the road becomes the way in, the car park gets its bays and wide bays, the retail park its
 * shop units and canopy, and the drive-thru its restaurant, order point and lane arrows.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkSiteGameTests {
    private UkSiteGameTests() {
    }

    private static SiteLayout lay(RoadPlan.SiteType type, int w, int d) {
        RoadPlan plan = new RoadPlan();
        plan.addSite(type, 0, 0, w - 1, d - 1);
        RoadPlan.Node a = plan.addNode(w / 2, d + 30), b = plan.addNode(w / 2, d - 2);
        plan.addSegment(a.id, b.id, RoadStyle.mainRoad());
        Layout l = Layout.of(plan, Terrain.flat(64));
        return l.sites.get(0);
    }

    private static int count(SiteLayout sl, SiteLayout.ItemType t) {
        return (int) sl.items.stream().filter(i -> i.type() == t).count();
    }

    @GameTest(template = "empty5x4x5")
    public static void carParkLaysOutBaysAndAWayIn(GameTestHelper helper) {
        SiteLayout sl = lay(RoadPlan.SiteType.CAR_PARK, 60, 48);
        helper.assertTrue(sl.warning == null, "unexpected warning: " + sl.warning);
        helper.assertTrue(sl.frame.front() == 0, "the road side should be south, was " + sl.frame.front());
        helper.assertTrue(sl.entrances.size() == 1 && sl.entrances.get(0).side() == 0, "one way in, on the road side");
        helper.assertTrue(sl.bayCount() >= 30, "too few bays: " + sl.bayCount());
        helper.assertTrue(count(sl, SiteLayout.ItemType.DISABLED_BAY) == 2, "two disabled bays");
        helper.assertTrue(count(sl, SiteLayout.ItemType.EV_BAY) == count(sl, SiteLayout.ItemType.EV_CHARGER), "a charger per EV bay");
        // the way in is open tarmac right through the verge
        int mid = sl.entrances.get(0).at();
        for (int v = 0; v < 3; v++) {
            helper.assertTrue(sl.kind[sl.at(mid, v)] == Layout.ROAD, "the way in is blocked at v=" + v);
        }
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void retailParkHasShopsAndACanopy(GameTestHelper helper) {
        SiteLayout sl = lay(RoadPlan.SiteType.RETAIL_PARK, 72, 70);
        helper.assertTrue(sl.warning == null, "unexpected warning: " + sl.warning);
        long units = sl.buildings.stream().filter(b -> b.kind().equals("unit")).count();
        helper.assertTrue(units == 3, "expected 3 shop units, got " + units);
        helper.assertTrue(sl.buildings.stream().anyMatch(b -> b.kind().equals("canopy")), "no canopy");
        helper.assertTrue(sl.bayCount() >= 20, "too few bays: " + sl.bayCount());
        helper.assertTrue(count(sl, SiteLayout.ItemType.TROLLEYS) == 3, "trolleys by each door");
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void driveThruHasALaneRoundTheRestaurant(GameTestHelper helper) {
        SiteLayout sl = lay(RoadPlan.SiteType.DRIVE_THRU, 36, 44);
        helper.assertTrue(sl.warning == null, "unexpected warning: " + sl.warning);
        helper.assertTrue(sl.buildings.size() == 1 && sl.buildings.get(0).kind().equals("drive_thru"), "no restaurant");
        helper.assertTrue(count(sl, SiteLayout.ItemType.ORDER_POINT) == 1 && count(sl, SiteLayout.ItemType.MENU_BOARD) == 1,
                "no order point and menu board");
        int arrows = 0;
        for (byte m : sl.mark) {
            arrows += m == Layout.M_ARROW ? 1 : 0;
        }
        helper.assertTrue(arrows >= 4, "too few arrows round the lane: " + arrows);
        // too small: a car park instead, with a warning
        SiteLayout small = lay(RoadPlan.SiteType.DRIVE_THRU, 20, 20);
        helper.assertTrue(small.warning != null && small.buildings.isEmpty(), "a tiny drive-thru should fall back to a car park");
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void paradeFillsTheFrontageWithShops(GameTestHelper helper) {
        SiteLayout sl = lay(RoadPlan.SiteType.PARADE, 64, 30);
        helper.assertTrue(sl.buildings.size() >= 5, "a 64-wide parade should have at least 5 shops, had " + sl.buildings.size());
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, total = 0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (SiteLayout.Building b : sl.buildings) {
            helper.assertTrue(b.kind().equals("prefab") && b.v0() == 0, "shops stand at the front of the site");
            for (int u = b.u0(); u <= b.u1(); u++) {
                helper.assertTrue(seen.add(u), "two shops overlap at " + u);
            }
            var e = com.ptmuk.building.PrefabCatalog.get(SiteLayout.UNITS.get(b.brand()));
            helper.assertTrue(e != null && b.u1() - b.u0() + 1 == e.width() && b.v1() - b.v0() + 1 == e.depth(), "a shop's plot isn't its size");
            lo = Math.min(lo, b.u0());
            hi = Math.max(hi, b.u1());
            total += e.width();
        }
        helper.assertTrue(lo >= 0 && hi < 64 && hi - lo + 1 == total, "the shops should be side by side within the site");
        helper.assertTrue(64 - total < 9, "room for another shop was left over: " + (64 - total));
        RoadPlan other = new RoadPlan();
        RoadPlan.Site st = other.addSite(RoadPlan.SiteType.PARADE, 0, 0, 63, 29);
        st.brand = 3;
        RoadPlan.Node a = other.addNode(32, 60), b = other.addNode(32, 28);
        other.addSegment(a.id, b.id, RoadStyle.mainRoad());
        SiteLayout mix = Layout.of(other, Terrain.flat(64)).sites.get(0);
        helper.assertTrue(mix.buildings.get(0).brand() != sl.buildings.get(0).brand(), "shuffling should change the shops");
        helper.succeed();
    }

    /**
     * A grid town over 400 x 260: a High Street with shop parades in the middle, named side
     * streets, rows of houses everywhere else, a church, pub and surgery, all as checked below.
     */
    @GameTest(template = "empty5x4x5")
    public static void laysOutAGridTown(GameTestHelper helper) {
        checkTown(helper, false, 400, 260);
    }

    /** The same of winding streets, on a bigger site (side streets need room to wind). */
    @GameTest(template = "empty5x4x5")
    public static void laysOutAWindingTown(GameTestHelper helper) {
        checkTown(helper, true, 520, 420);
    }

    /**
     * A big winding town: traffic lights at several junctions, trees in its green spaces, and
     * streets out into every part of it (nowhere much more than a street's gardens away from one).
     */
    @GameTest(template = "empty5x4x5")
    public static void bigTownHasLightsTreesAndFillsOut(GameTestHelper helper) {
        int w = 900, d = 700;
        RoadPlan plan = new RoadPlan();
        RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, w - 1, d - 1);
        com.ptmuk.road.layout.TownLayout.layOut(plan, town, net.minecraft.util.RandomSource.create(1));
        long lights = plan.nodes.stream().filter(n -> n.junction == RoadPlan.Junction.SIGNALS).count();
        helper.assertTrue(lights >= 3, "only " + lights + " traffic light junctions in a big town");
        Layout l = Layout.of(plan, Terrain.flat(64));
        long trees = l.fixtures.stream().filter(f -> f.type() == Layout.FixtureType.PARK_TREE).count();
        helper.assertTrue(trees >= 50, "only " + trees + " trees in the green spaces");
        boolean[] road = new boolean[w * d];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int idx = l.index(x, z);
                road[z * w + x] = idx >= 0 && l.kind[idx] == Layout.ROAD;
            }
        }
        int near = 0, all = 0;
        for (int z = 0; z < d; z += 8) {
            for (int x = 0; x < w; x += 8) {
                all++;
                search:
                for (int dz = -60; dz <= 60; dz += 4) {
                    for (int dx = -60; dx <= 60; dx += 4) {
                        int xx = x + dx, zz = z + dz;
                        if (xx >= 0 && zz >= 0 && xx < w && zz < d && dx * dx + dz * dz <= 3600 && road[zz * w + xx]) {
                            near++;
                            break search;
                        }
                    }
                }
            }
        }
        helper.assertTrue(near >= all * 0.9, "only " + near + " of " + all + " places in the town are near a street");
        helper.succeed();
    }

    /**
     * Shops, houses and a church, pub and surgery, on named streets with a High Street; every
     * building inside the town, clear of the roads and of each other, each with its building; and
     * the town turned to a green space, so no ground inside it is left as it was.
     */
    private static void checkTown(GameTestHelper helper, boolean winding, int w, int d) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, w - 1, d - 1);
        town.winding = winding;
        String said = com.ptmuk.road.layout.TownLayout.layOut(plan, town, net.minecraft.util.RandomSource.create(1));
        helper.assertTrue(said.startsWith("Laid out"), said);
        helper.assertTrue(plan.sites.stream().noneMatch(st -> st.type == RoadPlan.SiteType.TOWN), "the town site should give way to the town");
        helper.assertTrue(town.type == RoadPlan.SiteType.GREEN, "the town site should be left as its green");
        helper.assertTrue(plan.segments.stream().anyMatch(sg -> "High Street".equals(sg.name)), "no High Street");
        long streetNames = plan.segments.stream().map(sg -> sg.name).distinct().count();
        helper.assertTrue(streetNames >= 4 && plan.segments.stream().allMatch(sg -> sg.name != null && !sg.name.isBlank()),
                "every street should have a name, " + streetNames + " names");
        long parades = plan.sites.stream().filter(st -> st.type == RoadPlan.SiteType.PARADE).count();
        long houses = plan.sites.stream().filter(st -> st.type == RoadPlan.SiteType.TERRACES || st.type == RoadPlan.SiteType.SEMIS
                || st.type == RoadPlan.SiteType.DETACHED).count();
        helper.assertTrue(parades >= 2 && houses >= 8, parades + " parades and " + houses + " lots of houses");
        helper.assertTrue(plan.sites.stream().anyMatch(st -> st.type == RoadPlan.SiteType.CIVIC), "no church, pub and surgery");
        RoadPlan roads = plan.copy();
        roads.sites.clear();
        Layout bare = Layout.of(roads, Terrain.flat(64));
        for (RoadPlan.Site a : plan.sites) {
            if (a == town) {
                continue;
            }
            helper.assertTrue(a.x0 >= 0 && a.z0 >= 0 && a.x1 < w && a.z1 < d, "a " + a.type + " outside the town: " + a.x0 + "," + a.z0 + " " + a.x1 + "," + a.z1);
            for (RoadPlan.Site b : plan.sites) {
                helper.assertTrue(a == b || b == town || a.x1 < b.x0 || b.x1 < a.x0 || a.z1 < b.z0 || b.z1 < a.z0,
                        a.type + " at " + a.x0 + "," + a.z0 + " overlaps " + b.type + " at " + b.x0 + "," + b.z0);
            }
            for (int z = a.z0; z <= a.z1; z++) {
                for (int x = a.x0; x <= a.x1; x++) {
                    int idx = bare.index(x, z);
                    byte k = idx < 0 ? Layout.NONE : bare.kind[idx];
                    helper.assertTrue(k == Layout.NONE || k == Layout.BLEND, "a " + a.type + " at " + a.x0 + "," + a.z0 + " is on a road (" + x + "," + z + ")");
                }
            }
        }
        Layout l = Layout.of(plan, Terrain.flat(64));
        for (SiteLayout sl : l.sites) {
            helper.assertTrue(sl.site == town || !sl.buildings.isEmpty(), "an empty " + sl.site.type + " at " + sl.site.x0 + "," + sl.site.z0 + ": " + sl.warning);
        }
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int idx = l.index(x, z);
                helper.assertTrue(idx >= 0 && l.kind[idx] != Layout.NONE && l.kind[idx] != Layout.BLEND, "ground left untouched in the town at " + x + "," + z);
            }
        }
        helper.succeed();
    }

    /**
     * Ground kept clear inside a town gets no buildings, no side streets and isn't touched (no
     * grass laid over it, no slope cut into it), in either kind of town; a town without kerbs has
     * none on any of its roads.
     */
    @GameTest(template = "empty5x4x5")
    public static void keepsGroundClearAndLeavesOutKerbs(GameTestHelper helper) {
        for (boolean winding : new boolean[]{false, true}) {
            RoadPlan plan = new RoadPlan();
            RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, 519, 419);
            town.winding = winding;
            town.kerbs = false;
            // off the main road (which runs through the middle the long way)
            RoadPlan.Site keep = plan.addSite(RoadPlan.SiteType.KEEP_CLEAR, 300, 20, 420, 120);
            com.ptmuk.road.layout.TownLayout.layOut(plan, town, net.minecraft.util.RandomSource.create(2));
            String kind = winding ? "winding" : "grid";
            helper.assertTrue(plan.segments.stream().noneMatch(sg -> sg.style.kerbs), "a " + kind + " town without kerbs has a road with them");
            for (RoadPlan.Site a : plan.sites) {
                helper.assertTrue(a == town || a == keep || a.x1 < keep.x0 || keep.x1 < a.x0 || a.z1 < keep.z0 || keep.z1 < a.z0,
                        "a " + a.type + " on the kept ground in the " + kind + " town at " + a.x0 + "," + a.z0);
            }
            Layout l = Layout.of(plan, Terrain.flat(64));
            for (int z = keep.z0; z <= keep.z1; z++) {
                for (int x = keep.x0; x <= keep.x1; x++) {
                    int idx = l.index(x, z);
                    helper.assertTrue(winding ? l.kind[idx] == Layout.NONE : l.kind[idx] == Layout.NONE || l.kind[idx] != Layout.BLEND && l.kind[idx] != Layout.ISLAND,
                            "the kept ground in the " + kind + " town is touched at " + x + "," + z);
                }
            }
        }
        helper.succeed();
    }

    /** Street nameplates face their road (with houses behind them, facing away they'd look at a front wall). */
    @GameTest(template = "empty5x4x5")
    public static void nameplatesFaceTheirRoad(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, 519, 419);
        com.ptmuk.road.layout.TownLayout.layOut(plan, town, net.minecraft.util.RandomSource.create(3));
        Layout l = Layout.of(plan, Terrain.flat(64));
        helper.assertTrue(l.nameplates.size() >= 8, "only " + l.nameplates.size() + " nameplates");
        for (Layout.Nameplate n : l.nameplates) {
            double a = Math.toRadians(n.rot8() * 45);
            boolean road = false;
            for (int k = 1; k <= 12 && !road; k++) {
                int idx = l.index(n.x() + (int) Math.round(-Math.sin(a) * k), n.z() + (int) Math.round(Math.cos(a) * k));
                road = idx >= 0 && l.kind[idx] == Layout.ROAD;
            }
            helper.assertTrue(road, "the " + n.name() + " nameplate at " + n.x() + "," + n.z() + " faces away from the road");
        }
        helper.succeed();
    }
}
