package com.ptmuk.road;

import com.ptmuk.PtmUk;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.RoadMapTileMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Road Planner's map, from the server: the client can only draw the chunks it has loaded
 * (just a couple round you with a short render distance, as with Distant Horizons), so the
 * server sends the rest a chunk at a time, nearest first. Loaded chunks are read directly;
 * others are read from the world's save without loading them into the world. Never generates
 * terrain: chunks that were never made stay blank.
 */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID)
public final class RoadMapTiles {
    /** Furthest the map reaches from where it was opened, in chunks. */
    public static final int MAX_RADIUS = 32;
    private static final int PER_TICK = 24;
    private static final int IN_FLIGHT = 48;
    /** A colour of 0 means nothing known there; heights of this mean the same. */
    public static final short UNKNOWN = Short.MIN_VALUE;

    private static final class Job {
        final ServerLevel level;
        final Deque<ChunkPos> todo;
        int inFlight;

        Job(ServerLevel level, Deque<ChunkPos> todo) {
            this.level = level;
            this.todo = todo;
        }
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private RoadMapTiles() {
    }

    /** Start (or restart) sending a player the map round a point. */
    public static void request(ServerPlayer player, int x, int z, int radiusChunks) {
        int r = Mth.clamp(radiusChunks, 1, MAX_RADIUS);
        int cx = x >> 4, cz = z >> 4;
        List<ChunkPos> all = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                all.add(new ChunkPos(cx + dx, cz + dz));
            }
        }
        all.sort(Comparator.comparingInt(p -> Math.max(Math.abs(p.x - cx), Math.abs(p.z - cz))));
        JOBS.put(player.getUUID(), new Job(player.serverLevel(), new ArrayDeque<>(all)));
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
        var server = event.getServer();
        JOBS.entrySet().removeIf(e -> {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Job job = e.getValue();
            if (player == null || player.serverLevel() != job.level) {
                return true;
            }
            for (int n = 0; n < PER_TICK && !job.todo.isEmpty() && job.inFlight < IN_FLIGHT; n++) {
                ChunkPos pos = job.todo.poll();
                LevelChunk loaded = job.level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (loaded != null) {
                    send(player, pos, fromLevel(job.level, loaded));
                    continue;
                }
                job.inFlight++;
                job.level.getChunkSource().chunkMap.read(pos).whenCompleteAsync((tag, err) -> {
                    job.inFlight--;
                    if (err == null && tag.isPresent()) {
                        Tile tile = fromSave(job.level, tag.get());
                        if (tile != null) {
                            send(player, pos, tile);
                        }
                    }
                }, server);
            }
            return job.todo.isEmpty() && job.inFlight == 0;
        });
    }

    /** Send a loaded chunk's map again, after something changed it. */
    public static void resend(ServerPlayer player, ChunkPos pos) {
        LevelChunk chunk = player.serverLevel().getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) {
            send(player, pos, fromLevel(player.serverLevel(), chunk));
        }
    }

    private static void send(ServerPlayer player, ChunkPos pos, Tile tile) {
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RoadMapTileMessage(pos.x, pos.z, tile.colours, tile.ground));
    }

    /** One chunk of map: 16 x 16 colours (as NativeImage wants them) and ground heights, x fastest. */
    public record Tile(int[] colours, short[] ground) {
    }

    private static int shade(MapColor colour, int top, int north) {
        MapColor.Brightness b = top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        return colour == MapColor.NONE ? 0 : colour.calculateRGBColor(b);
    }

    private static Tile fromLevel(ServerLevel level, LevelChunk chunk) {
        int[] colours = new int[256];
        short[] ground = new short[256];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int north = z > 0 ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) : top;
                BlockState s = chunk.getBlockState(p.set(x0 + x, top, z0 + z));
                MapColor c = s.getMapColor(level, p);
                if (c == MapColor.NONE) {
                    c = chunk.getBlockState(p.set(x0 + x, top - 1, z0 + z)).getMapColor(level, p);
                }
                colours[z * 16 + x] = s.getFluidState().isEmpty() ? shade(c, top, north) : shade(c, 0, 0);
                ground[z * 16 + x] = (short) WorldTerrain.scan(level, x0 + x, z0 + z, UNKNOWN);
            }
        }
        return new Tile(colours, ground);
    }

    // ------------------------------------------------------------------ from the save, without loading the chunk

    private record Section(BlockState[] palette, long[] data, int bits) {
        BlockState get(int x, int y, int z) {
            if (data.length == 0) {
                return palette[0];
            }
            int i = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
            int i2 = (int) unpack(data, i, bits);
            return i2 < palette.length ? palette[i2] : Blocks.AIR.defaultBlockState();
        }
    }

    /** One value from a SimpleBitStorage (values never straddle two longs). */
    private static long unpack(long[] data, int index, int bits) {
        int per = 64 / bits;
        int li = index / per;
        if (li >= data.length) {
            return 0;
        }
        return (data[li] >>> ((index % per) * bits)) & ((1L << bits) - 1);
    }

    private static Tile fromSave(ServerLevel level, CompoundTag tag) {
        if (tag.getInt("DataVersion") < 2860) {
            return null;                                    // older than 1.18: a different layout
        }
        // finished chunks have heightmaps; ones saved part-way through generation (round the edge of
        // where anyone has been) may not, and are scanned from the top instead
        CompoundTag maps = tag.getCompound("Heightmaps");
        long[] surface = maps.getLongArray("WORLD_SURFACE");
        long[] solid = maps.getLongArray("MOTION_BLOCKING_NO_LEAVES");
        int minY = level.getMinBuildHeight();
        int bits = Mth.ceillog2(level.getHeight() + 1);
        var blocks = level.holderLookup(Registries.BLOCK);
        Map<Integer, Section> sections = new HashMap<>();
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
            sections.put((int) s.getByte("Y"), new Section(palette, states.getLongArray("data"),
                    Math.max(4, Mth.ceillog2(palette.length))));
        }
        if (sections.isEmpty()) {
            return null;
        }
        int highest = sections.keySet().stream().max(Integer::compare).orElse(0) * 16 + 15;
        int[] colours = new int[256];
        short[] ground = new short[256];
        int[] tops = new int[256];
        for (int i = 0; i < 256; i++) {
            if (surface.length > 0) {
                tops[i] = (int) unpack(surface, i, bits) + minY - 1;
            } else {
                int y = highest;
                while (y > minY && block(sections, i & 15, y, i >> 4).isAir()) {
                    y--;
                }
                tops[i] = y;
            }
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                int top = tops[i];
                BlockState s = block(sections, x, top, z);
                MapColor c = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (c == MapColor.NONE) {
                    c = block(sections, x, top - 1, z).getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                }
                colours[i] = s.getFluidState().isEmpty() ? shade(c, top, z > 0 ? tops[i - 16] : top) : shade(c, 0, 0);
                int y = solid.length > 0 ? (int) unpack(solid, i, bits) + minY - 1 : top;
                while (y > minY) {
                    BlockState g = block(sections, x, y, z);
                    if ((g.is(net.minecraft.tags.BlockTags.SNOW) && !g.canBeReplaced()) || !WorldTerrain.natural(g)) {
                        break;
                    }
                    y--;
                }
                ground[i] = (short) (y + 1);
            }
        }
        return new Tile(colours, ground);
    }

    private static BlockState block(Map<Integer, Section> sections, int x, int y, int z) {
        Section s = sections.get(Math.floorDiv(y, 16));
        return s == null ? Blocks.AIR.defaultBlockState() : s.get(x, y, z);
    }
}
