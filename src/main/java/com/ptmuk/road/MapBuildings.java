package com.ptmuk.road;

import com.ptmuk.building.ShopNames;

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
        /** Its shops (or business), each to get a sign on its street front. */
        public List<ShopFront> shops = new ArrayList<>();
        /** Its real roof (see OsmMap.Surface), Base64, from block sx, sz, sw points across; "" if it has none. */
        public String surface = "";
        public int sx, sz, sw;
        transient byte[] roofs;
    }

    /** A shop in a building: the kind of sign (see ShopNames), its name ("" for a made-up one), where it's mapped (quarter blocks). */
    public static final class ShopFront {
        public String kind = "retail_home", name = "";
        public int x, z;
    }

    /** The buildings round where you build: the square they came from (x0, z0, x1, z1) and the map's scale. */
    public static final class Batch {
        public int[] box;
        public double scale = 1;
        public List<Footprint> buildings = new ArrayList<>();
    }

    /**
     * What's built. After the buildings: areas laid on the ground (a car park, a forecourt, a
     * pedestrian square, a pitch; water; a park or grass, a wood), lines along it (a river, a path,
     * a hedge, fence or wall, a row of trees), things stood on it, and canopies (a roof on posts).
     */
    private enum Use { HOUSE, FLATS, SHOP, OFFICE, INDUSTRIAL, CIVIC, CHURCH, SHED, CAR_PARK, FORECOURT, PEDESTRIAN, YARD, PITCH, WATER, GREEN,
        WOOD, RIVER, PATH, AISLE, HEDGE, FENCE, WALL, TREE_ROW, THING, CANOPY }

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
    private static final int MAX_BUILDINGS = 60000, MAX_CORNERS = 2000, MAX_CELLS = 250 * 250, MAX_AREA_CELLS = 500 * 500;
    /** The most a map's buildings can come to, packed, to send to the server in one go (all of a big map's). */
    public static final int MAX_PACKED = 8 * 1024 * 1024;
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
    /** Where the paths, rivers, hedges and the like run. */
    private final Set<Long> lineCells = new HashSet<>();
    /** Car parks' aisles: they mark out the bays (a car park with aisles in it is just its tarmac; they do the rest). */
    private final List<Footprint> aisles = new ArrayList<>();
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
            for (com.ptmuk.road.OsmMap.Shop sh : o.shops()) {
                ShopFront sf = new ShopFront();
                sf.kind = sh.kind();
                sf.name = sh.name();
                sf.x = (int) Math.round((sh.x() + mi.nudgeX) * 4);
                sf.z = (int) Math.round((sh.z() + mi.nudgeZ) * 4);
                f.shops.add(sf);
            }
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
        b.buildings.removeIf(f -> f == null || f.x == null || f.z == null || f.x.length != f.z.length
                || f.x.length < (f.kind != null && com.ptmuk.road.OsmMap.feature(f.kind) ? 1 : 3) || f.x.length > MAX_CORNERS);
        if (b.buildings.size() > MAX_BUILDINGS) {
            b.buildings = new ArrayList<>(b.buildings.subList(0, MAX_BUILDINGS));
        }
        for (Footprint f : b.buildings) {
            f.kind = f.kind == null ? "yes" : f.kind;
            f.roof = f.roof == null ? "" : f.roof;
            f.material = f.material == null ? "" : f.material;
            f.shops = f.shops == null ? new ArrayList<>() : new ArrayList<>(f.shops.subList(0, Math.min(40, f.shops.size())));
            f.shops.removeIf(sf -> sf == null);
            for (ShopFront sf : f.shops) {
                sf.kind = sf.kind != null && com.ptmuk.building.ShopNames.has(sf.kind) ? sf.kind : "retail_home";
                sf.name = sf.name == null ? "" : sf.name.length() > 30 ? sf.name.substring(0, 30) : sf.name;
            }
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
    /** Takes the buildings with their middles within half each way of (cx, cz) out of all of them (so each is built once); null if none. */
    static Batch within(Batch all, double cx, double cz, int half) {
        if (all == null) {
            return null;
        }
        Batch b = new Batch();
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        b.box = new int[]{x - half, z - half, x + half, z + half};
        b.scale = all.scale;
        all.buildings.removeIf(f -> {
            double[] m = middle(f);
            boolean in = m[0] >= b.box[0] && m[0] < b.box[2] && m[1] >= b.box[1] && m[1] < b.box[3];
            if (in) {
                b.buildings.add(f);
            }
            return in;
        });
        return b.buildings.isEmpty() ? null : b;
    }

    /** The middle of the building still to build nearest (cx, cz); null if there are none left. */
    static double[] nearest(Batch all, double cx, double cz) {
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (Footprint f : all == null ? List.<Footprint>of() : all.buildings) {
            double[] m = middle(f);
            double d = Math.hypot(m[0] - cx, m[1] - cz);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    private static double[] middle(Footprint f) {
        double mx = 0, mz = 0;
        for (int i = 0; i < f.x.length; i++) {
            mx += f.x[i] / 4.0;
            mz += f.z[i] / 4.0;
        }
        return new double[]{mx / f.x.length, mz / f.x.length};
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
        // buildings take their ground first; then water, the car parks, squares and pitches, the parks and woods round them;
        // then the lines and things over those, and the canopies over the forecourts
        List<Footprint> order = new ArrayList<>(batch.buildings);
        order.sort(java.util.Comparator.comparingInt(f -> rank(f.kind)));
        Set<Long> paved = new HashSet<>();
        for (Footprint f : order) {
            int rank = rank(f.kind);
            boolean surface = area(rank), canopy = canopy(f.kind), small = lineOrThing(rank);
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
                } else if (!occupied.contains(c) && !(surface && paved.contains(c))) {
                    keep.add(c);
                }
            }
            // a line or a thing loses just what's on a road; an area or building mostly on one isn't built
            if (small ? keep.isEmpty() : keep.size() < 4 || clash * 3 > cells.length) {
                continue;
            }
            if (surface) {
                paved.addAll(keep);
            } else if (small) {
                if (rank < 7) {
                    lineCells.addAll(keep);                 // (no trees on paths)
                }
                if (f.kind.equals("aisle")) {
                    aisles.add(f);
                }
            } else if (!canopy) {
                occupied.addAll(keep);                      // (a canopy stands over a forecourt: it doesn't keep the ground to itself)
            }
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

    /** The order things are given their ground and built in (see the constructor). */
    private static int rank(String kind) {
        if (canopy(kind)) {
            return 8;
        }
        if (!com.ptmuk.road.OsmMap.feature(kind)) {
            return 0;
        }
        return switch (kind) {
            case "water" -> 1;
            case "parking", "fuel", "pedestrian", "pitch" -> 2;
            case "green", "wood" -> 3;
            case "yard:retail", "yard:industrial" -> 4;
            case "aisle", "hedge", "fence", "fence:metal", "wall", "tree_row" -> 6;
            default -> kind.startsWith("river:") ? 5 : kind.startsWith("path:") ? 6 : 7;
        };
    }

    /** Areas laid on the ground (ranks 1-4), lines along it (5, 6), things stood on it (7). */
    private static boolean area(int rank) {
        return rank >= 1 && rank <= 4;
    }

    private static boolean lineOrThing(int rank) {
        return rank >= 5 && rank <= 7;
    }

    /** A line's width in blocks: the number at the end of its kind ("path:paved:3", "river:18"), else 1. */
    private static int width(String kind) {
        int colon = kind.lastIndexOf(':');
        try {
            return colon < 0 ? 1 : Math.max(1, Math.min(120, Integer.parseInt(kind.substring(colon + 1))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static Use use(String kind, double areaM2, double depthM, int floors) {
        if (kind.startsWith("river:")) {
            return Use.RIVER;
        }
        if (kind.startsWith("path:")) {
            return Use.PATH;
        }
        return switch (kind) {
            case "pedestrian", "yard:retail" -> Use.PEDESTRIAN;
            case "yard:industrial" -> Use.YARD;
            case "aisle" -> Use.AISLE;
            case "pitch" -> Use.PITCH;
            case "water" -> Use.WATER;
            case "green" -> Use.GREEN;
            case "wood" -> Use.WOOD;
            case "hedge" -> Use.HEDGE;
            case "fence", "fence:metal" -> Use.FENCE;
            case "wall" -> Use.WALL;
            case "tree_row" -> Use.TREE_ROW;
            case "tree", "bench", "bin", "post_box", "phone", "bike", "lamp", "bollard" -> Use.THING;
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
            case "garage", "garages", "shed", "hut", "greenhouse", "outbuilding", "allotment_house" -> Use.SHED;
            case "roof", "carport" -> Use.CANOPY;
            case "parking" -> Use.CAR_PARK;
            case "fuel" -> Use.FORECOURT;
            // just "yes": narrow and low is houses (a terrace or a pair of semis), else by its floors and size
            default -> floors <= 3 && depthM <= 7 ? Use.HOUSE : floors >= 4 ? Use.FLATS : areaM2 > 1500 ? Use.INDUSTRIAL : Use.SHOP;
        };
    }

    private static boolean canopy(String kind) {
        return kind.equals("roof") || kind.equals("carport");
    }

    private static boolean pitched(Shell s, double depthM) {
        if (s.use.ordinal() >= Use.CAR_PARK.ordinal()) {
            return false;                                   // not a building
        }
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
            // (not walls and windows: see surface and canopy) posts and deck
            case CAR_PARK, FORECOURT, PEDESTRIAN, YARD, PITCH, WATER, GREEN, WOOD, RIVER, PATH, AISLE, HEDGE, FENCE, WALL, TREE_ROW, THING, CANOPY ->
                    new Style("minecraft:light_gray_concrete", "minecraft:white_concrete", null, null,
                    "minecraft:smooth_stone", "minecraft:spruce_door");
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
        switch (s.use) {
            case CAR_PARK, FORECOURT, PEDESTRIAN, YARD, PITCH -> {
                return surface(out, pal, s, base, g);
            }
            case WATER -> {
                return water(out, s, base, g);
            }
            case GREEN, WOOD -> {
                return green(out, pal, s, g);
            }
            case RIVER, PATH, AISLE, HEDGE, FENCE, WALL, TREE_ROW, THING -> {
                return line(out, pal, s, g);
            }
            case CANOPY -> {
                return canopy(out, pal, s, base);
            }
            default -> {
            }
        }
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
        if (!s.fp.shops.isEmpty()) {
            shopFronts(out, pal, s, base, sh, door);
        }
        return n * (h + 6);
    }

    /**
     * Each shop's sign over its stretch of the building's street front (the side its door's on):
     * the stretch nearest where the shop's mapped, up to 7 blocks of it, along the top of the ground
     * floor, with its real name (or a made-up one) in the colours of its kind (see ShopNames).
     */
    private void shopFronts(SiteBuildings.Out out, RoadBuilder.Palette pal, Shell s, int base, int sh, long door) {
        int front = door == Long.MIN_VALUE ? -1 : windowSide(s, door);
        if (front < 0) {
            return;
        }
        int[] normal = SIDES[front], along = SIDES[(front + 1) % 4];
        List<Long> frontage = new ArrayList<>();
        for (long c : s.cells) {
            if (windowSide(s, c) == front) {
                frontage.add(c);
            }
        }
        Set<Long> used = new HashSet<>();
        int y = base + Math.max(3, sh - 1);
        for (ShopFront shop : s.fp.shops) {
            long best = Long.MIN_VALUE;
            double bestD = Double.MAX_VALUE;
            for (long c : frontage) {
                double d = Math.hypot(kx(c) + 0.5 - shop.x / 4.0, kz(c) + 0.5 - shop.z / 4.0);
                if (!used.contains(c) && d < bestD) {
                    best = c;
                    bestD = d;
                }
            }
            if (best == Long.MIN_VALUE) {
                break;                                      // the front's full
            }
            // the stretch: out from there along the wall, to its ends or the next shop's
            List<Long> row = new ArrayList<>(List.of(best));
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int i = 1; i <= 3; i++) {
                    long c = key(kx(best) + along[0] * i * dir, kz(best) + along[1] * i * dir);
                    if (!frontage.contains(c) || used.contains(c)) {
                        break;
                    }
                    row.add(c);
                }
            }
            for (long c : row) {
                used.add(c);
                used.add(key(kx(c) + along[0], kz(c) + along[1]));          // a gap before the next shop's
                used.add(key(kx(c) - along[0], kz(c) - along[1]));
            }
            ShopNames.Sign look = ShopNames.pick(shop.kind, ShopNames.seed(kx(best), kz(best)));
            BlockState board = RoadBuilder.with(pal.get("ptmuk:shop_sign", "minecraft:oak_wall_sign"), "facing", FACING[front].getSerializedName());
            for (long c : row) {
                BlockPos at = new BlockPos(kx(c) + normal[0], y, kz(c) + normal[1]);
                out.set(at, board);
                if (look != null && out.level().getBlockEntity(at) instanceof com.ptmuk.building.ShopSignBlockEntity sign) {
                    sign.text = shop.name.isBlank() ? look.text() : shop.name.toUpperCase(java.util.Locale.ROOT);
                    sign.sub = look.sub();
                    sign.board = look.board();
                    sign.ink = look.ink();
                    sign.setChanged();
                    out.level().sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
                }
            }
        }
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

    /** Where a car park or forecourt was laid, a column at a time: the level of its surface (a canopy over it stands on it). */
    private final Map<Long, Integer> laid = new HashMap<>();

    /**
     * A car park or a petrol station's forecourt, level with most of the ground under it: tarmac
     * (a forecourt concrete), filled under and cleared over. A car park's bays are marked out along
     * its longest side the UK way (3 blocks wide, two rows of 5 back to back, a 6-block aisle), with
     * a way round inside its edge left clear, and lamp posts down the aisles.
     */
    private int surface(SiteBuildings.Out out, RoadBuilder.Palette pal, Shell s, int base, int[] g) {
        boolean park = s.use == Use.CAR_PARK && !hasAisles(s);
        BlockState top = pal.get(switch (s.use) {
            case CAR_PARK -> "ptmuk:tarmac";
            case PEDESTRIAN -> s.fp.kind.equals("yard:retail") ? "ptmuk:tarmac" : "ptmuk:block_paving_grey";
            case YARD -> "minecraft:smooth_stone";
            case PITCH -> "minecraft:grass_block";
            default -> "minecraft:smooth_stone";
        }, "minecraft:gray_concrete");
        BlockState line = Blocks.WHITE_CONCRETE.defaultBlockState(), fill = pal.get("minecraft:stone", null), air = Blocks.AIR.defaultBlockState();
        double[] ax = axis(s.fp);
        Map<Long, Integer> edge = distances(s.in);
        double v0 = Double.MAX_VALUE, u0 = Double.MAX_VALUE;
        for (long c : s.cells) {
            u0 = Math.min(u0, (kx(c) + 0.5) * ax[0] + (kz(c) + 0.5) * ax[1]);
            v0 = Math.min(v0, -(kx(c) + 0.5) * ax[1] + (kz(c) + 0.5) * ax[0]);
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.cells.length; i++) {
            long c = s.cells[i];
            int x = kx(c), z = kz(c);
            for (int y = Math.min(g[i] - 1, base) ; y < base; y++) {
                out.set(p.set(x, y, z), fill);
            }
            boolean bay = false, lamp = false;
            if (park && edge.getOrDefault(c, 1) > 3) {
                // in the car park's own frame: u along its longest side, v across it from its edge
                int u = (int) Math.floor((x + 0.5) * ax[0] + (z + 0.5) * ax[1] - u0), v = (int) Math.floor(-(x + 0.5) * ax[1] + (z + 0.5) * ax[0] - v0) - 4;
                int row = Math.floorMod(v, 16);                 // 0-4 and 5-9: two rows of bays; 10-15: the aisle
                bay = row < 10 && Math.floorMod(u, 3) == 0;
                lamp = row == 12 && Math.floorMod(u, 24) == 12;
            }
            // a pitch's touchline round it
            out.set(p.set(x, base, z), bay || s.use == Use.PITCH && edge.getOrDefault(c, 1) == 1 ? line : top);
            laid.put(c, base);
            for (int y = base + 1; y <= Math.max(g[i], base + 1) + 2; y++) {
                if (!out.level().getBlockState(p.set(x, y, z)).isAir()) {
                    out.set(p, air);                            // the ground higher here, or a tree or plants on it
                }
            }
            if (lamp) {
                BlockState pole = pal.get("ptmuk:sign_pole_galvanised", "iron_bars");
                for (int k = 1; k <= 5; k++) {
                    out.set(p.set(x, base + k, z), RoadBuilder.with(RoadBuilder.with(pole, "base", k == 1), "cap", false));
                }
                out.set(p.set(x, base + 6, z), RoadBuilder.with(pal.get("ptmuk:street_light_led", "lantern"), "facing", "north"));
            }
        }
        return s.cells.length;
    }

    /**
     * A canopy (building=roof: a petrol station's, or a carport): a flat deck on posts at its
     * height (5 blocks at least), lit underneath, open at the sides. On a forecourt, fuel pumps
     * stand along its middle between the posts.
     */
    private int canopy(SiteBuildings.Out out, RoadBuilder.Palette pal, Shell s, int base) {
        long middle = s.cells[s.cells.length / 2];
        boolean forecourt = laid.containsKey(middle);
        int floor = forecourt ? laid.get(middle) : base, top = floor + Math.max(5, s.fp.height);
        BlockState deck = pal.get("minecraft:white_concrete", null), fascia = pal.get("minecraft:light_gray_concrete", null);
        BlockState post = pal.get("minecraft:light_gray_concrete", null), lit = pal.get("minecraft:sea_lantern", "glowstone");
        BlockState pump = pal.get("ptmuk:fuel_pump", "minecraft:iron_block"), air = Blocks.AIR.defaultBlockState();
        double[] ax = axis(s.fp);
        Map<Long, Integer> edge = distances(s.in);
        double vMin = Double.MAX_VALUE, vMax = -Double.MAX_VALUE, uMin = Double.MAX_VALUE;
        for (long c : s.cells) {
            double v = -(kx(c) + 0.5) * ax[1] + (kz(c) + 0.5) * ax[0];
            vMin = Math.min(vMin, v);
            vMax = Math.max(vMax, v);
            uMin = Math.min(uMin, (kx(c) + 0.5) * ax[0] + (kz(c) + 0.5) * ax[1]);
        }
        double vMid = (vMin + vMax) / 2;
        // pumps face across the canopy: the side of the island they're on
        String face = Math.abs(ax[0]) >= Math.abs(ax[1]) ? "south" : "east";
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (long c : s.cells) {
            int x = kx(c), z = kz(c), d = edge.getOrDefault(c, 1);
            int u = (int) Math.floor((x + 0.5) * ax[0] + (z + 0.5) * ax[1] - uMin);
            boolean centre = Math.abs(-(x + 0.5) * ax[1] + (z + 0.5) * ax[0] - vMid) <= 0.5;
            boolean isPost = centre && d > 1 && Math.floorMod(u, 8) == 4, isPump = forecourt && centre && d > 1 && Math.floorMod(u, 8) == 1;
            for (int y = floor + 1; y < top; y++) {
                BlockState b = isPost ? post : isPump && y == floor + 1 ? RoadBuilder.with(pump, "facing", face) : air;
                if (b != air || !out.level().getBlockState(p.set(x, y, z)).isAir()) {
                    out.set(p.set(x, y, z), b);
                }
            }
            if (!forecourt) {
                out.set(p.set(x, floor, z), pal.get("minecraft:smooth_stone", null));
            }
            out.set(p.set(x, top, z), d == 1 ? fascia : Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? lit : deck);
            if (d == 1) {
                out.set(p.set(x, top + 1, z), fascia);      // a deeper edge, as a canopy's fascia is
            }
        }
        return s.cells.length * 3;
    }

    /**
     * Water (a lake, pond, reservoir or a river drawn as an area): level with the lowest of its
     * shore so it stays in, dug down 1 to 3 blocks (deepest away from the edge), on a dirt bed.
     */
    private int water(SiteBuildings.Out out, Shell s, int base, int[] g) {
        Map<Long, Integer> edge = distances(s.in);
        int level = base;
        for (int i = 0; i < s.cells.length; i++) {
            if (edge.getOrDefault(s.cells[i], 1) == 1) {
                level = Math.min(level, g[i] - 1);
            }
        }
        BlockState water = Blocks.WATER.defaultBlockState(), bed = Blocks.DIRT.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.cells.length; i++) {
            long c = s.cells[i];
            int x = kx(c), z = kz(c), depth = Math.min(3, edge.getOrDefault(c, 1));
            out.set(p.set(x, level - depth, z), bed);
            for (int y = level - depth + 1; y <= level; y++) {
                out.set(p.set(x, y, z), water);
            }
            for (int y = level + 1; y <= Math.max(g[i], level + 1) + 1; y++) {
                if (!out.level().getBlockState(p.set(x, y, z)).isAir()) {
                    out.set(p, air);
                }
            }
            laid.put(c, level);
        }
        return s.cells.length * 2;
    }

    /**
     * A park, grass, a cemetery or a wood: the ground left as it lies, grassed over, with trees:
     * now and then in a park, close together in a wood (none on the paths through them).
     */
    private int green(SiteBuildings.Out out, RoadBuilder.Palette pal, Shell s, int[] g) {
        Map<Long, Integer> edge = distances(s.in);
        boolean wood = s.use == Use.WOOD;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.cells.length; i++) {
            long c = s.cells[i];
            int x = kx(c), z = kz(c), top = g[i] - 1;
            BlockState was = out.level().getBlockState(p.set(x, top, z));
            if (!was.is(Blocks.GRASS_BLOCK) && was.getFluidState().isEmpty()) {
                out.set(p, grass);
            }
            laid.put(c, top);
            int hash = Math.floorMod(x * 73856093 ^ z * 19349663, wood ? 23 : 150);
            if (hash == 0 && edge.getOrDefault(c, 1) >= (wood ? 2 : 3) && clearOfLines(c)) {
                thing(out, pal, "tree", x, top + 1, z);
            }
        }
        return s.cells.length;
    }

    private boolean clearOfLines(long c) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (lineCells.contains(key(kx(c) + dx, kz(c) + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A line along the ground or a thing stood on it, following the ground (or what's been laid
     * there): a river or stream cut into it, a path, a hedge, fence or wall, a row of trees.
     */
    private int line(SiteBuildings.Out out, RoadBuilder.Palette pal, Shell s, int[] g) {
        String kind = s.fp.kind;
        Map<Long, Integer> edge = s.use == Use.RIVER ? distances(s.in) : Map.of();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState path = pal.get(kind.startsWith("path:cycle") ? "ptmuk:tarmac" : kind.startsWith("path:track") ? "minecraft:dirt_path"
                : "ptmuk:paving_slabs", "minecraft:smooth_stone");
        BlockState barrier = switch (s.use) {
            case HEDGE -> RoadBuilder.with(pal.get("minecraft:oak_leaves", null), "persistent", true);
            case FENCE -> pal.get(kind.equals("fence:metal") ? "ptmuk:palisade_fence" : "minecraft:spruce_fence", "minecraft:oak_fence");
            case WALL -> pal.get("minecraft:brick_wall", "minecraft:cobblestone_wall");
            default -> null;
        };
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.cells.length; i++) {
            long c = s.cells[i];
            int x = kx(c), z = kz(c), y = laid.getOrDefault(c, g[i] - 1);
            switch (s.use) {
                case RIVER -> {
                    int depth = Math.min(3, 1 + edge.getOrDefault(c, 1));
                    out.set(p.set(x, y - depth, z), Blocks.DIRT.defaultBlockState());
                    for (int k = y - depth + 1; k <= y; k++) {
                        out.set(p.set(x, k, z), Blocks.WATER.defaultBlockState());
                    }
                    clear(out, p, x, y + 1, y + 2, z, air);
                }
                case PATH -> {
                    out.set(p.set(x, y, z), path);
                    clear(out, p, x, y + 1, y + 3, z, air);
                    laid.put(c, y);
                }
                case AISLE -> {
                    // the drive down the middle (6 wide), a row of bays (5 deep, 3 wide) either side, square to it; lamps behind the bays
                    double[] at = alongLine(s.fp, x + 0.5, z + 0.5);
                    boolean bay = at[0] >= 3 && at[0] < 8 && Math.floorMod((int) Math.floor(at[1]), 3) == 0;
                    out.set(p.set(x, y, z), bay ? Blocks.WHITE_CONCRETE.defaultBlockState() : pal.get("ptmuk:tarmac", "minecraft:gray_concrete"));
                    clear(out, p, x, y + 1, y + 3, z, air);
                    laid.put(c, y);
                    if (at[0] >= 7.5 && at[0] < 8 && Math.floorMod((int) Math.floor(at[1]), 30) == 15) {
                        thing(out, pal, "lamp", x, y + 1, z);
                    }
                }
                case HEDGE, FENCE, WALL -> {
                    int high = s.use == Use.FENCE ? 1 : 2;
                    for (int k = 1; k <= high; k++) {
                        out.set(p.set(x, y + k, z), barrier);
                        out.join(p.immutable());
                    }
                }
                case TREE_ROW -> {
                    if (Math.floorMod(x * 31 + z * 17, 7) == 0) {
                        thing(out, pal, "tree", x, y + 1, z);
                    }
                }
                default -> thing(out, pal, kind, x, y + 1, z);
            }
        }
        return s.cells.length * 2;
    }

    private static void clear(SiteBuildings.Out out, BlockPos.MutableBlockPos p, int x, int y0, int y1, int z, BlockState air) {
        for (int y = y0; y <= y1; y++) {
            if (!out.level().getBlockState(p.set(x, y, z)).isAir()) {
                out.set(p, air);
            }
        }
    }

    /** A thing stood at x, y, z: a tree, a bench, a bin, a pillar box, a phone box, a bike stand, a bollard or a street lamp. */
    private static void thing(SiteBuildings.Out out, RoadBuilder.Palette pal, String kind, int x, int y, int z) {
        Layout.FixtureType type = switch (kind) {
            case "tree" -> Layout.FixtureType.PARK_TREE;
            case "bench" -> Layout.FixtureType.BENCH;
            case "bin" -> Layout.FixtureType.BIN;
            case "post_box" -> Layout.FixtureType.PILLAR_BOX;
            case "phone" -> Layout.FixtureType.PHONE_BOX;
            case "bike" -> Layout.FixtureType.BIKE_STAND;
            case "bollard" -> Layout.FixtureType.BOLLARD;
            default -> null;
        };
        if (type != null) {
            SiteFixtures.place(out.level(), new Layout.Fixture(type, x, z, y, 0, 0), y, pal, out::set);
        } else if (kind.equals("lamp")) {
            BlockState pole = pal.get("ptmuk:sign_pole_galvanised", "iron_bars");
            for (int k = 0; k < 5; k++) {
                out.set(new BlockPos(x, y + k, z), RoadBuilder.with(RoadBuilder.with(pole, "base", k == 0), "cap", false));
            }
            out.set(new BlockPos(x, y + 5, z), RoadBuilder.with(pal.get("ptmuk:street_light_led", "lantern"), "facing", "north"));
        }
    }

    /** Whether any car park aisle runs through a shell's ground. */
    private boolean hasAisles(Shell s) {
        for (Footprint a : aisles) {
            for (int i = 0; i < a.x.length; i++) {
                if (s.in.contains(key(Math.floorDiv(a.x[i], 4), Math.floorDiv(a.z[i], 4)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How far (x, z) is from a line, and how far along it the nearest point is (blocks): {distance, along}. */
    private static double[] alongLine(Footprint f, double x, double z) {
        double best = Double.MAX_VALUE, at = 0, run = 0;
        for (int i = 0; i + 1 < f.x.length; i++) {
            double ax = f.x[i] / 4.0, az = f.z[i] / 4.0, dx = f.x[i + 1] / 4.0 - ax, dz = f.z[i + 1] / 4.0 - az, l2 = dx * dx + dz * dz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            double d = Math.hypot(ax + dx * t - x, az + dz * t - z);
            if (d < best) {
                best = d;
                at = run + t * Math.sqrt(l2);
            }
            run += Math.sqrt(l2);
        }
        return new double[]{best, at};
    }

    /** The way along a footprint's longest side (x, z, length one). */
    private static double[] axis(Footprint f) {
        double best = -1, dx = 1, dz = 0;
        for (int i = 0; i + 1 < f.x.length; i++) {
            double ex = f.x[i + 1] - f.x[i], ez = f.z[i + 1] - f.z[i], len = Math.hypot(ex, ez);
            if (len > best) {
                best = len;
                dx = ex / len;
                dz = ez / len;
            }
        }
        return new double[]{dx, dz};
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

    /** The columns whose middles are inside the outline (along a line, its width; a thing, its one); null if it's too big to build. */
    private static long[] raster(Footprint f) {
        int rank = rank(f.kind);
        if (lineOrThing(rank)) {
            return along(f, f.kind.equals("aisle") ? 16 : width(f.kind));
        }
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
        if ((long) (ix1 - ix0) * (iz1 - iz0) > (area(rank) ? MAX_AREA_CELLS : MAX_CELLS)) {
            return null;                                    // shortcut: buildings over 250x250 (parks, woods, water over 500x500) are left out
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

    /** The columns within width/2 of a line (a thing: the one it's in). */
    private static long[] along(Footprint f, int width) {
        Set<Long> cells = new java.util.LinkedHashSet<>();
        double r = width / 2.0;
        int reach = (int) Math.ceil(r);
        for (int i = 0; i < f.x.length && cells.size() < MAX_AREA_CELLS; i++) {
            double ax = f.x[i] / 4.0, az = f.z[i] / 4.0;
            double bx = i + 1 < f.x.length ? f.x[i + 1] / 4.0 : ax, bz = i + 1 < f.z.length ? f.z[i + 1] / 4.0 : az;
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 4));      // every quarter block: no gaps on a slant
            for (int k = 0; k <= steps; k++) {
                double sx = ax + (bx - ax) * k / steps, sz = az + (bz - az) * k / steps;
                if (width <= 1) {
                    cells.add(key((int) Math.floor(sx), (int) Math.floor(sz)));
                    continue;
                }
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        int cx = (int) Math.floor(sx + dx), cz = (int) Math.floor(sz + dz);
                        if (Math.hypot(cx + 0.5 - sx, cz + 0.5 - sz) <= r) {
                            cells.add(key(cx, cz));
                        }
                    }
                }
            }
        }
        return cells.stream().mapToLong(Long::longValue).toArray();
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
