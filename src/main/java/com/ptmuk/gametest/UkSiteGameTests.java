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
}
