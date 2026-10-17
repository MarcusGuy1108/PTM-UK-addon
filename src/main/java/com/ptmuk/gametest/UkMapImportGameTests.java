package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.OsmMap;
import com.ptmuk.road.plan.RoadPlan;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Real maps: a little hand-made OpenStreetMap export (a crossroads with traffic lights, a
 * roundabout with three arms and a three-storey house beside one street) read and laid out at
 * the planner's scale.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkMapImportGameTests {
    private static final double LAT = 51.5, LON = -0.1;

    private UkMapImportGameTests() {
    }

    /** A point x metres east and y metres north of the middle. */
    private static String node(int id, double east, double north, String tags) {
        double lat = LAT + north / 111250.0, lon = LON + east / (111320.0 * Math.cos(Math.toRadians(LAT)));
        return "<node id=\"" + id + "\" lat=\"" + lat + "\" lon=\"" + lon + "\"" + (tags.isEmpty() ? "/>" : ">" + tags + "</node>");
    }

    private static String way(int id, String tags, int... refs) {
        StringBuilder sb = new StringBuilder("<way id=\"" + id + "\">");
        for (int r : refs) {
            sb.append("<nd ref=\"").append(r).append("\"/>");
        }
        return sb.append(tags).append("</way>").toString();
    }

    private static String tag(String k, String v) {
        return "<tag k=\"" + k + "\" v=\"" + v + "\"/>";
    }

    private static final String OSM = "<?xml version=\"1.0\"?><osm version=\"0.6\">"
            + node(1, -100, 0, "") + node(2, 0, 0, "") + node(3, 100, 0, "")
            + node(4, 0, -100, "") + node(6, 0, -6, tag("highway", "traffic_signals")) + node(5, 0, 100, "")
            + node(10, 20, 5, "") + node(11, 40, 5, "") + node(12, 40, 15, "") + node(13, 20, 15, "")
            + node(20, 320, 0, "") + node(21, 314, 14, "") + node(22, 300, 20, "") + node(23, 286, 14, "")
            + node(24, 280, 0, "") + node(25, 286, -14, "") + node(26, 300, -20, "") + node(27, 314, -14, "")
            + node(30, 400, 0, "") + node(31, 300, 100, "")
            + way(100, tag("highway", "residential"), 1, 2, 3)
            + way(101, tag("highway", "residential"), 4, 6, 2, 5)
            + way(102, tag("building", "house") + tag("building:levels", "3"), 10, 11, 12, 13, 10)
            + way(103, tag("highway", "primary") + tag("junction", "roundabout"), 20, 21, 22, 23, 24, 25, 26, 27, 20)
            + way(104, tag("highway", "primary"), 3, 24)
            + way(105, tag("highway", "primary"), 20, 30)
            + way(106, tag("highway", "primary"), 22, 31)
            + "</osm>";

    @GameTest(template = "empty5x4x5")
    public static void readsRoadsJunctionsAndBuildings(GameTestHelper helper) throws Exception {
        OsmMap map = OsmMap.read(new ByteArrayInputStream(OSM.getBytes(StandardCharsets.UTF_8)));
        RoadPlan plan = new RoadPlan();
        RoadPlan.MapImport mi = new RoadPlan.MapImport();
        mi.lat0 = LAT;
        mi.lon0 = LON;
        List<OsmMap.Outline> buildings = map.buildings(mi);
        helper.assertTrue(buildings.size() == 1, "one building, got " + buildings.size());
        helper.assertTrue(buildings.get(0).height() == 15, "3 floors at 1.8 should be 15 blocks, got " + buildings.get(0).height());
        // 20 m along the street is 36 blocks
        double w = buildings.get(0).x()[1] - buildings.get(0).x()[0];
        helper.assertTrue(Math.abs(w - 20 * RoadPlan.MapImport.SCALE) < 0.5, "20 m should be 36 blocks, got " + w);
        int roads = map.roads(plan, mi, buildings, -2000, -2000, 2000, 2000);
        helper.assertTrue(roads >= 7, "too few roads: " + roads);
        RoadPlan.Node cross = nearest(plan, 0, 0);
        helper.assertTrue(cross.junction == RoadPlan.Junction.SIGNALS, "the crossroads should have lights, was " + cross.junction);
        RoadPlan.Node ring = nearest(plan, 300 * RoadPlan.MapImport.SCALE, 0);
        helper.assertTrue(ring.junction == RoadPlan.Junction.ROUNDABOUT, "no roundabout at the ring, was " + ring.junction);
        helper.assertTrue(plan.segmentsAt(ring.id).size() == 3, "the roundabout should have 3 arms, had " + plan.segmentsAt(ring.id).size());
        // the street past the house: pavement out to its front (5 m = 9 blocks from the middle of the road)
        int pavement = plan.segmentsAt(nearest(plan, -100 * RoadPlan.MapImport.SCALE, 0).id).get(0).style.pavement;
        helper.assertTrue(pavement == 4, "pavement should reach the house front (4), was " + pavement);
        helper.assertTrue(mi.firstId > 0 && mi.lastId >= mi.firstId, "ids not recorded");
        helper.succeed();
    }

    private static RoadPlan.Node nearest(RoadPlan plan, double x, double z) {
        RoadPlan.Node best = null;
        for (RoadPlan.Node n : plan.nodes) {
            if (best == null || Math.hypot(n.x - x, n.z - z) < Math.hypot(best.x - x, best.z - z)) {
                best = n;
            }
        }
        return best;
    }
}
