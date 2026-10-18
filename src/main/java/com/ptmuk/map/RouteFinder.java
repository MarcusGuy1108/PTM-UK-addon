package com.ptmuk.map;

import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.RouteMessage;
import com.ptmuk.road.OsmMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Directions along the roads: a search over the road surface (tarmac, PTM2's roads) in two-block
 * cells, a slice each tick so the server doesn't stall, reading chunks that aren't loaded straight
 * from the save (like the planner's map) rather than loading them. The way it finds is then cut
 * into turns, each named after the road it turns onto where the road has a name.
 */
@Mod.EventBusSubscriber(modid = com.ptmuk.PtmUk.MOD_ID)
public final class RouteFinder {
    private static final int CELL = 2;
    /** Cells looked at per tick, and in all. */
    private static final int PER_TICK = 20000, MAX_NODES = 600000;
    /** How far from a road the start and end can be (cells). */
    private static final int SNAP = 24;
    private static final int IN_FLIGHT = 24;

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private RouteFinder() {
    }

    /** Is this a road surface? */
    static boolean road(BlockState s) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        if (id == null) {
            return false;
        }
        String p = id.getPath();
        return p.contains("tarmac") || p.endsWith("_road") || p.endsWith("_road_slab") || (p.contains("asphalt") && !p.contains("sidewalk"));
    }

    /** The surface at a column: down from the top past thin things (markings, carpets), whatever height it's at. */
    public static BlockState top(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        for (int y = top; y > top - 3; y--) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (road(s) || s.isCollisionShapeFullBlock(chunk, p)) {
                return s;
            }
        }
        return chunk.getBlockState(p.set(x, top, z));
    }

    private static final class Job {
        final ServerLevel level;
        final double fromX, fromZ, toX, toZ;
        final String name;
        /** Road cells of each chunk read so far: 64 bits (an 8 x 8 grid of cells). */
        final Map<Long, Long> masks = new HashMap<>();
        final Set<Long> reading = new HashSet<>();
        /** Chunks asked for and not yet being read. */
        final Set<Long> wanted = new java.util.LinkedHashSet<>();
        /** Cells waiting for their chunk to be read. */
        final Map<Long, List<long[]>> waiting = new HashMap<>();
        final PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Long.compare(a[1], b[1]));
        final Map<Long, Float> g = new HashMap<>();
        final Map<Long, Long> from = new HashMap<>();
        long start = Long.MIN_VALUE, goal = Long.MIN_VALUE;
        int expanded;
        int inFlight;
        /** Load chunks on the spot rather than read them in the background (for tests). */
        boolean sync;

        Job(ServerLevel level, double fromX, double fromZ, double toX, double toZ, String name) {
            this.level = level;
            this.fromX = fromX;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toZ = toZ;
            this.name = name;
        }
    }

    public static void request(ServerPlayer player, double toX, double toZ, String name) {
        JOBS.put(player.getUUID(), new Job(player.serverLevel(), player.getX(), player.getZ(), toX, toZ, name));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JOBS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        JOBS.entrySet().removeIf(e -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || player.serverLevel() != e.getValue().level) {
                return true;
            }
            RouteMessage done = step(e.getValue());
            if (done != null) {
                PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), done);
            }
            return done != null;
        });
    }

    /** For tests: a route worked out straight away (round loaded chunks only). */
    public static RouteMessage findNow(ServerLevel level, double fromX, double fromZ, double toX, double toZ, String name) {
        Job job = new Job(level, fromX, fromZ, toX, toZ, name);
        job.sync = true;
        for (int i = 0; i < 200; i++) {
            RouteMessage done = step(job);
            if (done != null) {
                return done;
            }
        }
        return RouteMessage.none("Took too long");
    }

    // ------------------------------------------------------------------ cells and chunks

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int kz(long k) {
        return (int) k;
    }

    private static long chunkOf(long cell) {
        return key(Math.floorDiv(kx(cell) * CELL, 16), Math.floorDiv(kz(cell) * CELL, 16));
    }

    /** True or false for a road cell, or null if its chunk hasn't been read yet (it asks for it). */
    private static Boolean isRoad(Job job, long cell) {
        long ck = chunkOf(cell);
        Long mask = job.masks.get(ck);
        if (mask == null) {
            want(job, ck);
            mask = job.masks.get(ck);                       // a loaded chunk is read straight away
            if (mask == null) {
                return null;
            }
        }
        int bit = Math.floorMod(kz(cell), 8) * 8 + Math.floorMod(kx(cell), 8);
        return (mask >>> bit & 1) != 0;
    }

    /** Get a chunk's road cells: straight away if it's loaded, else queue it to be read from the save. */
    private static void want(Job job, long ck) {
        if (job.masks.containsKey(ck) || job.reading.contains(ck)) {
            return;
        }
        LevelChunk loaded = job.sync ? job.level.getChunk(kx(ck), kz(ck)) : job.level.getChunkSource().getChunkNow(kx(ck), kz(ck));
        if (loaded != null) {
            job.masks.put(ck, mask(loaded));
            return;
        }
        job.wanted.add(ck);
    }

    /** Start reading queued chunks, a few at a time, in the background. */
    private static void pump(Job job) {
        for (var it = job.wanted.iterator(); it.hasNext() && job.inFlight < IN_FLIGHT; ) {
            long ck = it.next();
            it.remove();
            job.reading.add(ck);
            job.inFlight++;
            job.level.getChunkSource().chunkMap.read(new ChunkPos(kx(ck), kz(ck))).whenCompleteAsync((tag, err) -> {
                job.inFlight--;
                job.reading.remove(ck);
                job.masks.put(ck, err == null && tag.isPresent() ? mask(job.level, tag.get()) : 0L);
                List<long[]> back = job.waiting.remove(ck);
                if (back != null) {
                    job.open.addAll(back);
                }
            }, job.level.getServer());
        }
    }

    /** The surface of a column: down from the top past thin things (markings, carpets, signs), at most three blocks. */
    private static long mask(LevelChunk chunk) {
        long m = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = top; y > top - 3; y--) {
                    BlockState s = chunk.getBlockState(p.set(x0 + x, y, z0 + z));
                    if (road(s)) {
                        m |= 1L << ((z / CELL) * 8 + x / CELL);
                        break;
                    }
                    if (s.isCollisionShapeFullBlock(chunk, p)) {
                        break;
                    }
                }
            }
        }
        return m;
    }

    /** The same from a chunk as saved (see RoadMapTiles.fromSave for the format). */
    private static long mask(ServerLevel level, CompoundTag tag) {
        if (tag.getInt("DataVersion") < 2860) {
            return 0;
        }
        long[] solid = tag.getCompound("Heightmaps").getLongArray("MOTION_BLOCKING_NO_LEAVES");
        if (solid.length == 0) {
            return 0;
        }
        int minY = level.getMinBuildHeight(), bits = Mth.ceillog2(level.getHeight() + 1);
        var blocks = level.holderLookup(Registries.BLOCK);
        Map<Integer, BlockState[]> palettes = new HashMap<>();
        Map<Integer, long[]> datas = new HashMap<>();
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            CompoundTag states = s.getCompound("block_states");
            ListTag pal = states.getList("palette", Tag.TAG_COMPOUND);
            if (pal.isEmpty()) {
                continue;
            }
            BlockState[] palette = new BlockState[pal.size()];
            for (int k = 0; k < palette.length; k++) {
                palette[k] = NbtUtils.readBlockState(blocks, pal.getCompound(k));
            }
            palettes.put((int) s.getByte("Y"), palette);
            datas.put((int) s.getByte("Y"), states.getLongArray("data"));
        }
        long m = 0;
        for (int i = 0; i < 256; i++) {
            int x = i & 15, z = i >> 4;
            int top = (int) unpack(solid, i, bits) + minY - 1;
            for (int y = top; y > top - 3; y--) {
                BlockState s = block(palettes, datas, x, y, z);
                if (road(s)) {
                    m |= 1L << ((z / CELL) * 8 + x / CELL);
                    break;
                }
                if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                    break;
                }
            }
        }
        return m;
    }

    private static BlockState block(Map<Integer, BlockState[]> palettes, Map<Integer, long[]> datas, int x, int y, int z) {
        int sy = Math.floorDiv(y, 16);
        BlockState[] palette = palettes.get(sy);
        if (palette == null) {
            return Blocks.AIR.defaultBlockState();
        }
        long[] data = datas.get(sy);
        if (data.length == 0) {
            return palette[0];
        }
        int i = (y & 15) << 8 | z << 4 | x;
        int v = (int) unpack(data, i, Math.max(4, Mth.ceillog2(palette.length)));
        return v < palette.length ? palette[v] : Blocks.AIR.defaultBlockState();
    }

    private static long unpack(long[] data, int index, int bits) {
        int per = 64 / bits, li = index / per;
        return li >= data.length ? 0 : (data[li] >>> ((index % per) * bits)) & ((1L << bits) - 1);
    }

    // ------------------------------------------------------------------ the search

    /** Work on a route for a while; the answer when it's done (found or not), else null. */
    private static RouteMessage step(Job job) {
        pump(job);
        if (job.start == Long.MIN_VALUE || job.goal == Long.MIN_VALUE) {
            Long s = snap(job, job.fromX, job.fromZ), t = snap(job, job.toX, job.toZ);
            if (s == null || t == null) {
                return null;                                // still reading the chunks round them
            }
            if (s == Long.MAX_VALUE || t == Long.MAX_VALUE) {
                return RouteMessage.none(s == Long.MAX_VALUE ? "You're not near a road" : "There's no road near " + job.name);
            }
            job.start = s;
            job.goal = t;
            job.g.put(s, 0f);
            job.open.add(new long[]{s, cost(0, s, t)});
        }
        for (int n = 0; n < PER_TICK; n++) {
            long[] top = job.open.poll();
            if (top == null) {
                boolean busy = !job.waiting.isEmpty() || job.inFlight > 0 || !job.wanted.isEmpty();
                return busy ? null : RouteMessage.none("Couldn't find a way there by road");
            }
            long cell = top[0];
            if (cell == job.goal) {
                return finish(job);
            }
            if (++job.expanded > MAX_NODES) {
                return RouteMessage.none("That's too far to work out a route");
            }
            float gc = job.g.getOrDefault(cell, Float.MAX_VALUE);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    long next = key(kx(cell) + dx, kz(cell) + dz);
                    Boolean r = isRoad(job, next);
                    if (r == null) {
                        // come back to this cell when that chunk has been read
                        job.waiting.computeIfAbsent(chunkOf(next), k -> new ArrayList<>()).add(top);
                        continue;
                    }
                    if (!r) {
                        continue;
                    }
                    float ng = gc + (dx != 0 && dz != 0 ? 1.4142f : 1f);
                    if (ng < job.g.getOrDefault(next, Float.MAX_VALUE)) {
                        job.g.put(next, ng);
                        job.from.put(next, cell);
                        job.open.add(new long[]{next, cost(ng, next, job.goal)});
                    }
                }
            }
        }
        return null;
    }

    /** Priority: cost so far plus the straight line to go, as a fixed-point number. */
    private static long cost(float g, long cell, long goal) {
        return (long) ((g + Math.hypot(kx(cell) - kx(goal), kz(cell) - kz(goal))) * 1000);
    }

    /** The nearest road cell to a point: null while the chunks round it are read, MAX_VALUE if there's none. */
    private static Long snap(Job job, double x, double z) {
        int cx = Mth.floor(x / CELL), cz = Mth.floor(z / CELL);
        Long best = null;
        double bestD = Double.MAX_VALUE;
        boolean unread = false;
        for (int dx = -SNAP; dx <= SNAP; dx++) {
            for (int dz = -SNAP; dz <= SNAP; dz++) {
                double d = dx * dx + dz * dz;
                if (d > SNAP * SNAP || d >= bestD) {
                    continue;
                }
                Boolean r = isRoad(job, key(cx + dx, cz + dz));
                if (r == null) {
                    unread = true;
                } else if (r) {
                    best = key(cx + dx, cz + dz);
                    bestD = d;
                }
            }
        }
        return unread ? null : best == null ? Long.MAX_VALUE : best;
    }

    // ------------------------------------------------------------------ turns

    private static RouteMessage finish(Job job) {
        List<double[]> cells = new ArrayList<>();
        for (Long c = job.goal; c != null; c = job.from.get(c)) {
            cells.add(0, new double[]{kx(c) * CELL + 1.0, kz(c) * CELL + 1.0});
        }
        cells.add(0, new double[]{job.fromX, job.fromZ});
        cells.add(new double[]{job.toX, job.toZ});
        double[] x = new double[cells.size()], z = new double[cells.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = cells.get(i)[0];
            z[i] = cells.get(i)[1];
        }
        boolean[] keep = OsmMap.simplify(x, z, 0, x.length - 1, 2.5);
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                pts.add(new float[]{(float) x[i], (float) z[i]});
            }
        }
        return Directions.of(pts, MapPlaces.roads(job.level), job.name);
    }
}
