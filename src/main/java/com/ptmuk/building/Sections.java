package com.ptmuk.building;

import com.ptmuk.block.Walkways;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Big sections of the world, copied to files and pasted back anywhere: in this world or another
 * one, turned and mirrored. A section is a folder under the game folder's ptmuk/sections (so every
 * world on this game, or this server, sees it) of 32 x 32 column tiles, each a compressed NBT of
 * its block palette, its blocks and its block entities' data, and info.dat (written last, so a
 * half-copied section isn't listed). Copying and pasting go a little each tick, so a whole town
 * doesn't freeze the game. Pasting first copies what's there into the player's undo section.
 */
public final class Sections {
    public static final int TILE = 32;
    /** How far below the lower corner a copy goes: foundations, cellars and the ground they stand in. */
    public static final int BELOW = 8;
    /** The biggest section across, either way. */
    public static final int MAX_SIDE = 4096;
    private static final long BUDGET_NS = 30_000_000L;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** A saved section: its size, the corner first clicked (where it's pasted from) and where it was copied from. */
    public record Info(String name, int sx, int sy, int sz, int ax, int ay, int az, BlockPos origin) {
        BlockPos anchor() {
            return new BlockPos(ax, ay, az);
        }

        int tiles() {
            return ((sx + TILE - 1) / TILE) * ((sz + TILE - 1) / TILE);
        }
    }

    private Sections() {
    }

    static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("ptmuk").resolve("sections");
    }

    /** A safe folder name from what the player typed (letters, digits, - and _), or null. */
    public static String clean(String name) {
        String s = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() || s.length() > 40 ? null : s;
    }

    private static String undoName(UUID player) {
        return ".undo-" + player;
    }

    public static List<Info> list() {
        List<Info> out = new ArrayList<>();
        try (Stream<Path> dirs = Files.isDirectory(root()) ? Files.list(root()) : Stream.empty()) {
            dirs.map(d -> d.getFileName().toString()).filter(n -> !n.startsWith(".")).sorted().forEach(n -> {
                Info i = info(n);
                if (i != null) {
                    out.add(i);
                }
            });
        } catch (IOException ignored) {
            // no sections folder yet
        }
        return out;
    }

    public static Info info(String name) {
        try {
            Path f = root().resolve(name).resolve("info.dat");
            if (!Files.isRegularFile(f)) {
                return null;
            }
            CompoundTag t = NbtIo.readCompressed(f.toFile());
            return new Info(name, t.getInt("sx"), t.getInt("sy"), t.getInt("sz"), t.getInt("ax"), t.getInt("ay"), t.getInt("az"),
                    new BlockPos(t.getInt("ox"), t.getInt("oy"), t.getInt("oz")));
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean delete(String name) {
        Path dir = root().resolve(name);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Where a section's block at `local` goes, pasted with its anchor at target. */
    static BlockPos place(BlockPos local, BlockPos anchor, BlockPos target, Rotation rot, Mirror mirror) {
        return StructureTemplate.transform(local, mirror, rot, anchor).offset(target.getX() - anchor.getX(),
                target.getY() - anchor.getY(), target.getZ() - anchor.getZ());
    }

    /** The world box a section takes pasted with its anchor at target. */
    public static BoundingBox box(int sx, int sy, int sz, BlockPos anchor, BlockPos target, Rotation rot, Mirror mirror) {
        return BoundingBox.fromCorners(place(BlockPos.ZERO, anchor, target, rot, mirror),
                place(new BlockPos(sx - 1, sy - 1, sz - 1), anchor, target, rot, mirror));
    }

    // ------------------------------------------------------------------ jobs

    private interface Job {
        /** Work until `until` (System.nanoTime()); true once finished. */
        boolean step(long until) throws IOException;

        /** How far through, 0 to 1. */
        double done();

        String what();

        UUID player();

        /** Once finished: what to tell the player. */
        String finished();
    }

    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static int ticks;

    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    /**
     * Copy the box between two corners (from BELOW under the lower one up to the top of whatever's
     * there) to a section, pasted later from the first corner.
     */
    public static String copy(ServerLevel level, UUID player, String name, BlockPos c1, BlockPos c2) {
        int y0 = Math.max(level.getMinBuildHeight(), Math.min(c1.getY(), c2.getY()) - BELOW);
        return queue(new Copy(level, player, name, c1, c2, y0, Math.max(c1.getY(), c2.getY()), true));
    }

    /** Paste a section with its first corner at target, after copying what's there for undo; what to tell the player, or null. */
    public static String paste(ServerLevel level, UUID player, Info s, BlockPos target, Rotation rot, Mirror mirror, boolean keepAir) {
        BoundingBox box = box(s.sx, s.sy, s.sz, s.anchor(), target, rot, mirror);
        if (box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()) {
            return "It doesn't fit between the bottom and top of the world there";
        }
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ()), max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
        queue(new Copy(level, player, undoName(player), min, max, box.minY(), box.maxY(), false));
        return queue(new Paste(level, player, s, target, rot, mirror, keepAir, false));
    }

    /** Put back what the last paste covered. */
    public static String undo(ServerLevel level, UUID player) {
        Info u = info(undoName(player));
        if (u == null) {
            return "No paste to undo";
        }
        return queue(new Paste(level, player, u, u.origin(), Rotation.NONE, Mirror.NONE, false, true));
    }

    private static String queue(Job job) {
        JOBS.addLast(job);
        return JOBS.size() > 1 && !(job instanceof Paste && JOBS.size() == 2 && JOBS.peekFirst() instanceof Copy c && c.name.startsWith("."))
                ? job.what() + ": waiting for " + JOBS.peekFirst().what() + " to finish" : null;
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Job job = JOBS.peekFirst();
        ServerPlayer player = server.getPlayerList().getPlayer(job.player());
        boolean finished;
        try {
            finished = job.step(System.nanoTime() + BUDGET_NS);
        } catch (IOException | RuntimeException e) {
            com.ptmuk.PtmUk.LOGGER.error("Section job failed", e);
            JOBS.pollFirst();
            if (job instanceof Copy) {
                JOBS.removeIf(j -> j.player().equals(job.player()) && j instanceof Paste p && !p.undo);   // no paste without its undo
            }
            if (player != null) {
                player.displayClientMessage(Component.literal(job.what() + " failed: " + e.getMessage()), false);
            }
            return;
        }
        if (finished) {
            JOBS.pollFirst();
            if (player != null && !job.finished().isEmpty()) {
                player.displayClientMessage(Component.literal(job.finished()), false);
            }
        } else if (player != null && ++ticks % 20 == 0) {
            player.displayClientMessage(Component.literal(job.what() + ": " + Math.round(job.done() * 100) + "%"), true);
        }
    }

    /** Game tests: run everything queued now. */
    public static void runAll() throws IOException {
        while (!JOBS.isEmpty()) {
            if (JOBS.peekFirst().step(Long.MAX_VALUE)) {
                JOBS.pollFirst();
            }
        }
    }

    private static final class Copy implements Job {
        final ServerLevel level;
        final UUID player;
        final String name;
        final Path dir;
        final int x0, z0, y0, sx, sz, ax, ay, az;
        int y1, sy;
        final boolean findTop;
        /** Finding the top: the next chunk to look at. */
        int heightChunk;
        int tile, col;
        Map<BlockState, Integer> ids;
        List<BlockState> palette;
        int[] cells;
        ListTag bes;
        int tw, td;
        boolean started;

        Copy(ServerLevel level, UUID player, String name, BlockPos c1, BlockPos c2, int y0, int y1, boolean findTop) {
            this.level = level;
            this.player = player;
            this.name = name;
            this.dir = root().resolve(name);
            this.x0 = Math.min(c1.getX(), c2.getX());
            this.z0 = Math.min(c1.getZ(), c2.getZ());
            this.sx = Math.abs(c1.getX() - c2.getX()) + 1;
            this.sz = Math.abs(c1.getZ() - c2.getZ()) + 1;
            this.y0 = y0;
            this.y1 = Math.min(level.getMaxBuildHeight() - 1, y1);
            this.findTop = findTop;
            this.ax = c1.getX() - x0;
            this.ay = c1.getY() - y0;
            this.az = c1.getZ() - z0;
        }

        @Override
        public boolean step(long until) throws IOException {
            if (!started) {
                started = true;
                delete(name);                                  // copying over an old one
                Files.createDirectories(dir);
            }
            int cx0 = x0 >> 4, cz0 = z0 >> 4, cw = ((x0 + sx - 1) >> 4) - cx0 + 1, chunks = cw * (((z0 + sz - 1) >> 4) - cz0 + 1);
            while (findTop && heightChunk < chunks) {
                LevelChunk chunk = level.getChunk(cx0 + heightChunk % cw, cz0 + heightChunk / cw);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = chunk.getPos().getMinBlockX() + x, wz = chunk.getPos().getMinBlockZ() + z;
                        if (wx >= x0 && wx < x0 + sx && wz >= z0 && wz < z0 + sz) {
                            y1 = Math.max(y1, Math.min(level.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)));
                        }
                    }
                }
                heightChunk++;
                if (System.nanoTime() > until) {
                    return false;
                }
            }
            sy = y1 - y0 + 1;
            int nx = (sx + TILE - 1) / TILE, tiles = nx * ((sz + TILE - 1) / TILE);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            while (tile < tiles) {
                int tx = tile % nx, tz = tile / nx;
                if (cells == null) {
                    tw = Math.min(TILE, sx - tx * TILE);
                    td = Math.min(TILE, sz - tz * TILE);
                    cells = new int[tw * td * sy];
                    ids = new HashMap<>();
                    palette = new ArrayList<>();
                    bes = new ListTag();
                }
                LevelChunk chunk = null;
                for (; col < tw * td; col++) {
                    if (System.nanoTime() > until) {
                        return false;
                    }
                    int lx = tx * TILE + col / td, lz = tz * TILE + col % td, wx = x0 + lx, wz = z0 + lz;
                    if (chunk == null || chunk.getPos().x != wx >> 4 || chunk.getPos().z != wz >> 4) {
                        chunk = level.getChunk(wx >> 4, wz >> 4);
                    }
                    for (int ly = 0; ly < sy; ly++) {
                        BlockState s = chunk.getBlockState(p.set(wx, y0 + ly, wz));
                        cells[col * sy + ly] = ids.computeIfAbsent(s, k -> {
                            palette.add(k);
                            return palette.size() - 1;
                        });
                        if (s.hasBlockEntity()) {
                            BlockEntity be = chunk.getBlockEntity(p);
                            if (be != null) {
                                CompoundTag t = be.saveWithFullMetadata();
                                t.putInt("lx", lx);
                                t.putInt("ly", ly);
                                t.putInt("lz", lz);
                                bes.add(t);
                            }
                        }
                    }
                }
                CompoundTag t = new CompoundTag();
                t.putInt("w", tw);
                t.putInt("d", td);
                ListTag pal = new ListTag();
                palette.forEach(s -> pal.add(NbtUtils.writeBlockState(s)));
                t.put("palette", pal);
                t.putIntArray("cells", cells);
                t.put("bes", bes);
                NbtIo.writeCompressed(t, dir.resolve("t" + tile + ".dat").toFile());
                cells = null;
                col = 0;
                tile++;
            }
            CompoundTag info = new CompoundTag();
            info.putInt("sx", sx);
            info.putInt("sy", sy);
            info.putInt("sz", sz);
            info.putInt("ax", ax);
            info.putInt("ay", ay);
            info.putInt("az", az);
            info.putInt("ox", x0);
            info.putInt("oy", y0);
            info.putInt("oz", z0);
            NbtIo.writeCompressed(info, dir.resolve("info.dat").toFile());
            return true;
        }

        @Override
        public double done() {
            int tiles = ((sx + TILE - 1) / TILE) * ((sz + TILE - 1) / TILE);
            return (tile + (tw * td == 0 ? 0 : (double) col / (tw * td))) / Math.max(1, tiles);
        }

        @Override
        public String what() {
            return name.startsWith(".") ? "Saving what's there for undo" : "Copying " + name;
        }

        @Override
        public UUID player() {
            return player;
        }

        @Override
        public String finished() {
            return name.startsWith(".") ? "" : "Copied " + name + ": " + sx + " x " + sz + ", " + sy + " high. Sneak + right-click to paste it.";
        }
    }

    private static final class Paste implements Job {
        final ServerLevel level;
        final UUID player;
        final Info s;
        final BlockPos target;
        final Rotation rot;
        final Mirror mirror;
        final boolean keepAir, undo;
        int tile, col, tw, td;
        BlockState[] pal;
        int[] cells;
        ListTag bes;

        Paste(ServerLevel level, UUID player, Info s, BlockPos target, Rotation rot, Mirror mirror, boolean keepAir, boolean undo) {
            this.level = level;
            this.player = player;
            this.s = s;
            this.target = target;
            this.rot = rot;
            this.mirror = mirror;
            this.keepAir = keepAir;
            this.undo = undo;
        }

        @Override
        public boolean step(long until) throws IOException {
            int nx = (s.sx + TILE - 1) / TILE;
            BlockPos anchor = s.anchor();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            var blocks = level.holderLookup(Registries.BLOCK);
            while (tile < s.tiles()) {
                int tx = tile % nx, tz = tile / nx;
                if (cells == null) {
                    CompoundTag t = NbtIo.readCompressed(root().resolve(s.name).resolve("t" + tile + ".dat").toFile());
                    tw = t.getInt("w");
                    td = t.getInt("d");
                    ListTag list = t.getList("palette", Tag.TAG_COMPOUND);
                    pal = new BlockState[list.size()];
                    for (int i = 0; i < pal.length; i++) {
                        pal[i] = NbtUtils.readBlockState(blocks, list.getCompound(i)).mirror(mirror).rotate(rot);
                    }
                    cells = t.getIntArray("cells");
                    bes = t.getList("bes", Tag.TAG_COMPOUND);
                    if (cells.length != tw * td * s.sy) {
                        throw new IOException("tile " + tile + " of " + s.name + " is damaged");
                    }
                }
                for (; col < tw * td; col++) {
                    if (System.nanoTime() > until) {
                        return false;
                    }
                    BlockPos base = place(new BlockPos(tx * TILE + col / td, 0, tz * TILE + col % td), anchor, target, rot, mirror);
                    LevelChunk chunk = level.getChunk(base.getX() >> 4, base.getZ() >> 4);
                    for (int ly = 0; ly < s.sy; ly++) {
                        BlockState state = pal[cells[col * s.sy + ly]];
                        if (keepAir && state.isAir()) {
                            continue;
                        }
                        p.set(base.getX(), base.getY() + ly, base.getZ());
                        BlockState old = chunk.getBlockState(p);
                        if (old == state) {
                            continue;
                        }
                        if (old.hasBlockEntity()) {
                            Clearable.tryClear(level.getBlockEntity(p));   // so a chest it replaces doesn't spill
                        }
                        level.setBlock(p, state, FLAGS);
                        Walkways.placed(level, p);
                    }
                }
                for (int i = 0; i < bes.size(); i++) {
                    CompoundTag t = bes.getCompound(i).copy();
                    BlockPos at = place(new BlockPos(t.getInt("lx"), t.getInt("ly"), t.getInt("lz")), anchor, target, rot, mirror);
                    BlockEntity be = level.getBlockEntity(at);
                    if (be != null) {
                        t.putInt("x", at.getX());
                        t.putInt("y", at.getY());
                        t.putInt("z", at.getZ());
                        be.load(t);
                        be.setChanged();
                        level.sendBlockUpdated(at, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                cells = null;
                col = 0;
                tile++;
            }
            if (undo) {
                delete(s.name);                                  // undone: nothing left to undo
            }
            return true;
        }

        @Override
        public double done() {
            return (tile + (tw * td == 0 ? 0 : (double) col / (tw * td))) / Math.max(1, s.tiles());
        }

        @Override
        public String what() {
            return undo ? "Undoing the paste" : "Pasting " + s.name;
        }

        @Override
        public UUID player() {
            return player;
        }

        @Override
        public String finished() {
            return undo ? "Put back what the paste covered" : "Pasted " + s.name + ". Sneak + right-click and Undo paste to take it back.";
        }
    }
}
