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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
    /** When each named road was last looked for on the ground (game time; not saved). */
    private final Map<MapData.Road, Long> checked = new java.util.IdentityHashMap<>();

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
    public static List<MapData.Road> namePlanRoads(ServerLevel level, com.ptmuk.road.plan.RoadPlan plan) {
        List<MapData.Road> added = new ArrayList<>();
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
            added.add(road);
        }
        if (!named.isEmpty()) {
            m.setDirty();
        }
        return added;
    }

    /** Take the names off every road that comes within `radius` blocks of (x, z). */
    public static String clearNames(ServerLevel level, int x, int z, int radius) {
        MapPlaces m = get(level);
        int before = m.roads.size();
        m.roads.removeIf(r -> near(r, x + 0.5, z + 0.5, radius));
        int n = before - m.roads.size();
        if (n > 0) {
            m.setDirty();
        }
        return n == 0 ? "No named roads there" : "Took the names off " + n + " road" + (n == 1 ? "" : "s");
    }

    /** Take names off the map again (a road build undone). */
    public static void forgetRoads(ServerLevel level, List<MapData.Road> names) {
        MapPlaces m = get(level);
        if (m.roads.removeIf(r -> names.stream().anyMatch(n -> n == r))) {
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

    /** The name of the nearest named road within `reach` blocks of (x, z), or null. */
    public static String nameNear(ServerLevel level, double x, double z, double reach) {
        String best = null;
        for (MapData.Road r : get(level).roads) {
            double d = distance(r, x, z);
            if (d < reach) {
                reach = d;
                best = r.name();
            }
        }
        return best;
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

    /** Everything for the map round a player (names of roads that have gone from round them are dropped first). */
    public static MapData gather(ServerPlayer player) {
        forgetGoneRoads(player.serverLevel(), player.getX(), player.getZ());
        return gather(player.serverLevel(), player.getX(), player.getZ());
    }

    /**
     * Drop the names of roads that aren't there any more (a test town taken away): a road whose ground
     * is loaded at three or more places along it with nothing but natural ground (grass, dirt, sand,
     * stone, water, trees) round any of them. A road of any block someone built keeps its name. Each
     * road is looked at once every ten seconds at most, and nothing is loaded to do it.
     */
    public static void forgetGoneRoads(ServerLevel level, double px, double pz) {
        MapPlaces m = get(level);
        long now = level.getGameTime();
        boolean dropped = m.roads.removeIf(r -> {
            if (!near(r, px, pz, REACH) || now - m.checked.getOrDefault(r, Long.MIN_VALUE / 2) < 200) {
                return false;
            }
            m.checked.put(r, now);
            int loaded = 0;
            for (double f = 0.1; f < 1; f += 0.2) {
                double[] at = along(r, f);
                Boolean here = builtNear(level, (int) Math.floor(at[0]), (int) Math.floor(at[1]));
                if (here == null) {
                    continue;
                }
                if (here) {
                    return false;
                }
                loaded++;
            }
            return loaded >= 3;
        });
        if (dropped) {
            m.checked.keySet().retainAll(new java.util.HashSet<>(m.roads));
            m.setDirty();
        }
    }

    /** The point a fraction f of the way along a road's middle line. */
    private static double[] along(MapData.Road r, double f) {
        double total = 0;
        for (int i = 0; i + 1 < r.x().length; i++) {
            total += Math.hypot(r.x()[i + 1] - r.x()[i], r.z()[i + 1] - r.z()[i]);
        }
        double want = total * f;
        for (int i = 0; i + 1 < r.x().length; i++) {
            double l = Math.hypot(r.x()[i + 1] - r.x()[i], r.z()[i + 1] - r.z()[i]);
            if (want <= l || i + 2 == r.x().length) {
                double t = l == 0 ? 0 : Math.min(1, want / l);
                return new double[]{r.x()[i] + (r.x()[i + 1] - r.x()[i]) * t, r.z()[i] + (r.z()[i + 1] - r.z()[i]) * t};
            }
            want -= l;
        }
        return new double[]{r.x()[0], r.z()[0]};
    }

    /** Whether anything built (not natural ground) is on top within a few blocks of (x, z), or null if that ground isn't loaded. */
    private static Boolean builtNear(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                LevelChunk chunk = level.getChunkSource().getChunkNow((x + dx) >> 4, (z + dz) >> 4);
                if (chunk == null) {
                    return null;
                }
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (x + dx) & 15, (z + dz) & 15);   // the top block's own height
                BlockState s = chunk.getBlockState(p.set(x + dx, top, z + dz));
                if (!(s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.LOGS)
                        || s.getFluidState().is(FluidTags.WATER) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.ICE))) {
                    return true;
                }
            }
        }
        return false;
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
