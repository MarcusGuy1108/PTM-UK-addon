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
     * A town laid out over 400 x 260: a High Street with shop parades in the middle, named side
     * streets, rows of houses everywhere else, each row inside the town, clear of the roads and of
     * each other, and every row filled with buildings.
     */
    @GameTest(template = "empty5x4x5")
    public static void laysOutATown(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Site town = plan.addSite(RoadPlan.SiteType.TOWN, 0, 0, 399, 259);
        String said = com.ptmuk.road.layout.TownLayout.layOut(plan, town, net.minecraft.util.RandomSource.create(1));
        helper.assertTrue(said.startsWith("Laid out"), said);
        helper.assertTrue(plan.sites.stream().noneMatch(st -> st.type == RoadPlan.SiteType.TOWN), "the town site should give way to the town");
        helper.assertTrue(plan.segments.stream().anyMatch(sg -> "High Street".equals(sg.name)), "no High Street");
        long streetNames = plan.segments.stream().map(sg -> sg.name).distinct().count();
        helper.assertTrue(streetNames >= 4 && plan.segments.stream().allMatch(sg -> sg.name != null && !sg.name.isBlank()),
                "every street should have a name, " + streetNames + " names");
        long parades = plan.sites.stream().filter(st -> st.type == RoadPlan.SiteType.PARADE).count();
        long houseRows = plan.sites.stream().filter(st -> st.type == RoadPlan.SiteType.TERRACES || st.type == RoadPlan.SiteType.SEMIS
                || st.type == RoadPlan.SiteType.DETACHED).count();
        helper.assertTrue(parades >= 2 && houseRows >= 8, parades + " parades and " + houseRows + " rows of houses");
        helper.assertTrue(plan.sites.stream().anyMatch(st -> st.type == RoadPlan.SiteType.CIVIC), "no church, pub and surgery");
        var nodes = new java.util.HashMap<Integer, RoadPlan.Node>();
        plan.nodes.forEach(n -> nodes.put(n.id, n));
        for (RoadPlan.Site a : plan.sites) {
            helper.assertTrue(a.x0 >= 0 && a.z0 >= 0 && a.x1 <= 399 && a.z1 <= 259, "a row outside the town: " + a.x0 + "," + a.z0 + " " + a.x1 + "," + a.z1);
            for (RoadPlan.Site b : plan.sites) {
                helper.assertTrue(a == b || a.x1 < b.x0 || b.x1 < a.x0 || a.z1 < b.z0 || b.z1 < a.z0,
                        "rows overlap: " + a.type + " " + a.x0 + "," + a.z0 + " and " + b.type + " " + b.x0 + "," + b.z0);
            }
            for (RoadPlan.Segment sg : plan.segments) {
                RoadPlan.Node p = nodes.get(sg.a), q = nodes.get(sg.b);
                double h = sg.style.halfOuter() - 0.5;
                double rx0 = Math.min(p.x, q.x) - h, rx1 = Math.max(p.x, q.x) + h, rz0 = Math.min(p.z, q.z) - h, rz1 = Math.max(p.z, q.z) + h;
                helper.assertTrue(a.x1 + 1 <= rx0 || a.x0 >= rx1 || a.z1 + 1 <= rz0 || a.z0 >= rz1,
                        "a " + a.type + " row at " + a.x0 + "," + a.z0 + " is on " + sg.name);
            }
        }
        Layout l = Layout.of(plan, Terrain.flat(64));
        for (SiteLayout sl : l.sites) {
            helper.assertTrue(!sl.buildings.isEmpty(), "an empty " + sl.site.type + " row at " + sl.site.x0 + "," + sl.site.z0 + ": " + sl.warning);
        }
        helper.succeed();
    }
}
