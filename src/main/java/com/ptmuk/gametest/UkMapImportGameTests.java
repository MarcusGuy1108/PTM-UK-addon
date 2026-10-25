package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.MapBuildings;
import com.ptmuk.road.OsmMap;
import com.ptmuk.road.plan.RoadPlan;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

    /** A street with a terrace of three houses and a shop on one side of it. */
    private static final String STREET = "<?xml version=\"1.0\"?><osm version=\"0.6\">"
            + node(1, -20, 0, "") + node(2, 45, 0, "")
            + node(10, 0, -4, "") + node(11, 5, -4, "") + node(12, 10, -4, "") + node(13, 15, -4, "")
            + node(14, 15, -12, "") + node(15, 10, -12, "") + node(16, 5, -12, "") + node(17, 0, -12, "")
            + node(20, 22, -4, "") + node(21, 34, -4, "") + node(22, 34, -14, "") + node(23, 22, -14, "")
            + way(100, tag("highway", "residential"), 1, 2)
            + way(101, tag("building", "house"), 10, 11, 16, 17, 10)
            + way(102, tag("building", "house"), 11, 12, 15, 16, 11)
            + way(103, tag("building", "house"), 12, 13, 14, 15, 12)
            + way(104, tag("building", "retail"), 20, 21, 22, 23, 20)
            + "</osm>";

    /**
     * A map's buildings built from their outlines: the terrace gets one roof running over its
     * party walls (hipped only at its ends), front doors on the street, windows and chimneys; the
     * shop a flat roof and a shop window. Built high over the world on a slab of stone.
     */
    @GameTest(template = "empty5x4x5", timeoutTicks = 200)
    public static void buildsTheMapsBuildings(GameTestHelper helper) throws Exception {
        OsmMap map = OsmMap.read(new ByteArrayInputStream(STREET.getBytes(StandardCharsets.UTF_8)));
        RoadPlan plan = new RoadPlan();
        RoadPlan.MapImport mi = new RoadPlan.MapImport();
        mi.lat0 = LAT;
        mi.lon0 = LON;
        mi.x = 6000;
        mi.z = 6000;
        plan.map = mi;
        List<OsmMap.Outline> outlines = map.buildings(mi);
        map.roads(plan, mi, outlines, -1e9, -1e9, 1e9, 1e9);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        int ground = 260;
        for (int x = 5950; x < 6090; x++) {
            for (int z = 5960; z < 6040; z++) {
                level.setBlock(new BlockPos(x, ground - 1, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        com.ptmuk.road.layout.Layout layout = com.ptmuk.road.layout.Layout.of(plan, com.ptmuk.road.layout.Terrain.flat(ground));
        MapBuildings.Batch batch = MapBuildings.unpack(MapBuildings.pack(MapBuildings.around(outlines, mi, 6000, 6000, RoadPlan.PART)));
        helper.assertTrue(batch != null && batch.buildings.size() == 4, "four buildings should be sent");
        int built = MapBuildings.buildAll(level, batch, layout, plan);
        helper.assertTrue(built == 4, "four buildings should be built, got " + built);
        // the middle house of the terrace (5..10 m east, 4..12 m south of the road) and its party wall at 5 m,
        // on the front slope (the chimneys stand on the ridge by the party walls)
        int zMid = (int) Math.floor(mi.z(LAT - 6 / 111250.0));
        int roofParty = roofAt(level, (int) Math.floor(mi.x(LON + 5.2 / (111320.0 * Math.cos(Math.toRadians(LAT))))), zMid, ground);
        int roofMid = roofAt(level, (int) Math.floor(mi.x(LON + 7.5 / (111320.0 * Math.cos(Math.toRadians(LAT))))), zMid, ground);
        helper.assertTrue(roofParty == roofMid && roofMid > ground + 8, "the terrace roof should run on over the party wall: "
                + roofParty + " vs " + roofMid);
        int doors = 0, windows = 0, pots = 0, shopWindows = 0;
        boolean deck = false;
        for (int x = 5950; x < 6090; x++) {
            for (int z = 5960; z < 6040; z++) {
                for (int y = ground; y < ground + 30; y++) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock()).toString();
                    if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                            && s.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                        doors++;
                        helper.assertTrue(s.getValue(net.minecraft.world.level.block.DoorBlock.FACING) == net.minecraft.core.Direction.SOUTH,
                                "a front door should face the street (north of it), faces " + s.getValue(net.minecraft.world.level.block.DoorBlock.FACING));
                    }
                    windows += id.equals("ptmuk:sash_window") || id.equals("ptmuk:upvc_window") ? 1 : 0;
                    shopWindows += id.equals("ptmuk:shop_window") ? 1 : 0;
                    pots += s.is(Blocks.FLOWER_POT) ? 1 : 0;
                    deck |= s.is(Blocks.GRAY_CONCRETE);
                }
            }
        }
        helper.assertTrue(doors == 4, "each building should have a front door, found " + doors);
        helper.assertTrue(windows >= 12, "too few house windows: " + windows);
        helper.assertTrue(shopWindows >= 4, "the shop should have a shop window, found " + shopWindows);
        helper.assertTrue(pots == 3, "each house should have a chimney, found " + pots);
        helper.assertTrue(deck, "the shop should have a flat roof");
        helper.succeed();
    }

    /** The y of the top block in a column. */
    private static int roofAt(net.minecraft.server.level.ServerLevel level, int x, int z, int from) {
        int y = from + 40;
        while (y > from && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
            y--;
        }
        return y;
    }

    /**
     * A plan too big to build in one go (a road four thousand blocks long) is built a part at a
     * time: the part round you builds the roads with a point in it, the ones past them come
     * along already built so the junctions are laid out right, and what got built is marked on
     * the whole plan.
     */
    @GameTest(template = "empty5x4x5")
    public static void buildsABigMapAPartAtATime(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Node prev = plan.addNode(0, 0);
        for (int x = 100; x <= 4000; x += 100) {
            RoadPlan.Node n = plan.addNode(x, 0);
            plan.addSegment(prev.id, n.id, com.ptmuk.road.plan.RoadStyle.residential());
            prev = n;
        }
        helper.assertTrue(plan.tooBig(), "a road 4000 long should be too big for one go");
        RoadPlan part = plan.part(1000, 0, RoadPlan.PART);
        helper.assertTrue(!part.tooBig(), "a part should be small enough");
        helper.assertTrue(part.ownNodes.size() == 7 && part.ownSegments.size() == 8,
                "the part should build points 700..1300 and the 8 roads off them, got " + part.ownNodes.size() + " and " + part.ownSegments.size());
        helper.assertTrue(part.segments.size() == 10, "the roads past the part's should come along, got " + part.segments.size());
        com.ptmuk.road.layout.Layout l = com.ptmuk.road.layout.Layout.of(part, com.ptmuk.road.layout.Terrain.flat(64));
        helper.assertTrue(l.dirty[l.index(1000, 0)] && !l.dirty[l.index(575, 0)], "only the part's own roads should be built");
        helper.assertTrue(l.index(500, 0) < 0, "the roads past the part's should only come a little way");
        for (RoadPlan.Node n : part.nodes) {
            n.y = 64;
            n.built = true;
        }
        part.segments.forEach(sg -> sg.built = true);
        plan.builtFrom(part);
        long built = plan.segments.stream().filter(sg -> sg.built).count();
        helper.assertTrue(built == 8, "8 roads should be marked built, got " + built);
        helper.assertTrue(!nearest(plan, 600, 0).built && nearest(plan, 700, 0).built, "only the part's own points are built");
        helper.assertTrue(nearest(plan, 600, 0).y == 64, "the heights fixed in the part should be kept");
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
