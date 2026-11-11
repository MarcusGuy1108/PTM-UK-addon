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

    /** A map's bus stops come in as the plan's, named as on the map, on the side of the road they're on. */
    @GameTest(template = "empty5x4x5")
    public static void readsBusStops(GameTestHelper helper) throws Exception {
        String osm = "<?xml version=\"1.0\"?><osm version=\"0.6\">"
                + node(1, -100, 0, "") + node(2, 100, 0, "")
                + node(3, 20, 6, tag("highway", "bus_stop") + tag("name", "Market Street"))
                + node(4, -30, -6, tag("highway", "bus_stop"))
                + way(100, tag("highway", "secondary") + tag("name", "Station Road"), 1, 2) + "</osm>";
        OsmMap map = OsmMap.read(new ByteArrayInputStream(osm.getBytes(StandardCharsets.UTF_8)));
        RoadPlan plan = new RoadPlan();
        RoadPlan.MapImport mi = new RoadPlan.MapImport();
        mi.lat0 = LAT;
        mi.lon0 = LON;
        map.roads(plan, mi, map.buildings(mi), -2000, -2000, 2000, 2000);
        helper.assertTrue(map.busStops(plan, mi) == 2 && plan.busStops.size() == 2, "bus stops: " + plan.busStops.size());
        com.ptmuk.road.layout.Layout l = com.ptmuk.road.layout.Layout.of(plan, com.ptmuk.road.layout.Terrain.flat(64));
        helper.assertTrue(l.busStopZones.size() == 2, "laid out " + l.busStopZones.size() + " stops");
        for (com.ptmuk.road.layout.Layout.BusStopZone z : l.busStopZones) {
            double mz = (z.z0() + z.z1()) / 2.0;
            boolean north = z.name().equals("Market Street");
            helper.assertTrue(north ? mz < 0 : mz > 0 && !z.name().isBlank(), z.name() + "'s stop is on the wrong side: " + z);
        }
        helper.succeed();
    }

    /**
     * A map fetched from Overpass: the query asks for roads, buildings and bus stops in a box the
     * right size round the place; and Overpass's answer (no bounds of its own) reads, centred on its points.
     */
    @GameTest(template = "empty5x4x5")
    public static void fetchedMapsRead(GameTestHelper helper) throws Exception {
        double[] b = OsmMap.box(LAT, LON, 500);
        helper.assertTrue(Math.abs((b[2] - b[0]) * 111250 - 1000) < 1 && Math.abs((b[3] - b[1]) * 111320 * Math.cos(Math.toRadians(LAT)) - 1000) < 1,
                "the box isn't 1 km across: " + java.util.Arrays.toString(b));
        String q = OsmMap.overpassQuery(b[0], b[1], b[2], b[3]);
        for (String want : new String[]{"way[\"highway\"]", "way[\"building\"]", "bus_stop", "(._;>;);out body;", String.format(java.util.Locale.ROOT, "%.6f", b[0])}) {
            helper.assertTrue(q.contains(want), "the query has no " + want + ": " + q);
        }
        String answer = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><osm version=\"0.6\" generator=\"Overpass API\">"
                + "<note>The data included in this document is from www.openstreetmap.org.</note>"
                + node(1, -100, 0, "") + node(2, 100, 0, "") + way(100, tag("highway", "residential"), 1, 2) + "</osm>";
        OsmMap map = OsmMap.read(new ByteArrayInputStream(answer.getBytes(StandardCharsets.UTF_8)));
        helper.assertTrue(Math.abs(map.midLat() - LAT) < 1e-4 && Math.abs(map.midLon() - LON) < 1e-4, "not centred on its points: " + map.midLat() + ", " + map.midLon());
        helper.succeed();
    }

    /** A street with a terrace of three houses (tiled roofs, the map says) and a stone shop on one side of it. */
    private static final String STREET = "<?xml version=\"1.0\"?><osm version=\"0.6\">"
            + node(1, -20, 0, "") + node(2, 45, 0, "")
            + node(10, 0, -4, "") + node(11, 5, -4, "") + node(12, 10, -4, "") + node(13, 15, -4, "")
            + node(14, 15, -12, "") + node(15, 10, -12, "") + node(16, 5, -12, "") + node(17, 0, -12, "")
            + node(20, 22, -4, "") + node(21, 34, -4, "") + node(22, 34, -14, "") + node(23, 22, -14, "")
            + way(100, tag("highway", "residential"), 1, 2)
            + way(101, tag("building", "house") + tag("roof:material", "roof_tiles"), 10, 11, 16, 17, 10)
            + way(102, tag("building", "house") + tag("roof:material", "roof_tiles"), 11, 12, 15, 16, 11)
            + way(103, tag("building", "house") + tag("roof:material", "roof_tiles"), 12, 13, 14, 15, 12)
            + way(104, tag("building", "retail") + tag("building:material", "stone"), 20, 21, 22, 23, 20)
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
        int doors = 0, windows = 0, pots = 0, shopWindows = 0, stone = 0, tiles = 0, otherRoofs = 0;
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
                    stone += id.equals("ptmuk:portland_stone") ? 1 : 0;
                    tiles += id.equals("ptmuk:roof_clay_tiles_stairs") ? 1 : 0;
                    otherRoofs += id.equals("ptmuk:roof_slate_stairs") || id.equals("ptmuk:roof_concrete_tiles_stairs") ? 1 : 0;
                    deck |= s.is(Blocks.GRAY_CONCRETE);
                }
            }
        }
        helper.assertTrue(doors == 4, "each building should have a front door, found " + doors);
        helper.assertTrue(windows >= 12, "too few house windows: " + windows);
        helper.assertTrue(shopWindows >= 4, "the shop should have a shop window, found " + shopWindows);
        helper.assertTrue(pots == 3, "each house should have a chimney, found " + pots);
        helper.assertTrue(deck, "the shop should have a flat roof");
        helper.assertTrue(stone > 20, "the map says the shop's stone, found " + stone + " stone blocks");
        helper.assertTrue(tiles > 20 && otherRoofs == 0, "the map says the terrace has tiled roofs: " + tiles + " tiles, " + otherRoofs + " others");
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

    /**
     * Real heights from the LIDAR survey: its GeoTIFFs read (big-endian 48-pixel tiles placed by a
     * model transformation as the service sends them, and little-endian strips placed by pixel
     * scale and tie point, with no-data gaps), and a street measured from them: a pitched house
     * (eaves 5 m, ridge 9 m), a flat-roofed block 12 m tall, and an outline with nothing standing
     * in it. The map's buildings then get those heights and roofs; saved and read back the same.
     */
    @GameTest(template = "empty5x4x5")
    public static void measuresRealHeights(GameTestHelper helper) throws Exception {
        int w = STREET_W, h = STREET_H;
        double dLat = 1 / 111250.0, dLon = 1 / (111320.0 * Math.cos(Math.toRadians(LAT))), lat0 = LAT + 20 * dLat, lon0 = LON - 20 * dLon;
        float[][] heights = streetHeights();
        float[] top = heights[0], ground = heights[1];
        com.ptmuk.road.Lidar.Grid t = com.ptmuk.road.Lidar.tiff(geoTiff(top, w, h, lat0, lon0, dLat, dLon, true));
        com.ptmuk.road.Lidar.Grid g = com.ptmuk.road.Lidar.tiff(geoTiff(ground, w, h, lat0, lon0, dLat, dLon, false));
        helper.assertTrue(t != null && g != null && t.w() == w && t.h() == h && g.w() == w && g.h() == h, "the GeoTIFFs didn't read");
        helper.assertTrue(Math.abs(t.lat0() - lat0) < 1e-9 && Math.abs(g.lon0() - lon0) < 1e-9 && Math.abs(g.dLat() - dLat) < 1e-12,
                "placed wrong: " + t + " / " + g);
        helper.assertTrue(Float.isNaN(t.v()[30 * w + 109]) && t.v()[59 * w + 50] == top[59 * w + 50] && g.v()[59 * w + 109] == ground[59 * w + 109],
                "the heights (or the gaps) didn't come through, in the last tile and the last strip");
        com.ptmuk.road.Lidar lidar = new com.ptmuk.road.Lidar();
        lidar.add(t, g);
        com.ptmuk.road.Lidar.Shape house = lidar.shape(lats(-12, -4, -4, -12), lons(5, 5, 10, 10));
        com.ptmuk.road.Lidar.Shape block = lidar.shape(lats(-14, -4, -4, -14), lons(22, 22, 34, 34));
        helper.assertTrue(house != null && house.pitched() && house.eaves() >= 5 && house.eaves() <= 7 && house.top() >= 8 && house.top() <= 9.01,
                "the house should be pitched, eaves about 5 m and ridge 9: " + house);
        helper.assertTrue(block != null && block.roof().equals("flat") && Math.abs(block.eaves() - 12) < 0.01, "the block should be flat at 12 m: " + block);
        helper.assertTrue(lidar.shape(lats(-12, -4, -4, -12), lons(40, 40, 50, 50)) == null, "nothing stands in the garden");
        var bytes = new java.io.ByteArrayOutputStream();
        lidar.write(bytes);
        com.ptmuk.road.Lidar again = com.ptmuk.road.Lidar.read(new ByteArrayInputStream(bytes.toByteArray()));
        helper.assertTrue(house.equals(again.shape(lats(-12, -4, -4, -12), lons(5, 5, 10, 10))), "not the same read back");
        // the map's buildings: walls up to the eaves, roofs as measured; one with nothing there guessed as before
        String osm = "<?xml version=\"1.0\"?><osm version=\"0.6\">"
                + node(1, 5, -12, "") + node(2, 5, -4, "") + node(3, 10, -4, "") + node(4, 10, -12, "")
                + node(5, 22, -14, "") + node(6, 22, -4, "") + node(7, 34, -4, "") + node(8, 34, -14, "")
                + node(9, 40, -12, "") + node(10, 40, -4, "") + node(11, 50, -4, "") + node(12, 50, -12, "")
                + way(100, tag("building", "house"), 1, 2, 3, 4, 1) + way(101, tag("building", "retail"), 5, 6, 7, 8, 5)
                + way(102, tag("building", "house"), 9, 10, 11, 12, 9) + "</osm>";
        OsmMap map = OsmMap.read(new ByteArrayInputStream(osm.getBytes(StandardCharsets.UTF_8)));
        map.lidar = again;
        RoadPlan.MapImport mi = new RoadPlan.MapImport();
        mi.lat0 = LAT;
        mi.lon0 = LON;
        List<OsmMap.Outline> b = map.buildings(mi);
        helper.assertTrue(b.size() == 3, "three buildings, got " + b.size());
        OsmMap.Outline oh = b.get(0), ob = b.get(1), og = b.get(2);
        helper.assertTrue(oh.roof().equals("gabled") && oh.height() == (int) Math.round(house.eaves() * mi.scale), "the house: " + oh.roof() + " " + oh.height());
        helper.assertTrue(ob.roof().equals("flat") && ob.height() == 22 && ob.floors() == 4, "the block: " + ob.roof() + " " + ob.height() + " " + ob.floors());
        helper.assertTrue(og.roof().isEmpty() && og.height() == 10, "the empty one should be guessed: " + og.roof() + " " + og.height());
        helper.succeed();
    }

    private static final int STREET_W = 110, STREET_H = 60;

    /**
     * A street's LIDAR, a metre a pixel from 20 m west and 20 m north of the middle, on sloping
     * ground: the tops of things, then the ground. A house 5 m by 8 m, its ridge running east-west
     * (eaves 5 m, ridge 9 m); a flat-roofed block 12 m tall; and a church, its nave 14 m by 10 m
     * (eaves 6 m, ridge 11 m) with a tower 20 m tall in the middle. No survey past 85 m east.
     */
    private static float[][] streetHeights() {
        float[] top = new float[STREET_W * STREET_H], ground = new float[STREET_W * STREET_H];
        for (int r = 0; r < STREET_H; r++) {
            for (int c = 0; c < STREET_W; c++) {
                double east = c + 0.5 - 20, north = 20 - (r + 0.5);
                double building = east >= 5 && east <= 10 && north >= -12 && north <= -4 ? 5 + 4 - Math.abs(north + 8)
                        : east >= 22 && east <= 34 && north >= -14 && north <= -4 ? 12
                        : east >= 56 && east <= 60 && north >= -11 && north <= -7 ? 20
                        : east >= 52 && east <= 66 && north >= -14 && north <= -4 ? 6 + 5 - Math.abs(north + 9) : 0;
                ground[r * STREET_W + c] = (float) (50 + 0.05 * east);
                top[r * STREET_W + c] = east > 85 ? Float.NaN : (float) (ground[r * STREET_W + c] + building);
            }
        }
        return new float[][]{top, ground};
    }

    /**
     * Real roofs built from the LIDAR's shape, not a guess: the house's roof is gabled at its end
     * walls (rising to its ridge there, as the real one does, where a guessed roof is hipped) and
     * its ridge is at the real height; the church's tower stands up out of its roof, walled up
     * from the nave roof with no gaps. Built far away, high up on a slab of stone.
     */
    @GameTest(template = "empty5x4x5", timeoutTicks = 200)
    public static void buildsRealRoofShapes(GameTestHelper helper) throws Exception {
        double dLat = 1 / 111250.0, dLon = 1 / (111320.0 * Math.cos(Math.toRadians(LAT)));
        float[][] heights = streetHeights();
        com.ptmuk.road.Lidar lidar = new com.ptmuk.road.Lidar();
        lidar.add(new com.ptmuk.road.Lidar.Grid(LAT + 20 * dLat, LON - 20 * dLon, dLat, dLon, STREET_W, STREET_H, heights[0]),
                new com.ptmuk.road.Lidar.Grid(LAT + 20 * dLat, LON - 20 * dLon, dLat, dLon, STREET_W, STREET_H, heights[1]));
        String osm = "<?xml version=\"1.0\"?><osm version=\"0.6\">"
                + node(1, 5, -12, "") + node(2, 5, -4, "") + node(3, 10, -4, "") + node(4, 10, -12, "")
                + node(5, 52, -14, "") + node(6, 52, -4, "") + node(7, 66, -4, "") + node(8, 66, -14, "")
                + way(100, tag("building", "house"), 1, 2, 3, 4, 1) + way(101, tag("building", "church"), 5, 6, 7, 8, 5) + "</osm>";
        OsmMap map = OsmMap.read(new ByteArrayInputStream(osm.getBytes(StandardCharsets.UTF_8)));
        map.lidar = lidar;
        RoadPlan plan = new RoadPlan();
        RoadPlan.MapImport mi = new RoadPlan.MapImport();
        mi.lat0 = LAT;
        mi.lon0 = LON;
        mi.x = 7000;
        mi.z = 7000;
        plan.map = mi;
        List<OsmMap.Outline> outlines = map.buildings(mi);
        helper.assertTrue(outlines.size() == 2 && outlines.get(0).surface() != null && outlines.get(1).surface() != null,
                "both should have their real roof's shape");
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        int ground = 260;
        for (int x = 6990; x < 7130; x++) {
            for (int z = 6990; z < 7040; z++) {
                level.setBlock(new BlockPos(x, ground - 1, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        com.ptmuk.road.layout.Layout layout = com.ptmuk.road.layout.Layout.of(plan, com.ptmuk.road.layout.Terrain.flat(ground));
        MapBuildings.Batch batch = MapBuildings.unpack(MapBuildings.pack(MapBuildings.around(outlines, mi, 7050, 7010, RoadPlan.PART)));
        helper.assertTrue(batch != null && MapBuildings.buildAll(level, batch, layout, plan) == 2, "both should be built");
        // the house: x 7009..7018 (5..10 m east), z 7007..7021 (4..12 m south), its ridge along z 7014
        int gable = roofAt(level, 7009, 7014, ground), eaves = roofAt(level, 7009, 7008, ground), ridge = roofAt(level, 7013, 7014, ground);
        helper.assertTrue(gable >= eaves + 3, "the end wall should rise to a gable: " + gable + " at the ridge, " + eaves + " by the eaves");
        helper.assertTrue(ridge - ground >= 14 && ridge - ground <= 19, "the ridge should be about 9 m (16 blocks) up, is " + (ridge - ground));
        // the church's tower: x 7102..7109 (56..60 m east), z 7013..7019, its top 20 m (36 blocks) up
        helper.assertTrue(roofAt(level, 7105, 7016, ground) - ground >= 30, "the tower should stand up out of the roof, top at "
                + (roofAt(level, 7105, 7016, ground) - ground));
        // along the church through the tower: wherever the roof steps up, the step's face is walled, not open
        for (int x = 7096; x < 7116; x++) {
            int a = roofAt(level, x, 7016, ground), b = roofAt(level, x + 1, 7016, ground), hi = a > b ? x : x + 1;
            for (int y = Math.min(a, b) + 1; y < Math.max(a, b); y++) {
                helper.assertTrue(!level.getBlockState(new BlockPos(hi, y, 7016)).isAir(), "an open step in the roof at " + hi + ", " + y
                        + " (roofs " + a + " and " + b + ")");
            }
        }
        helper.succeed();
    }

    private static double[] lats(double... north) {
        double[] out = new double[north.length + 1];
        for (int i = 0; i <= north.length; i++) {
            out[i] = LAT + north[i % north.length] / 111250.0;
        }
        return out;
    }

    private static double[] lons(double... east) {
        double[] out = new double[east.length + 1];
        for (int i = 0; i <= east.length; i++) {
            out[i] = LON + east[i % east.length] / (111320.0 * Math.cos(Math.toRadians(LAT)));
        }
        return out;
    }

    /**
     * A GeoTIFF of heights: 32-bit floats, NaN written as the service's no-data value; big-endian
     * in 48-pixel tiles placed by a model transformation, or little-endian 16-row strips placed by
     * pixel scale and tie point.
     */
    private static byte[] geoTiff(float[] v, int w, int h, double lat0, double lon0, double dLat, double dLon, boolean tiled) {
        int bw = tiled ? 48 : w, bh = tiled ? 48 : 16, across = (w + bw - 1) / bw, blocks = across * ((h + bh - 1) / bh), size = bw * bh * 4;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8 + blocks * size + 4096)
                .order(tiled ? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) (tiled ? 'M' : 'I')).put(1, (byte) (tiled ? 'M' : 'I')).putShort(2, (short) 42);
        for (int k = 0; k < blocks; k++) {
            for (int r = 0; r < bh; r++) {
                for (int c = 0; c < bw; c++) {
                    int x = (k % across) * bw + c, y = (k / across) * bh + r;
                    float f = x < w && y < h ? v[y * w + x] : Float.NaN;
                    b.putFloat(8 + k * size + 4 * (r * bw + c), Float.isNaN(f) ? -Float.MAX_VALUE : f);
                }
            }
        }
        int at = 8 + blocks * size;                         // the arrays, then the directory
        int offsets = at, counts = at + 4 * blocks, geo = counts + 4 * blocks;
        for (int k = 0; k < blocks; k++) {
            b.putInt(offsets + 4 * k, 8 + k * size).putInt(counts + 4 * k, size);
        }
        double[] place = tiled ? new double[]{dLon, 0, 0, lon0, 0, -dLat, 0, lat0, 0, 0, 0, 0, 0, 0, 0, 1}
                : new double[]{dLon, dLat, 0, 0, 0, 0, lon0, lat0, 0};    // pixel scale (3), then the tie point (6)
        for (int i = 0; i < place.length; i++) {
            b.putDouble(geo + 8 * i, place[i]);
        }
        byte[] nodata = "-3.4028234663852886E38\0".getBytes(StandardCharsets.US_ASCII);
        int text = geo + 8 * place.length;
        b.put(text, nodata);
        int ifd = text + nodata.length + 1 & ~1;
        b.putInt(4, ifd);
        List<int[]> e = new java.util.ArrayList<>();          // tag, type, count, value or offset
        e.add(new int[]{256, 4, 1, w});
        e.add(new int[]{257, 4, 1, h});
        e.add(new int[]{258, 3, 1, 32});
        e.add(new int[]{259, 3, 1, 1});
        if (!tiled) {
            e.add(new int[]{273, 4, blocks, offsets});
            e.add(new int[]{278, 3, 1, bh});
            e.add(new int[]{279, 4, blocks, counts});
        } else {
            e.add(new int[]{322, 3, 1, bw});
            e.add(new int[]{323, 3, 1, bh});
            e.add(new int[]{324, 4, blocks, offsets});
            e.add(new int[]{325, 4, blocks, counts});
        }
        e.add(new int[]{339, 3, 1, 3});
        if (tiled) {
            e.add(new int[]{34264, 12, 16, geo});
        } else {
            e.add(new int[]{33550, 12, 3, geo});
            e.add(new int[]{33922, 12, 6, geo + 24});
        }
        e.add(new int[]{42113, 2, nodata.length, text});
        b.putShort(ifd, (short) e.size());
        for (int i = 0; i < e.size(); i++) {
            int[] x = e.get(i);
            int p = ifd + 2 + 12 * i;
            b.putShort(p, (short) x[0]).putShort(p + 2, (short) x[1]).putInt(p + 4, x[2]);
            if (x[1] == 3 && x[2] == 1) {
                b.putShort(p + 8, (short) x[3]);
            } else {
                b.putInt(p + 8, x[3]);
            }
        }
        b.putInt(ifd + 2 + 12 * e.size(), 0);
        return java.util.Arrays.copyOf(b.array(), ifd + 6 + 12 * e.size());
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
