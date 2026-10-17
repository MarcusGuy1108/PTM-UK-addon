package com.ptmuk.road;

import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.Terrain;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Builds a road plan into the world on the server: levels the ground (cutting through hills,
 * building up dips, sloping the sides), lays PTM2 asphalt in full blocks and slabs, kerbs,
 * pavements, verges and islands, PTM2 road markings in white and yellow, and street lights. It
 * works through the columns over several ticks so long roads don't stall the server, saving what
 * was there for undo.
 */
public final class RoadBuilder {
    private static final int UNDO_STEPS = 5;
    private static final int COLUMNS_PER_TICK = 600;
    private static final int CLEAR_ABOVE = 6;
    private static final int MAX_FILL = 64;
    private static final int MAX_CELLS = 2_500_000;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Saved(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    private record Undo(ResourceKey<Level> dimension, List<Saved> blocks) {
    }

    private static final class Job {
        UUID player;
        InteractionHand hand;
        ServerLevel level;
        Layout layout;
        RoadPlan plan;
        int[] cells;
        int next;
        List<Saved> saved = new ArrayList<>();
        Set<Long> touched = new HashSet<>();
        List<BlockPos> joins = new ArrayList<>();
        Palette palette;
        int changed;
    }

    private static final Map<UUID, Deque<Undo>> HISTORY = new HashMap<>();
    private static final List<Job> JOBS = new ArrayList<>();

    private RoadBuilder() {
    }

    public static boolean busy(UUID player) {
        for (Job j : JOBS) {
            if (j.player.equals(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ starting a build

    public static void start(ServerPlayer player, InteractionHand hand, RoadPlan plan) {
        if (busy(player.getUUID())) {
            player.displayClientMessage(Component.literal("Still building your last road..."), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        // node heights come from the ground once, then stay put so later edits don't shift built roads
        Terrain terrain = new LoadingTerrain(level, (int) Math.floor(player.getY()));
        Layout first = Layout.of(plan, terrain);
        for (RoadPlan.Node n : plan.nodes) {
            if (Double.isNaN(n.y)) {
                n.y = first.heightOf(n.id);
            }
        }
        Layout layout = Layout.of(plan, terrain);
        if ((long) layout.w * layout.h > MAX_CELLS) {
            player.displayClientMessage(Component.literal("That plan covers too big an area - build it in parts"), true);
            return;
        }
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < layout.kind.length; i++) {
            if (layout.dirty[i] && layout.kind[i] != Layout.NONE) {
                cells.add(i);
            }
        }
        if (cells.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing new to build - draw some roads first"), true);
            return;
        }
        // footprint first, then the blending margin, so slopes meet finished edges
        cells.sort((a, b) -> Boolean.compare(layout.kind[a] == Layout.BLEND, layout.kind[b] == Layout.BLEND));
        Job job = new Job();
        job.player = player.getUUID();
        job.hand = hand;
        job.level = level;
        job.layout = layout;
        job.plan = plan;
        job.cells = cells.stream().mapToInt(Integer::intValue).toArray();
        job.palette = new Palette();
        JOBS.add(job);
        player.displayClientMessage(Component.literal("Building roads: " + job.cells.length + " columns..."), true);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            int end = Math.min(job.cells.length, job.next + COLUMNS_PER_TICK);
            for (; job.next < end; job.next++) {
                column(job, job.cells[job.next]);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(job.player);
            if (job.next >= job.cells.length) {
                finish(job, player);
                it.remove();
            } else if (player != null && job.next % (COLUMNS_PER_TICK * 10) < COLUMNS_PER_TICK) {
                player.displayClientMessage(Component.literal("Building roads: " + (100 * job.next / job.cells.length) + "%"), true);
            }
        }
    }

    private static void finish(Job job, ServerPlayer player) {
        Layout layout = job.layout;
        // traffic-light junctions: a PTM2 intersection first, so the heads join it as they're placed
        for (Layout.SignalZone zone : layout.signalZones) {
            RoadPlan.Node n = job.plan.node(zone.nodeId());
            if (n != null && !n.built) {
                Ptm2Signals.intersection(job.level, zone.x0(), zone.z0(), zone.x1(), zone.z1());
            }
        }
        for (Layout.Fixture f : layout.fixtures) {
            int idx = layout.index(f.x(), f.z());
            if (idx >= 0 && layout.dirty[idx]) {
                fixture(job, f);
            }
        }
        for (BlockPos p : job.joins) {
            BlockState s = job.level.getBlockState(p);
            set(job, p, Block.updateFromNeighbourShapes(s, job.level, p));
        }
        Deque<Undo> history = HISTORY.computeIfAbsent(job.player, k -> new ArrayDeque<>());
        history.push(new Undo(job.level.dimension(), job.saved));
        while (history.size() > UNDO_STEPS) {
            history.removeLast();
        }
        for (RoadPlan.Node n : job.plan.nodes) {
            n.built = true;
        }
        for (RoadPlan.Segment s : job.plan.segments) {
            s.built = true;
        }
        if (player != null) {
            ItemStack stack = player.getItemInHand(job.hand);
            if (stack.getItem() instanceof RoadPlannerItem) {
                RoadPlans.write(stack, job.plan);
            }
            player.displayClientMessage(Component.literal("Roads built (" + job.changed + " blocks changed)"), true);
        }
    }

    // ------------------------------------------------------------------ blocks

    /** Block states by name, so PTM2's blocks are used when it's there and ours when it isn't. */
    static final class Palette {
        private final Map<String, BlockState> cache = new HashMap<>();

        BlockState get(String id, String fallback) {
            return cache.computeIfAbsent(id + "|" + fallback, k -> {
                BlockState s = lookup(id);
                return s != null ? s : lookup(fallback);
            });
        }

        static BlockState lookup(String id) {
            if (id == null) {
                return null;
            }
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            return b == null || b == Blocks.AIR ? null : b.defaultBlockState();
        }
    }

    static BlockState with(BlockState s, String prop, Object value) {
        if (s == null) {
            return null;
        }
        Property<?> p = s.getBlock().getStateDefinition().getProperty(prop);
        return p == null ? s : apply(s, p, String.valueOf(value).toLowerCase());
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    private static BlockState slab(BlockState s) {
        return with(s, "type", "bottom");
    }

    private static void set(Job job, BlockPos pos, BlockState state) {
        if (state == null) {
            return;
        }
        ServerLevel level = job.level;
        BlockState old = level.getBlockState(pos);
        if (old == state) {
            return;
        }
        if (job.touched.add(pos.asLong())) {
            BlockEntity be = level.getBlockEntity(pos);
            job.saved.add(new Saved(pos.immutable(), old, be == null ? null : be.saveWithFullMetadata()));
            Clearable.tryClear(be);
        }
        level.setBlock(pos, state, FLAGS);
        job.changed++;
    }

    private static boolean isHalf(float t) {
        return Math.abs(t - Math.floor(t)) > 0.25;
    }

    // ------------------------------------------------------------------ one column

    private static void column(Job job, int idx) {
        Layout layout = job.layout;
        ServerLevel level = job.level;
        int x = layout.cellX(idx), z = layout.cellZ(idx);
        level.getChunk(x >> 4, z >> 4);
        byte kind = layout.kind[idx];
        float t = layout.top[idx];
        if (Float.isNaN(t)) {
            return;
        }
        if (kind == Layout.BLEND) {
            blend(job, x, z, (int) t, layout.blendDist[idx]);
            return;
        }
        Palette pal = job.palette;
        RoadStyle st = layout.styleOf(idx);
        BlockState road = pal.get(st.surface, "ptmuk:tarmac");
        BlockState roadSlab = slab(pal.get(st.surface + "_slab", "ptmuk:tarmac_slab"));
        BlockState kerb = pal.get("ptmuk:concrete_kerb", "smooth_stone");
        BlockState kerbSlab = slab(pal.get("ptmuk:concrete_kerb_slab", "smooth_stone_slab"));
        BlockState paving = pal.get(st.footSurface, "ptmuk:paving_slabs");
        BlockState pavingSlab = slab(pal.get(st.footSurface + "_slab", "ptmuk:paving_slabs_slab"));
        BlockState tactile = pal.get("ptmuk:tactile_paving_red", "red_concrete");
        BlockState tactileSlab = slab(pal.get("ptmuk:tactile_paving_red_slab", "red_concrete"));
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState whiteSlab = slab(Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());

        boolean half = isHalf(t);
        int base = (int) Math.floor(t);
        BlockState full, top;
        switch (kind) {
            case Layout.ROAD -> {
                full = road;
                top = roadSlab;
            }
            case Layout.PAINT -> {
                full = white;
                top = whiteSlab;
            }
            case Layout.DOME -> {
                full = white;
                top = white;
            }
            case Layout.KERB, Layout.ISLAND_KERB -> {
                full = kerb;
                top = kerbSlab;
            }
            case Layout.FOOTWAY -> {
                full = paving;
                top = pavingSlab;
            }
            case Layout.TACTILE -> {
                full = tactile;
                top = tactileSlab;
            }
            default -> {
                full = grass;
                top = grass;
                half = false;
                base = (int) Math.ceil(t);
            }
        }
        // the surface: a full block with its top at t, or a slab on a full block for half heights
        int surfaceY;
        if (half) {
            set(job, new BlockPos(x, base - 1, z), full);
            set(job, new BlockPos(x, base, z), top);
            surfaceY = base;
        } else {
            set(job, new BlockPos(x, base - 1, z), full);
            surfaceY = base - 1;
        }
        int bottom = base - 1;
        // fill under it down to solid ground
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = bottom - 1, depth = 1; y > bottom - MAX_FILL && y > level.getMinBuildHeight(); y--, depth++) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (!WorldTerrain.natural(s) && s.getFluidState().isEmpty()) {
                break;
            }
            set(job, p.immutable(), depth <= 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
        if (kind == Layout.VERGE || kind == Layout.ISLAND || kind == Layout.RESERVATION || kind == Layout.BARRIER) {
            BlockState below = level.getBlockState(p.set(x, bottom - 1, z));
            if (below.is(Blocks.GRASS_BLOCK)) {
                set(job, p.immutable(), Blocks.DIRT.defaultBlockState());
            }
        }
        // clear above: everything within head room, the rest of any hill, and tree trunks
        int ground = WorldTerrain.scan(level, x, z, surfaceY + 1);
        int trees = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int clearTo = Math.max(surfaceY + CLEAR_ABOVE, ground - 1);
        for (int y = surfaceY + 1; y <= Math.max(clearTo, trees); y++) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.isAir()) {
                continue;
            }
            if (y <= clearTo || s.is(BlockTags.LOGS)) {
                set(job, p.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
        if (kind == Layout.DOME) {
            set(job, new BlockPos(x, surfaceY + 1, z), whiteSlab);
        }
        if (kind == Layout.BARRIER) {
            BlockPos at = new BlockPos(x, surfaceY + 1, z);
            set(job, at, pal.get("ptmuk:armco_barrier", "iron_bars"));
            job.joins.add(at);
        }
        int m = layout.mark[idx];
        if (m != Layout.M_NONE && kind == Layout.ROAD) {
            BlockState mark = marking(pal, m, layout.markRot[idx], half);
            set(job, new BlockPos(x, surfaceY + 1, z), mark);
        }
    }

    static BlockState marking(Palette pal, int m, int rot, boolean slab) {
        String id = switch (m & 0x3f) {
            case Layout.M_LINE -> "ptm2:center_line_marking";
            case Layout.M_DOUBLE -> "ptm2:double_line_marking";
            case Layout.M_HALF -> "ptm2:half_line_marking";
            case Layout.M_GIVE_WAY -> "ptm2:half_double_line_marking";
            case Layout.M_STOP -> "ptm2:stop_line_marking";
            case Layout.M_TRIANGLE -> "ptm2:triangles_marking";
            case Layout.M_ZEBRA -> "ptm2:pedestrian_crossing_line_marking";
            case Layout.M_HATCH -> "ptm2:restricted_area_marking";
            case Layout.M_ZIGZAG -> "ptm2:zig_zag_marking";
            case Layout.M_ARROW -> "ptm2:arrow_marking";
            default -> null;
        };
        BlockState s = pal.get(id, (m & Layout.YELLOW) != 0 ? "yellow_carpet" : "white_carpet");
        s = with(s, "rotation", rot);
        s = with(s, "slab", slab);
        s = with(s, "temporary", (m & Layout.YELLOW) != 0);
        s = with(s, "condition", "dry");
        return with(s, "waterlogged", false);
    }

    /** The ground beside the road: cut down or built up to a 1-in-1 slope meeting the edge. */
    private static void blend(Job job, int x, int z, int edgeTop, float dist) {
        ServerLevel level = job.level;
        int ground = WorldTerrain.scan(level, x, z, edgeTop);
        int reach = Math.round(dist);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (ground > edgeTop + reach) {
            int newTop = edgeTop + reach;
            int trees = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int y = newTop; y <= Math.max(ground, trees); y++) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (!s.isAir() && (y < ground || s.is(BlockTags.LOGS) || WorldTerrain.natural(s))) {
                    set(job, p.immutable(), Blocks.AIR.defaultBlockState());
                }
            }
            BlockState under = level.getBlockState(p.set(x, newTop - 1, z));
            if (under.is(Blocks.DIRT) || under.is(Blocks.STONE) || under.is(BlockTags.BASE_STONE_OVERWORLD)) {
                set(job, p.immutable(), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        } else if (ground < edgeTop - reach) {
            int newTop = edgeTop - reach;
            for (int y = ground; y < newTop; y++) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (WorldTerrain.natural(s) || !s.getFluidState().isEmpty()) {
                    set(job, p.immutable(), y == newTop - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                }
            }
            BlockState under = level.getBlockState(p.set(x, ground - 1, z));
            if (under.is(Blocks.GRASS_BLOCK) && newTop - 1 > ground - 1) {
                set(job, p.immutable(), Blocks.DIRT.defaultBlockState());
            }
        }
    }

    private static void fixture(Job job, Layout.Fixture f) {
        if (f.type() == Layout.FixtureType.SIGNAL) {
            signal(job, f);
            return;
        }
        float t = f.top();
        int y0;
        if (isHalf(t)) {
            int b = (int) Math.floor(t);
            set(job, new BlockPos(f.x(), b, f.z()), job.palette.get("ptmuk:paving_slabs", "smooth_stone"));
            y0 = b + 1;
        } else {
            y0 = (int) t;
        }
        BlockState pole = job.palette.get("ptmuk:sign_pole_galvanised", "iron_bars");
        for (int k = 0; k < 5; k++) {
            set(job, new BlockPos(f.x(), y0 + k, f.z()), with(with(pole, "base", k == 0), "cap", false));
        }
        Direction facing = Direction.fromYRot(f.rot8() * 45.0);
        set(job, new BlockPos(f.x(), y0 + 5, f.z()), with(job.palette.get("ptmuk:street_light_led", "lantern"), "facing",
                facing.getSerializedName()));
    }

    /** A black signal pole with an LED head on top facing the traffic coming up to the stop line. */
    private static void signal(Job job, Layout.Fixture f) {
        float t = f.top();
        int y0;
        if (isHalf(t)) {
            int b = (int) Math.floor(t);
            set(job, new BlockPos(f.x(), b, f.z()), job.palette.get("ptmuk:paving_slabs", "smooth_stone"));
            y0 = b + 1;
        } else {
            y0 = (int) t;
        }
        BlockState pole = job.palette.get("ptmuk:signal_pole_black", "iron_bars");
        BlockState head = job.palette.get("ptmuk:led_signal", "ptm2:traffic_light");
        head = with(head, "rotation", f.rot8());
        head = with(head, "attachment", "center");
        if (f.level() == 0) {
            for (int k = 0; k < 2; k++) {
                set(job, new BlockPos(f.x(), y0 + k, f.z()), with(with(pole, "base", k == 0), "cap", false));
            }
            set(job, new BlockPos(f.x(), y0 + 2, f.z()), head);
        } else {
            // a second approach's head higher up the same pole
            set(job, new BlockPos(f.x(), y0 + 3, f.z()), with(with(pole, "base", false), "cap", false));
            set(job, new BlockPos(f.x(), y0 + 4, f.z()), head);
        }
    }

    // ------------------------------------------------------------------ undo

    public static void undo(ServerPlayer player) {
        if (busy(player.getUUID())) {
            player.displayClientMessage(Component.literal("Wait for the road to finish building first"), true);
            return;
        }
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            player.displayClientMessage(Component.literal("No roads to undo"), true);
            return;
        }
        Undo undo = history.pop();
        ServerLevel level = player.server.getLevel(undo.dimension());
        if (level == null) {
            return;
        }
        List<Saved> blocks = undo.blocks();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Saved s = blocks.get(i);
            level.setBlock(s.pos(), s.state(), FLAGS);
        }
        for (Saved s : blocks) {
            if (s.nbt() != null) {
                BlockEntity be = level.getBlockEntity(s.pos());
                if (be != null) {
                    be.load(s.nbt());
                    be.setChanged();
                }
            }
        }
        player.displayClientMessage(Component.literal("Undid the last road build (" + blocks.size() + " blocks put back)"), true);
    }

    /** Ground heights for the server: chunks that aren't loaded yet get loaded. */
    private static final class LoadingTerrain implements Terrain {
        private final ServerLevel level;
        private final WorldTerrain inner;

        LoadingTerrain(ServerLevel level, int fallback) {
            this.level = level;
            this.inner = new WorldTerrain(level, fallback);
        }

        @Override
        public int groundTop(int x, int z) {
            level.getChunk(x >> 4, z >> 4);
            return inner.groundTop(x, z);
        }
    }
}
