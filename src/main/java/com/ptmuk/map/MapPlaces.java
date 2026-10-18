package com.ptmuk.map;

import com.ptmuk.road.OsmMap;
import com.ptmuk.road.RoadTrace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The map's places in a world: the shops and petrol stations (from their signs, as they load or
 * are edited, so they stay on the map when you're far away) and the roads people have named.
 * PTM2's stops, stations, zones and vehicles are read from PTM2 as they are (Ptm2Map).
 */
public final class MapPlaces extends SavedData {
    /** How far round a player the map is sent. */
    public static final int REACH = 2048;
    private static final int MAX_ROAD_POINTS = 2000;

    private final Map<BlockPos, MapData.Place> places = new HashMap<>();
    private final List<MapData.Road> roads = new ArrayList<>();

    static MapPlaces get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MapPlaces::load, MapPlaces::new, "ptmuk_map");
    }

    /** A sign's name for the map (blank takes it off). Safe to call from either side; only the server keeps it. */
    public static void note(Level level, BlockPos pos, MapData.Kind kind, String name) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        MapPlaces m = get(server);
        MapData.Place now = name == null || name.isBlank() ? null : new MapData.Place(kind, name.trim(), pos.getX(), pos.getZ());
        MapData.Place was = now == null ? m.places.remove(pos) : m.places.put(pos.immutable(), now);
        if (!java.util.Objects.equals(was, now)) {
            m.setDirty();
        }
    }

    public static void forget(Level level, BlockPos pos) {
        note(level, pos, MapData.Kind.SHOP, "");
    }

    /**
     * Name the road through (x, z), followed both ways from there; a blank name takes the name off
     * whatever named road passes there. Returns what to tell the player.
     */
    public static String nameRoad(ServerLevel level, int x, int z, String name) {
        MapPlaces m = get(level);
        boolean removed = m.roads.removeIf(r -> near(r, x, z, 4));
        if (name.isBlank()) {
            m.setDirty();
            return removed ? "Took the road's name off" : "There's no named road there";
        }
        RoadTrace.Result t = RoadTrace.traceTop(level, x, z, (bx, bz) -> RouteFinder.top(level, bx, bz));
        if (t == null || t.x().length < 2) {
            return "Couldn't follow a road from there: click the middle of one";
        }
        boolean[] keep = OsmMap.simplify(t.x(), t.z(), 0, t.x().length - 1, 1.5);
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i < keep.length && pts.size() < MAX_ROAD_POINTS; i++) {
            if (keep[i]) {
                pts.add(new float[]{(float) t.x()[i], (float) t.z()[i]});
            }
        }
        float[] xs = new float[pts.size()], zs = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            xs[i] = pts.get(i)[0];
            zs[i] = pts.get(i)[1];
        }
        m.roads.add(new MapData.Road(name.trim(), xs, zs));
        m.setDirty();
        return "Named " + name.trim();
    }

    /**
     * Roads built from a real map keep their names: the plan's newly built segments that have one,
     * joined end to end into one line per road, go on the map (replacing the same name there before).
     */
    public static void namePlanRoads(ServerLevel level, com.ptmuk.road.plan.RoadPlan plan) {
        Map<Integer, com.ptmuk.road.plan.RoadPlan.Node> nodes = new HashMap<>();
        plan.nodes.forEach(n -> nodes.put(n.id, n));
        List<com.ptmuk.road.plan.RoadPlan.Segment> named = new ArrayList<>();
        for (var s : plan.segments) {
            if (!s.built && s.name != null && !s.name.isBlank() && nodes.containsKey(s.a) && nodes.containsKey(s.b)) {
                named.add(s);
            }
        }
        MapPlaces m = get(level);
        java.util.Set<com.ptmuk.road.plan.RoadPlan.Segment> used = new java.util.HashSet<>();
        for (var first : named) {
            if (!used.add(first)) {
                continue;
            }
            // grow a chain both ways through nodes where exactly one more piece of the same road carries on
            java.util.Deque<Integer> chain = new java.util.ArrayDeque<>(List.of(first.a, first.b));
            for (boolean grew = true; grew; ) {
                grew = false;
                for (var s : named) {
                    if (used.contains(s) || !s.name.equals(first.name)) {
                        continue;
                    }
                    int end = s.a == chain.getLast() || s.b == chain.getLast() ? chain.getLast() : s.a == chain.getFirst() || s.b == chain.getFirst() ? chain.getFirst() : -1;
                    if (end < 0) {
                        continue;
                    }
                    int other = s.a == end ? s.b : s.a;
                    if (end == chain.getLast()) {
                        chain.addLast(other);
                    } else {
                        chain.addFirst(other);
                    }
                    used.add(s);
                    grew = true;
                }
            }
            float[] x = new float[chain.size()], z = new float[chain.size()];
            int i = 0;
            for (int id : chain) {
                x[i] = (float) nodes.get(id).x + 0.5f;
                z[i++] = (float) nodes.get(id).z + 0.5f;
            }
            MapData.Road road = new MapData.Road(first.name.trim(), x, z);
            m.roads.removeIf(r -> r.name().equals(road.name()) && near(r, x[x.length / 2], z[z.length / 2], 6));
            m.roads.add(road);
        }
        if (!named.isEmpty()) {
            m.setDirty();
        }
    }

    /** Name the road nearest a street nameplate (within a dozen blocks). */
    public static void nameRoadNear(ServerLevel level, BlockPos pos, String name) {
        for (int r = 1; r <= 12; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (RouteFinder.road(RouteFinder.top(level, pos.getX() + dx, pos.getZ() + dz))) {
                        nameRoad(level, pos.getX() + dx, pos.getZ() + dz, name);
                        return;
                    }
                }
            }
        }
    }

    /** Whether the road passes within `d` blocks of (x, z). */
    static boolean near(MapData.Road r, double x, double z, double d) {
        return distance(r, x, z) <= d;
    }

    /** How far (x, z) is from the road's middle line. */
    public static double distance(MapData.Road r, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < r.x().length; i++) {
            double ax = r.x()[i], az = r.z()[i], bx = r.x()[i + 1], bz = r.z()[i + 1];
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            best = Math.min(best, Math.hypot(x - (ax + dx * t), z - (az + dz * t)));
        }
        return best;
    }

    /** The named roads in a world (for directions). */
    public static List<MapData.Road> roads(ServerLevel level) {
        return get(level).roads;
    }

    /** Everything for the map round a player. */
    public static MapData gather(ServerPlayer player) {
        return gather(player.serverLevel(), player.getX(), player.getZ());
    }

    /** Everything for the map round a point. */
    public static MapData gather(ServerLevel level, double px, double pz) {
        MapPlaces m = get(level);
        MapData d = new MapData();
        for (MapData.Place p : m.places.values()) {
            if (Math.abs(p.x() - px) < REACH && Math.abs(p.z() - pz) < REACH) {
                d.places.add(p);
            }
        }
        for (MapData.Road r : m.roads) {
            if (near(r, px, pz, REACH)) {
                d.roads.add(r);
            }
        }
        Ptm2Map.add(level, px, pz, REACH, d);
        return d;
    }

    static MapPlaces load(CompoundTag tag) {
        MapPlaces m = new MapPlaces();
        for (Tag t : tag.getList("places", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                BlockPos pos = BlockPos.of(c.getLong("pos"));
                m.places.put(pos, new MapData.Place(MapData.Kind.valueOf(c.getString("kind")), c.getString("name"), pos.getX(), pos.getZ()));
            } catch (IllegalArgumentException ignored) {
                // a kind from a newer version
            }
        }
        for (Tag t : tag.getList("roads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ListTag pts = c.getList("points", Tag.TAG_FLOAT);
            float[] x = new float[pts.size() / 2], z = new float[pts.size() / 2];
            for (int i = 0; i < x.length; i++) {
                x[i] = pts.getFloat(i * 2);
                z[i] = pts.getFloat(i * 2 + 1);
            }
            if (x.length >= 2) {
                m.roads.add(new MapData.Road(c.getString("name"), x, z));
            }
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        places.forEach((pos, p) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            c.putString("kind", p.kind().name());
            c.putString("name", p.name());
            list.add(c);
        });
        tag.put("places", list);
        ListTag rl = new ListTag();
        for (MapData.Road r : roads) {
            CompoundTag c = new CompoundTag();
            c.putString("name", r.name());
            ListTag pts = new ListTag();
            for (int i = 0; i < r.x().length; i++) {
                pts.add(net.minecraft.nbt.FloatTag.valueOf(r.x()[i]));
                pts.add(net.minecraft.nbt.FloatTag.valueOf(r.z()[i]));
            }
            c.put("points", pts);
            rl.add(c);
        }
        tag.put("roads", rl);
        return tag;
    }
}
