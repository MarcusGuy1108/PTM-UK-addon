package com.ptmuk.road;

import com.google.gson.Gson;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.plan.RoadPlan;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A real map's buildings built from their outlines: brick (or render, stone, cladding) walls on
 * a floor level with the ground round them, windows storey by storey, a front door on the side
 * nearest a road, floors inside, and a roof: houses (and churches, and low flats) get a hipped
 * tiled roof that runs on over the houses next door, so a terrace has one roof hipped at its
 * ends, with a chimney each; shops, offices, sheds and big buildings get a flat roof behind a
 * parapet. What it is comes from the map's building tag (and its size where the map only says
 * "yes"); houses joined wall to wall look alike. The planner sends the outlines round you (it
 * has the map file; the server doesn't) just before it sends the plan to build.
 */
public final class MapBuildings {
    /** One building as sent from the planner: corners in quarter blocks (nudge added), and what the map says it is. */
    public static final class Footprint {
        public String kind = "yes", roof = "", material = "", roofMaterial = "";
        public int floors = 1, height = 6;
        public int[] x, z;
        /** Its real roof (see OsmMap.Surface), Base64, from block sx, sz, sw points across; "" if it has none. */
        public String surface = "";
        public int sx, sz, sw;
        transient byte[] roofs;
    }

    /** The buildings round where you build: the square they came from (x0, z0, x1, z1) and the map's scale. */
    public static final class Batch {
        public int[] box;
        public double scale = 1;
        public List<Footprint> buildings = new ArrayList<>();
    }

    private enum Use { HOUSE, FLATS, SHOP, OFFICE, INDUSTRIAL, CIVIC, CHURCH, SHED }

    private record Style(String wall, String roof, String window, String ground, String floor, String door) {
    }

    private static final class Shell {
        final Footprint fp;
        final long[] cells;
        final Set<Long> in = new HashSet<>();
        Use use;
        boolean pitched;
        Style style;
        int parent;

        Shell(Footprint fp, List<Long> cells, int index) {
            this.fp = fp;
            this.cells = cells.stream().mapToLong(Long::longValue).toArray();
            this.in.addAll(cells);
            this.parent = index;
        }
    }

    private static final Gson GSON = new Gson();
    private static final Map<UUID, Batch> PENDING = new HashMap<>();
    private static final int MAX_BUILDINGS = 20000, MAX_CORNERS = 2000, MAX_CELLS = 250 * 250;
    /** A roof goes up a block for every block in from the eaves (45 degrees), this high at most. */
    private static final int MAX_RISE = 8;
    /** A measured roof (a real one, from LIDAR) can go higher: 9 m over a house's eaves, more for a church tower or the like. */
    private static final int MAX_HOUSE_RISE = 16, MAX_MEASURED_RISE = 48;
    /** N, E, S, W: as Direction's horizontal order from north. */
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final Direction[] FACING = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final int[] box;
    private final List<Shell> shells = new ArrayList<>();
    private final Set<Long> occupied = new HashSet<>();
    private final Map<Long, Integer> rise;
    private final List<double[]> roads = new ArrayList<>();
    private WorldTerrain ground;
    private int next;

    // ------------------------------------------------------------------ planner side

    /** The middle of an outline, nudged. */
    public static double[] middle(com.ptmuk.road.OsmMap.Outline o, RoadPlan.MapImport mi) {
        double x = 0, z = 0;
        int n = o.x().length;
        for (int i = 0; i < n; i++) {
            x += o.x()[i];
            z += o.z()[i];
        }
        return new double[]{x / n + mi.nudgeX, z / n + mi.nudgeZ};
    }

    /** The map's buildings with their middles within half each way of (cx, cz) and not built yet; null if there are none. */
    public static Batch around(List<com.ptmuk.road.OsmMap.Outline> outlines, RoadPlan.MapImport mi, double cx, double cz, int half) {
        Batch b = new Batch();
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        b.box = new int[]{x - half, z - half, x + half, z + half};
        b.scale = mi.scale;
        for (com.ptmuk.road.OsmMap.Outline o : outlines) {
            double[] m = middle(o, mi);
            if (m[0] < b.box[0] || m[0] >= b.box[2] || m[1] < b.box[1] || m[1] >= b.box[3] || mi.buildingsBuilt(m[0], m[1])) {
                continue;
            }
            Footprint f = new Footprint();
            f.kind = o.kind();
            f.roof = o.roof();
            f.material = o.material();
            f.roofMaterial = o.roofMaterial();
            f.floors = o.floors();
            f.height = o.height();
            if (o.surface() != null) {
                f.surface = java.util.Base64.getEncoder().encodeToString(o.surface().v());
                f.sx = o.surface().x0() + mi.nudgeX;
                f.sz = o.surface().z0() + mi.nudgeZ;
                f.sw = o.surface().w();
            }
            f.x = new int[o.x().length];
            f.z = new int[o.z().length];
            for (int i = 0; i < f.x.length; i++) {
                f.x[i] = (int) Math.round((o.x()[i] + mi.nudgeX) * 4);
                f.z[i] = (int) Math.round((o.z()[i] + mi.nudgeZ) * 4);
            }
            b.buildings.add(f);
        }
        return b.buildings.isEmpty() ? null : b;
    }

    public static byte[] pack(Batch b) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DeflaterOutputStream z = new DeflaterOutputStream(out)) {
                z.write(GSON.toJson(b).getBytes(StandardCharsets.UTF_8));
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Unpacks what a client sent, keeping only buildings that make sense; null if nothing does. */
    public static Batch unpack(byte[] data) {
        Batch b;
        try (InflaterInputStream z = new InflaterInputStream(new ByteArrayInputStream(data))) {
            b = GSON.fromJson(new String(z.readAllBytes(), StandardCharsets.UTF_8), Batch.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (b == null || b.box == null || b.box.length != 4 || b.buildings == null) {
            return null;
        }
        b.scale = Double.isFinite(b.scale) ? Math.max(0.5, Math.min(5, b.scale)) : 1;
        b.buildings.removeIf(f -> f == null || f.x == null || f.z == null || f.x.length != f.z.length || f.x.length < 3
                || f.x.length > MAX_CORNERS);
        if (b.buildings.size() > MAX_BUILDINGS) {
            b.buildings = new ArrayList<>(b.buildings.subList(0, MAX_BUILDINGS));
        }
        for (Footprint f : b.buildings) {
            f.kind = f.kind == null ? "yes" : f.kind;
            f.roof = f.roof == null ? "" : f.roof;
            f.material = f.material == null ? "" : f.material;
            f.roofMaterial = f.roofMaterial == null ? "" : f.roofMaterial;
            f.height = Math.max(3, Math.min(200, f.height));
            f.floors = Math.max(1, Math.min(60, f.floors));
            try {
                byte[] r = f.surface == null || f.surface.isEmpty() ? null : java.util.Base64.getDecoder().decode(f.surface);
                f.roofs = r != null && f.sw > 0 && r.length % f.sw == 0 && r.length <= MAX_CELLS ? r : null;
            } catch (IllegalArgumentException e) {
                f.roofs = null;                                 // not Base64: build it without
            }
            f.surface = null;
        }
        return b.buildings.isEmpty() ? null : b;
    }

    /** The part of a batch with its buildings' middles within half each way of (cx, cz); null if none. */
    static Batch within(Batch all, double cx, double cz, int half) {
        if (all == null) {
            return null;
        }
        Batch b = new Batch();
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        b.box = new int[]{x - half, z - half, x + half, z + half};
        b.scale = all.scale;
        for (Footprint f : all.buildings) {
            double mx = 0, mz = 0;
            for (int i = 0; i < f.x.length; i++) {
                mx += f.x[i] / 4.0;
                mz += f.z[i] / 4.0;
            }
            mx /= f.x.length;
            mz /= f.x.length;
            if (mx >= b.box[0] && mx < b.box[2] && mz >= b.box[1] && mz < b.box[3]) {
                b.buildings.add(f);
            }
        }
        return b.buildings.isEmpty() ? null : b;
    }

    /** The buildings a player's planner sent, kept for the build that follows. */
    public static void receive(UUID player, byte[] data) {
        Batch b = unpack(data);
        if (b != null) {
            PENDING.put(player, b);
        }
    }

    static Batch take(UUID player) {
        return PENDING.remove(player);
    }

    // ------------------------------------------------------------------ server side

    /** Works out every building's columns, what it is and its roof; built later a few at a time (see step). */
    MapBuildings(Batch batch, Layout layout, RoadPlan plan) {
        box = batch.box;
        for (Footprint f : batch.buildings) {
            long[] cells = raster(f);
            if (cells == null) {
                continue;
            }
            // not over the roads, pavements or sites (a map's buildings can poke out over a road made wider)
            List<Long> keep = new ArrayList<>();
            int clash = 0;
            for (long c : cells) {
                int idx = layout.index(kx(c), kz(c));
                if (idx >= 0 && layout.kind[idx] != Layout.NONE && layout.kind[idx] != Layout.BLEND) {
                    clash++;
                } else if (!occupied.contains(c)) {
                    keep.add(c);
                }
            }
            if (keep.size() < 4 || clash * 3 > cells.length) {
                continue;
            }
            occupied.addAll(keep);
            shells.add(new Shell(f, keep, shells.size()));
        }
        Set<Long> pitchedCells = new HashSet<>();
        for (Shell s : shells) {
            int depth = distances(s.in).values().stream().max(Integer::compare).orElse(1);
            s.use = use(s.fp.kind, s.cells.length / (batch.scale * batch.scale), depth / batch.scale, s.fp.floors);
            s.pitched = pitched(s, depth / batch.scale);
            if (s.pitched) {
                pitchedCells.addAll(s.in);
            }
        }
        // a roof runs on over houses joined wall to wall, and they look alike
        rise = distances(pitchedCells);
        rise.replaceAll((c, r) -> Math.min(MAX_RISE, r));
        // or it's the shape the real one has: its ridges, hips, gables and lower roofs where it's measured
        for (Shell s : shells) {
            for (int i = 0; s.pitched && s.fp.roofs != null && i < s.cells.length; i++) {
                double up = surfaceAt(s.fp, kx(s.cells[i]) + 0.5, kz(s.cells[i]) + 0.5);
                if (!Double.isNaN(up)) {
                    // shortcut: a tree over a roof reads as roof; capped for houses, where no real roof is that tall
                    int cap = s.use == Use.HOUSE ? MAX_HOUSE_RISE : MAX_MEASURED_RISE;
                    rise.put(s.cells[i], (int) Math.max(1, Math.min(cap, Math.round(up) - s.fp.height)));
                }
            }
        }
        Map<Long, Integer> owner = new HashMap<>();
        for (int i = 0; i < shells.size(); i++) {
            for (long c : shells.get(i).cells) {
                owner.put(c, i);
            }
        }
        for (int i = 0; i < shells.size(); i++) {
            Shell s = shells.get(i);
            for (long c : s.cells) {
                for (long n : new long[]{key(kx(c) + 1, kz(c)), key(kx(c), kz(c) + 1)}) {
                    Integer o = owner.get(n);
                    if (o != null && o != i && s.pitched && shells.get(o).pitched) {
                        shells.get(root(i)).parent = root(o);
                    }
                }
            }
        }
        for (Shell s : shells) {
            Shell r = shells.get(root(s.parent));
            s.style = material(style(s.use, s.pitched, Long.hashCode(r.cells[0]) * 0x9E3779B1 + r.fp.kind.hashCode()), s.fp, s.pitched);
        }
        // roads near enough to have a front door on them
        for (RoadPlan.Segment sg : plan.segments) {
            RoadPlan.Node a = plan.node(sg.a), b = plan.node(sg.b);
            if (a != null && b != null && Math.max(a.x, b.x) > box[0] - 64 && Math.min(a.x, b.x) < box[2] + 64
                    && Math.max(a.z, b.z) > box[1] - 64 && Math.min(a.z, b.z) < box[3] + 64) {
                roads.add(new double[]{a.x + 0.5, a.z + 0.5, b.x + 0.5, b.z + 0.5});
            }
        }
    }

    /** Builds them all now, straight into the level with no undo (the game tests do). */
    public static int buildAll(ServerLevel level, Batch batch, Layout layout, RoadPlan plan) {
        MapBuildings mb = new MapBuildings(batch, layout, plan);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        List<BlockPos> joins = new ArrayList<>();
        mb.step(new SiteBuildings.Out() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                if (state != null) {
                    level.setBlock(pos, state, flags);
                    com.ptmuk.block.Walkways.placed(level, pos);
                }
            }

            @Override
            public void join(BlockPos pos) {
                joins.add(pos);
            }

            @Override
            public void remember(BlockPos pos) {
            }

            @Override
            public ServerLevel level() {
                return level;
            }
        }, new RoadBuilder.Palette(), Integer.MAX_VALUE);
        for (BlockPos p : joins) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), flags);
        }
        return mb.count();
    }

    boolean done() {
        return next >= shells.size();
    }

    int count() {
        return shells.size();
    }

    int built() {
        return next;
    }

    /** Builds the next buildings, about budget columns' worth of blocks. */
    void step(SiteBuildings.Out out, RoadBuilder.Palette pal, int budget) {
        int work = 0;
        while (next < shells.size() && work < budget) {
            work += build(out, pal, shells.get(next++));
        }
    }

    private int root(int i) {
        while (shells.get(i).parent != i) {
            i = shells.get(i).parent;
        }
        return i;
    }

    private static Use use(String kind, double areaM2, double depthM, int floors) {
        return switch (kind) {
            case "house", "detached", "semidetached_house", "terrace", "bungalow", "residential", "cabin", "farm" ->
                    floors >= 4 ? Use.FLATS : Use.HOUSE;
            case "apartments", "flats", "dormitory", "hotel" -> Use.FLATS;
            case "retail", "commercial", "shop", "supermarket", "kiosk", "pub", "restaurant" -> Use.SHOP;
            case "office" -> Use.OFFICE;
            case "industrial", "warehouse", "manufacture", "factory", "storage", "hangar", "service", "transportation", "depot",
                    "barn" -> Use.INDUSTRIAL;
            case "school", "college", "university", "hospital", "public", "civic", "government", "library", "fire_station",
                    "police", "train_station", "sports_hall", "community_centre", "kindergarten" -> Use.CIVIC;
            case "church", "chapel", "cathedral", "mosque", "temple", "synagogue", "religious" -> Use.CHURCH;
            case "garage", "garages", "shed", "carport", "hut", "roof", "greenhouse", "outbuilding", "allotment_house" -> Use.SHED;
            // just "yes": narrow and low is houses (a terrace or a pair of semis), else by its floors and size
            default -> floors <= 3 && depthM <= 7 ? Use.HOUSE : floors >= 4 ? Use.FLATS : areaM2 > 1500 ? Use.INDUSTRIAL : Use.SHOP;
        };
    }

    private static boolean pitched(Shell s, double depthM) {
        switch (s.fp.roof) {
            case "flat" -> {
                return false;
            }
            case "gabled", "hipped", "pitched", "pyramidal", "half-hipped", "gambrel", "mansard", "saltbox" -> {
                return true;
            }
            default -> {
                return s.use == Use.HOUSE || s.use == Use.CHURCH || (s.use == Use.FLATS && s.fp.floors <= 4 && depthM <= 9);
            }
        }
    }

    private static Style style(Use use, boolean pitched, int seed) {
        String red = "ptmuk:red_brick", stock = "ptmuk:london_stock_brick", render = "ptmuk:white_render", dash = "ptmuk:pebbledash";
        String deck = "minecraft:gray_concrete";
        String[] doors = {"minecraft:spruce_door", "minecraft:dark_oak_door", "minecraft:oak_door", "minecraft:jungle_door", "minecraft:mangrove_door"};
        String door = pick(seed, 5, doors);
        return switch (use) {
            case HOUSE -> {
                String wall = pick(seed, 1, red, red, red, stock, stock, dash, render, render);
                String roof = wall.equals(stock) ? "ptmuk:roof_slate" : pick(seed, 2, "ptmuk:roof_clay_tiles", "ptmuk:roof_concrete_tiles", "ptmuk:roof_slate");
                String window = roof.equals("ptmuk:roof_slate") ? "ptmuk:sash_window" : "ptmuk:upvc_window";
                yield new Style(wall, pitched ? roof : deck, window, window, "minecraft:oak_planks", door);
            }
            case FLATS -> {
                String window = pick(seed, 3, "ptmuk:upvc_window", "ptmuk:grey_framed_window");
                yield new Style(pick(seed, 1, red, stock, render), pitched ? "ptmuk:roof_concrete_tiles" : deck, window, window,
                        "minecraft:oak_planks", door);
            }
            case SHOP -> new Style(pick(seed, 1, red, stock, render, dash), pitched ? "ptmuk:roof_slate" : deck,
                    pick(seed, 3, "ptmuk:upvc_window", "ptmuk:sash_window"), "ptmuk:shop_window", "minecraft:polished_andesite", door);
            case OFFICE -> new Style(pick(seed, 1, "ptmuk:portland_stone", render, stock), deck, "ptmuk:grey_framed_window",
                    "ptmuk:grey_framed_window", "minecraft:polished_andesite", "minecraft:spruce_door");
            case INDUSTRIAL -> new Style(pick(seed, 1, "minecraft:light_gray_concrete", "minecraft:white_concrete", "minecraft:cyan_terracotta"),
                    deck, "ptmuk:grey_framed_window", "ptmuk:grey_framed_window", "minecraft:smooth_stone", "minecraft:spruce_door");
            case CIVIC -> {
                String window = pick(seed, 3, "ptmuk:georgian_window", "ptmuk:grey_framed_window");
                yield new Style(pick(seed, 1, stock, red), pitched ? "ptmuk:roof_slate" : deck, window, window, "minecraft:polished_andesite",
                        "minecraft:dark_oak_door");
            }
            case CHURCH -> new Style(pick(seed, 1, "ptmuk:portland_stone", "minecraft:stone_bricks", stock), pitched ? "ptmuk:roof_slate" : deck,
                    "ptmuk:leaded_window", "ptmuk:leaded_window", "minecraft:polished_andesite", "minecraft:dark_oak_door");
            case SHED -> new Style(pick(seed, 1, red, red, render), pitched ? "ptmuk:roof_concrete_tiles" : "minecraft:black_concrete",
                    null, null, "minecraft:smooth_stone", "minecraft:spruce_door");
        };
    }

    /** What the map says a building's walls and roof are made of (building:material, roof:material), where it says. */
    private static Style material(Style st, Footprint fp, boolean pitched) {
        String wall = switch (fp.material) {
            case "brick" -> st.wall().equals("ptmuk:london_stock_brick") ? st.wall() : "ptmuk:red_brick";
            case "stone", "sandstone", "limestone", "granite" -> "ptmuk:portland_stone";
            case "plaster", "render", "stucco" -> "ptmuk:white_render";
            case "concrete", "cement_block" -> "minecraft:light_gray_concrete";
            case "wood", "timber_framing" -> "minecraft:spruce_planks";
            default -> st.wall();
        };
        String roof = !pitched ? st.roof() : switch (fp.roofMaterial) {
            case "roof_tiles", "tile", "tiles", "clay" -> "ptmuk:roof_clay_tiles";
            case "slate" -> "ptmuk:roof_slate";
            case "concrete" -> "ptmuk:roof_concrete_tiles";
            default -> st.roof();
        };
        return new Style(wall, roof, st.window(), st.ground(), st.floor(), st.door());
    }

    private static String pick(int seed, int salt, String... options) {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return options[Math.floorMod(h, options.length)];
    }

    // ------------------------------------------------------------------ one building

    private int build(SiteBuildings.Out out, RoadBuilder.Palette pal, Shell s) {
        ServerLevel level = out.level();
        if (ground == null) {
            ground = new WorldTerrain(level, level.getSeaLevel());        // read now: after the roads levelled it
        }
        int n = s.cells.length;
        int[] g = new int[n];
        for (int i = 0; i < n; i++) {
            g[i] = ground.groundTop(kx(s.cells[i]), kz(s.cells[i]));
        }
        int[] sorted = g.clone();
        Arrays.sort(sorted);
        int base = sorted[n / 2] - 1;                                  // the ground floor: level with most of the ground under it
        int h = s.fp.height;
        int top = base + h;
        int storeys = Math.max(1, Math.min(s.fp.floors, h / 3));
        int sh = h / storeys;
        Style st = s.style;
        BlockState wall = pal.get(st.wall(), "minecraft:bricks"), floor = pal.get(st.floor(), "minecraft:oak_planks");
        BlockState stone = pal.get("minecraft:stone", null), air = Blocks.AIR.defaultBlockState();
        BlockState deck = pal.get(st.roof(), "minecraft:gray_concrete"), light = pal.get("minecraft:light", "minecraft:air");
        long door = door(s);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long chimney = Long.MIN_VALUE;
        int chimneyRise = 2;
        for (int i = 0; i < n; i++) {
            long c = s.cells[i];
            int x = kx(c), z = kz(c);
            boolean edge = false;
            for (int[] d : SIDES) {
                edge |= !s.in.contains(key(x + d[0], z + d[1]));
            }
            int side = windowSide(s, c);
            for (int y = Math.min(g[i], base); y < base; y++) {
                out.set(p.set(x, y, z), edge ? wall : stone);
            }
            out.set(p.set(x, base, z), edge ? wall : floor);
            for (int y = base + 1; y <= top; y++) {
                BlockState b;
                int k = Math.min(storeys - 1, (y - base - 1) / sh), row = y - base - k * sh;
                if (edge) {
                    if (c == door && y <= base + 2) {
                        b = doorHalf(pal, st, side, y == base + 1);
                    } else if (side >= 0 && st.window() != null && window(s.use, side % 2 == 0 ? x : z, k, row, sh, y == top - 2)) {
                        BlockState w = pal.get(k == 0 ? st.ground() : st.window(), "minecraft:glass_pane");
                        b = side % 2 == 0 ? RoadBuilder.with(RoadBuilder.with(w, "east", true), "west", true)
                                : RoadBuilder.with(RoadBuilder.with(w, "north", true), "south", true);
                    } else {
                        b = wall;
                    }
                } else if (y == top) {
                    b = s.pitched ? floor : deck;                       // the top ceiling, or the flat roof
                } else if (row == sh && k < storeys - 1) {
                    b = floor;
                } else if (row == 1 && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
                    b = light;                                          // so nothing spawns in the dark inside
                } else {
                    b = air;
                }
                out.set(p.set(x, y, z), b);
            }
            int roofTop;
            if (s.pitched) {
                int r = riseAt(x, z), low = r;
                for (int[] d : SIDES) {
                    low = Math.min(low, riseAt(x + d[0], z + d[1]));
                }
                for (int y = top + 1; y < top + r; y++) {
                    // gable and party walls up to the roof, and walls up from a lower roof beside it (a tower, a taller wing)
                    out.set(p.set(x, y, z), edge || y > top + low ? wall : air);
                }
                out.set(p.set(x, top + r, z), roof(pal, st, x, z, r));
                out.join(p.immutable());
                roofTop = top + r;
                if (s.use == Use.HOUSE && r > chimneyRise) {
                    chimney = c;
                    chimneyRise = r;
                }
            } else {
                if (edge) {
                    out.set(p.set(x, top + 1, z), wall);                // parapet
                }
                roofTop = top + (edge ? 1 : 0);
            }
            for (int y = roofTop + 1; y <= roofTop + 4; y++) {
                if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                    out.set(p, air);                                    // trees and the like in the way
                }
            }
        }
        if (chimney != Long.MIN_VALUE) {
            int x = kx(chimney), z = kz(chimney), y = top + chimneyRise;
            BlockState brick = pal.get("ptmuk:red_brick", "minecraft:bricks");
            out.set(p.set(x, y, z), brick);
            out.set(p.set(x, y + 1, z), brick);
            out.set(p.set(x, y + 2, z), pal.get("minecraft:flower_pot", null));   // the pot
        }
        return n * (h + 6);
    }

    private static boolean window(Use use, int along, int storey, int row, int sh, boolean highStrip) {
        int a4 = Math.floorMod(along, 4), a3 = Math.floorMod(along, 3);
        if (use == Use.INDUSTRIAL) {
            return highStrip && a4 != 0;                                // a strip high up
        }
        if (use == Use.SHOP && storey == 0) {
            return row >= 1 && row <= Math.min(sh - 1, 3) && a4 != 0;  // shop front
        }
        if (use == Use.CHURCH) {
            return row >= 2 && row <= Math.min(sh - 1, 6) && a3 == 1;
        }
        boolean rows = row >= 2 && row <= Math.max(2, Math.min(sh - 1, 4));
        return rows && (use == Use.HOUSE ? a4 == 1 || a4 == 2 : a3 != 0);
    }

    /**
     * Which way a stretch of plain straight wall at c looks out (index into SIDES), or -1: a
     * corner, a step in a slanting wall, or a wall against the next building.
     */
    private int windowSide(Shell s, long c) {
        int x = kx(c), z = kz(c), side = -1;
        for (int k = 0; k < 4; k++) {
            if (!s.in.contains(key(x + SIDES[k][0], z + SIDES[k][1]))) {
                if (side >= 0 || occupied.contains(key(x + SIDES[k][0], z + SIDES[k][1]))) {
                    return -1;
                }
                side = k;
            }
        }
        if (side < 0) {
            return -1;
        }
        int[] along = SIDES[(side + 1) % 4];
        return s.in.contains(key(x + along[0], z + along[1])) && s.in.contains(key(x - along[0], z - along[1])) ? side : -1;
    }

    /** The front door: on the plain wall nearest the closest road (or the south side when there's none). */
    private long door(Shell s) {
        double mx = 0, mz = 0;
        for (long c : s.cells) {
            mx += kx(c) + 0.5;
            mz += kz(c) + 0.5;
        }
        mx /= s.cells.length;
        mz /= s.cells.length;
        double tx = mx, tz = mz + 1000, best = Double.MAX_VALUE;
        for (double[] r : roads) {
            double dx = r[2] - r[0], dz = r[3] - r[1], len = dx * dx + dz * dz;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((mx - r[0]) * dx + (mz - r[1]) * dz) / len));
            double px = r[0] + t * dx, pz = r[1] + t * dz, d = Math.hypot(px - mx, pz - mz);
            if (d < best) {
                best = d;
                tx = px;
                tz = pz;
            }
        }
        long pick = Long.MIN_VALUE;
        best = Double.MAX_VALUE;
        for (long c : s.cells) {
            int side = windowSide(s, c);
            if (side >= 0) {
                double d = Math.hypot(kx(c) + 0.5 + SIDES[side][0] - tx, kz(c) + 0.5 + SIDES[side][1] - tz);
                if (d < best) {
                    best = d;
                    pick = c;
                }
            }
        }
        return pick;
    }

    private static BlockState doorHalf(RoadBuilder.Palette pal, Style st, int side, boolean lower) {
        BlockState d = pal.get(st.door(), "minecraft:oak_door");
        d = RoadBuilder.with(d, "facing", FACING[(side + 2) % 4].getSerializedName());   // opens inwards, flush with the outside
        return RoadBuilder.with(RoadBuilder.with(d, "hinge", "left"), "half", lower ? "lower" : "upper");
    }

    private int riseAt(int x, int z) {
        return rise.getOrDefault(key(x, z), 0);
    }

    /** A measured roof's height (blocks above the ground) at x, z: between its four nearest points, leaving out ones off the roof; NaN if all are. */
    private static double surfaceAt(Footprint f, double x, double z) {
        double u = (x - f.sx) / 2, w = (z - f.sz) / 2;
        int c = (int) Math.floor(u), r = (int) Math.floor(w), rows = f.roofs.length / f.sw;
        double sum = 0, weight = 0;
        for (int k = 0; k < 4; k++) {
            int cc = c + (k & 1), rr = r + (k >> 1);
            double wt = (1 - Math.abs(u - cc)) * (1 - Math.abs(w - rr));
            int v = cc >= 0 && rr >= 0 && cc < f.sw && rr < rows ? f.roofs[rr * f.sw + cc] & 0xFF : 0;
            if (v > 0 && wt > 0) {
                sum += wt * v / 2.0;
                weight += wt;
            }
        }
        return weight == 0 ? Double.NaN : sum / weight;
    }

    /** A roof stair looking up towards the ridge, or a slab along the ridge (and on top of a big roof, flat). */
    private BlockState roof(RoadBuilder.Palette pal, Style st, int x, int z, int r) {
        int up = -1;
        for (int k = 0; k < 4 && up < 0; k++) {
            if (riseAt(x + SIDES[k][0], z + SIDES[k][1]) > r) {
                up = k;
            }
        }
        for (int dx = -1; dx <= 1 && up < 0; dx += 2) {
            for (int dz = -1; dz <= 1 && up < 0; dz += 2) {
                if (riseAt(x + dx, z + dz) > r) {
                    up = dz < 0 ? 0 : 2;                            // a hip's corner: the stairs round it make the shape
                }
            }
        }
        if (up < 0) {
            return RoadBuilder.with(pal.get(st.roof() + "_slab", "minecraft:brick_slab"), "type", "bottom");
        }
        BlockState stair = pal.get(st.roof() + "_stairs", "minecraft:brick_stairs");
        return RoadBuilder.with(RoadBuilder.with(stair, "facing", FACING[up].getSerializedName()), "half", "bottom");
    }

    // ------------------------------------------------------------------ geometry

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int kz(long k) {
        return (int) k;
    }

    /** The columns whose middles are inside the outline; null if it's too big to build. */
    private static long[] raster(Footprint f) {
        int n = f.x.length;
        double[] xs = new double[n], zs = new double[n];
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            xs[i] = f.x[i] / 4.0;
            zs[i] = f.z[i] / 4.0;
            x0 = Math.min(x0, xs[i]);
            z0 = Math.min(z0, zs[i]);
            x1 = Math.max(x1, xs[i]);
            z1 = Math.max(z1, zs[i]);
        }
        int ix0 = (int) Math.floor(x0), iz0 = (int) Math.floor(z0), ix1 = (int) Math.ceil(x1), iz1 = (int) Math.ceil(z1);
        if ((long) (ix1 - ix0) * (iz1 - iz0) > MAX_CELLS) {
            return null;                                    // shortcut: buildings over 250x250 are left out, build them by hand
        }
        List<Long> in = new ArrayList<>();
        for (int z = iz0; z < iz1; z++) {
            for (int x = ix0; x < ix1; x++) {
                if (inside(xs, zs, x + 0.5, z + 0.5)) {
                    in.add(key(x, z));
                }
            }
        }
        return in.stream().mapToLong(Long::longValue).toArray();
    }

    static boolean inside(double[] xs, double[] zs, double px, double pz) {
        boolean in = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((zs[i] > pz) != (zs[j] > pz) && px < (xs[j] - xs[i]) * (pz - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** How many blocks in from the outside each column is (1 along the edge), counting diagonal steps as one. */
    private static Map<Long, Integer> distances(Set<Long> cells) {
        Map<Long, Integer> d = new HashMap<>();
        ArrayDeque<Long> q = new ArrayDeque<>();
        for (long c : cells) {
            int x = kx(c), z = kz(c);
            boolean rim = false;
            for (int dx = -1; dx <= 1 && !rim; dx++) {
                for (int dz = -1; dz <= 1 && !rim; dz++) {
                    rim = !cells.contains(key(x + dx, z + dz));
                }
            }
            if (rim) {
                d.put(c, 1);
                q.add(c);
            }
        }
        while (!q.isEmpty()) {
            long c = q.poll();
            int x = kx(c), z = kz(c), dc = d.get(c);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long nb = key(x + dx, z + dz);
                    if (cells.contains(nb) && !d.containsKey(nb)) {
                        d.put(nb, dc + 1);
                        q.add(nb);
                    }
                }
            }
        }
        return d;
    }
}
