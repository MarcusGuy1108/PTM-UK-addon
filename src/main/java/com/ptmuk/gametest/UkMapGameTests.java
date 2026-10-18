package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.map.MapData;
import com.ptmuk.map.MapPlaces;
import com.ptmuk.map.NameplateBlockEntity;
import com.ptmuk.map.RouteFinder;
import com.ptmuk.network.RouteMessage;
import com.ptmuk.registry.ModBlocks;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.stream.Collectors;
import com.rinventor.ptm2.core.properties.VDMemoryIDs;
import com.rinventor.ptm2.dimension.virtual.storage.VDStorageHandler;
import com.rinventor.ptm2.dimension.virtual.types.VDDistrict;
import com.rinventor.ptm2.dimension.virtual.types.VDPlatform;
import com.rinventor.ptm2.dimension.virtual.types.VDStation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The map's roads and directions, away from the other tests: a long L of tarmac, its first leg named
 * on the map and its second by a street nameplate; directions from one end to the other go east
 * along High Street and turn right onto Broadway.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkMapGameTests {
    /** On the flat test world's ground, so the road is open ground (not a roof) to the road tracer. */
    private static final int Y = -1, Z = -700;

    private UkMapGameTests() {
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 200, batch = "map")
    public static void namesRoadsAndGivesDirections(GameTestHelper helper) {
        Block tarmac = ForgeRegistries.BLOCKS.getValue(PtmUk.id("tarmac"));
        // the first leg is long, so the search reaches chunks it didn't read round the two ends
        for (int x = -4; x <= 160; x++) {
            for (int z = -4; z <= 50; z++) {
                boolean road = (z >= 0 && z <= 4 && x <= 154) || (x >= 150 && x <= 154 && z <= 44);
                helper.setBlock(x, Y, Z + z, road ? tarmac : Blocks.GRASS_BLOCK);
                for (int y = 1; y < 16; y++) {
                    helper.setBlock(x, Y + y, Z + z, Blocks.AIR);
                }
            }
        }
        var level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, Y, Z + 2)), corner = helper.absolutePos(new BlockPos(152, Y, Z + 2));
        BlockPos b = helper.absolutePos(new BlockPos(152, Y, Z + 42));
        MapPlaces.nameRoad(level, a.getX() + 10, a.getZ(), "");               // left over from an earlier run
        MapPlaces.nameRoad(level, b.getX(), b.getZ() - 10, "");
        String said = MapPlaces.nameRoad(level, a.getX() + 10, a.getZ(), "High Street");
        helper.assertTrue(said.startsWith("Named"), "naming the first leg: " + said);
        // a nameplate on the verge of the second leg names it
        BlockPos plate = new BlockPos(156, Y + 1, Z + 30);
        helper.setBlock(plate, ModBlocks.NAMEPLATE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "Broadway");
        ((NameplateBlockEntity) helper.getBlockEntity(plate)).applyEdit(tag);
        helper.assertTrue(MapPlaces.roads(level).stream().anyMatch(r -> r.name().equals("Broadway")
                && MapPlaces.distance(r, b.getX() + 0.5, b.getZ() - 10) < 4), "the nameplate should name the second leg");

        RouteMessage route = RouteFinder.findNow(level, a.getX() + 0.5, a.getZ() + 0.5, b.getX() + 0.5, b.getZ() + 0.5, "the end");
        helper.assertTrue(route.error().isEmpty(), "no route: " + route.error());
        String steps = route.steps().stream().map(RouteMessage.Step::text).collect(Collectors.joining(" / "));
        helper.assertTrue(steps.startsWith("Head east on High Street"), "first step: " + steps);
        helper.assertTrue(steps.contains("Turn right onto Broadway"), "the turn: " + steps);
        helper.assertTrue(steps.endsWith("Arrive at the end"), "last step: " + steps);
        boolean turnsAtCorner = false;
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < route.x().length; i++) {
            turnsAtCorner |= Math.hypot(route.x()[i] - (corner.getX() + 0.5), route.z()[i] - (corner.getZ() + 0.5)) < 6;   // it may trim the corner across the road
            pts.append(" ").append(Math.round(route.x()[i] - a.getX())).append(",").append(Math.round(route.z()[i] - a.getZ()));
        }
        helper.assertTrue(turnsAtCorner, "the route should turn at the corner (150, 0 from the start), not cut it:" + pts);
        helper.succeed();
    }

    /** A PTM2 station, stop and district, stored the way PTM2 stores them, come through onto the map. */
    @GameTest(template = "empty5x4x5", batch = "map")
    public static void showsPtm2StopsAndDistricts(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        VDStation station = new VDStation(o.getX() + 10, o.getZ() + 10, o.getX() + 30, o.getZ() + 20, true);
        station.name = "Test Bus Station";
        VDPlatform stop = new VDPlatform(o.getX() + 40, o.getZ() + 10, o.getX() + 44, o.getZ() + 12, true);
        stop.name = "Test Stop A";
        VDDistrict district = new VDDistrict(o.getX() - 50, o.getZ() - 50, o.getX() + 50, o.getZ() + 50);
        district.name = "Test District";
        VDStorageHandler.add(level, station.toString());
        VDStorageHandler.add(level, stop.toString());
        VDStorageHandler.add(level, district.toString());
        try {
            MapData d = MapPlaces.gather(level, o.getX(), o.getZ());
            helper.assertTrue(d.places.stream().anyMatch(p -> p.kind() == MapData.Kind.STATION && p.name().equals("Test Bus Station")),
                    "the station should be on the map: " + d.places);
            helper.assertTrue(d.places.stream().anyMatch(p -> p.kind() == MapData.Kind.STOP && p.name().equals("Test Stop A")),
                    "the stop should be on the map: " + d.places);
            helper.assertTrue(d.areas.stream().anyMatch(a -> a.kind() == MapData.AreaKind.DISTRICT && a.name().equals("Test District")),
                    "the district should be on the map: " + d.areas);
        } finally {
            removeNamed(level, VDMemoryIDs.STATION, "Test Bus Station");
            removeNamed(level, VDMemoryIDs.PLATFORM, "Test Stop A");
            removeNamed(level, VDMemoryIDs.DISTRICT, "Test District");
        }
        helper.succeed();
    }

    /** Roads built from a real map keep their names: the pieces of one road join into one named line. */
    @GameTest(template = "empty5x4x5", batch = "map")
    public static void keepsImportedRoadNames(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(0, 0, Z - 300));
        RoadPlan plan = new RoadPlan();
        RoadPlan.Node a = plan.addNode(o.getX(), o.getZ()), b = plan.addNode(o.getX() + 40, o.getZ()), c = plan.addNode(o.getX() + 80, o.getZ() + 10);
        plan.addSegment(b.id, c.id, RoadStyle.mainRoad()).name = "Imported Avenue";
        plan.addSegment(a.id, b.id, RoadStyle.mainRoad()).name = "Imported Avenue";
        MapPlaces.namePlanRoads(level, plan);
        var named = MapPlaces.roads(level).stream().filter(r -> r.name().equals("Imported Avenue")
                && MapPlaces.distance(r, o.getX() + 40.5, o.getZ() + 0.5) < 1).toList();
        helper.assertTrue(named.size() == 1 && named.get(0).x().length == 3, "the two pieces should make one three-point road, got "
                + named.stream().map(r -> r.x().length).toList());
        MapPlaces.namePlanRoads(level, plan);
        helper.assertTrue(MapPlaces.roads(level).stream().filter(r -> r.name().equals("Imported Avenue")
                && MapPlaces.distance(r, o.getX() + 40.5, o.getZ() + 0.5) < 1).count() == 1, "building again shouldn't name it twice");
        helper.succeed();
    }

    private static void removeNamed(net.minecraft.server.level.ServerLevel level, VDMemoryIDs kind, String name) {
        for (var e : java.util.List.copyOf(VDStorageHandler.getAll(level, kind).entrySet())) {
            if (e.getValue() != null && e.getValue().contains(name)) {
                VDStorageHandler.remove(level, e.getKey(), kind);
            }
        }
    }
}
