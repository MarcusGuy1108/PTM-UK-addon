package com.ptmuk.road;

import com.ptmuk.network.BlockSwapListMessage;
import com.ptmuk.network.PtmUkNetwork;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Block swap: what a box of the world is built from, and swapping some of those blocks for others
 * (an old build's coal roads for tarmac, its concrete pavements for paving), with undo. It looks
 * either at the open ground (the surface you walk on outside buildings, and anything thin on it
 * like carpet markings or post bases) or at everything built above the natural ground.
 */
public final class BlockSwap {
    /** The biggest box, in blocks each way. */
    public static final int MAX_SIZE = 256;
    /** The most kinds of block listed. */
    public static final int MAX_KINDS = 64;
    private static final int UNDO_STEPS = 5;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Undo(ResourceKey<Level> dimension, List<RoadBuilder.Saved> blocks) {
    }

    private static final Map<UUID, Deque<Undo>> HISTORY = new HashMap<>();

    private BlockSwap() {
    }

    /** Natural ground: where a build stops going down. */
    static boolean terrain(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.CLAY) || s.is(Blocks.BEDROCK) || !s.getFluidState().isEmpty();
    }

    /** Thin things, posts and trees: looked through to find the ground under them. */
    private static boolean seeThrough(ServerLevel level, BlockPos p, BlockState s) {
        return !s.isCollisionShapeFullBlock(level, p) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS);
    }

    /**
     * Heights in one column: the top block, the natural ground under everything, and the open
     * ground's surface (the first full block under anything thin, or MIN_VALUE if the column is
     * under a roof or a bridge). Null if there's no natural ground at all.
     */
    private static int[] heights(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int min = level.getMinBuildHeight();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int g = top;
        while (g > min && !terrain(level.getBlockState(p.set(x, g, z)))) {
            g--;
        }
        if (g <= min) {
            return null;
        }
        int f = top;
        while (f > g && seeThrough(level, p.set(x, f, z), level.getBlockState(p))) {
            f--;
        }
        if (f - g > 2) {
            // a lamp or a sign on a post: the post goes all the way down to the ground (a roof has a room under it)
            int y = f - 1;
            while (y > g && !level.getBlockState(p.set(x, y, z)).isAir() && seeThrough(level, p, level.getBlockState(p))) {
                y--;
            }
            boolean post = y < f - 1 && y - g <= 2 && !level.getBlockState(p.set(x, y, z)).isAir();
            f = post ? y : Integer.MIN_VALUE;
        }
        return new int[]{top, g, f};
    }

    /** The open ground's surface block in a column (under any carpet or post), or null under a roof. */
    public static BlockState surface(ServerLevel level, int x, int z) {
        int[] h = heights(level, x, z);
        return h == null || h[2] == Integer.MIN_VALUE ? null : level.getBlockState(new BlockPos(x, h[2], z));
    }

    /** The blocks a swap looks at in one column. */
    static void column(ServerLevel level, int x, int z, boolean groundOnly, List<BlockPos> out) {
        int[] h = heights(level, x, z);
        if (h == null) {
            return;
        }
        int top = h[0], g = h[1], f = h[2];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (!groundOnly) {
            for (int y = top; y > g; y--) {
                if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                    out.add(new BlockPos(x, y, z));
                }
            }
            if (top == g) {
                out.add(new BlockPos(x, g, z));                  // bare ground
            }
            return;
        }
        if (f == Integer.MIN_VALUE) {
            return;                                              // a roof or a bridge over it: not open ground
        }
        for (int y = f; y <= Math.min(top, f + 2); y++) {
            if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                out.add(new BlockPos(x, y, z));
            }
        }
    }

    public static List<BlockPos> blocks(ServerLevel level, int x0, int z0, int x1, int z1, boolean groundOnly) {
        List<BlockPos> out = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                level.getChunk(cx, cz);                          // load it, so it's the real world that's read
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                column(level, x, z, groundOnly, out);
            }
        }
        return out;
    }

    /** How many of each block, most first. */
    public static Map<Block, Integer> count(ServerLevel level, List<BlockPos> blocks) {
        Map<Block, Integer> n = new HashMap<>();
        for (BlockPos p : blocks) {
            n.merge(level.getBlockState(p).getBlock(), 1, Integer::sum);
        }
        Map<Block, Integer> sorted = new LinkedHashMap<>();
        n.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(MAX_KINDS)
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** Send the player what's in the box, with a note on what just happened. */
    public static void scan(ServerPlayer player, int x0, int z0, int x1, int z1, boolean groundOnly, String note) {
        ServerLevel level = player.serverLevel();
        Map<Block, Integer> n = count(level, blocks(level, x0, z0, x1, z1, groundOnly));
        List<String> ids = new ArrayList<>();
        int[] counts = new int[n.size()];
        for (Map.Entry<Block, Integer> e : n.entrySet()) {
            counts[ids.size()] = e.getValue();
            ids.add(ForgeRegistries.BLOCKS.getKey(e.getKey()).toString());
        }
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlockSwapListMessage(note, ids, counts));
    }

    /** Swap blocks in the box: each block in swaps for its new one (air takes it away). */
    public static void apply(ServerPlayer player, int x0, int z0, int x1, int z1, boolean groundOnly, Map<Block, Block> swaps) {
        ServerLevel level = player.serverLevel();
        List<RoadBuilder.Saved> saved = swap(level, x0, z0, x1, z1, groundOnly, swaps);
        if (!saved.isEmpty()) {
            Deque<Undo> history = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
            history.push(new Undo(level.dimension(), saved));
            while (history.size() > UNDO_STEPS) {
                history.removeLast();
            }
        }
        refreshMap(player, x0, z0, x1, z1);
        scan(player, x0, z0, x1, z1, groundOnly, "Swapped " + saved.size() + " blocks");
    }

    /** The swap itself: the blocks as they were, to undo it with. */
    public static List<RoadBuilder.Saved> swap(ServerLevel level, int x0, int z0, int x1, int z1, boolean groundOnly, Map<Block, Block> swaps) {
        List<RoadBuilder.Saved> saved = new ArrayList<>();
        for (BlockPos p : blocks(level, x0, z0, x1, z1, groundOnly)) {
            BlockState old = level.getBlockState(p);
            Block to = swaps.get(old.getBlock());
            if (to == null || to == old.getBlock()) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            saved.add(new RoadBuilder.Saved(p, old, be == null ? null : be.saveWithFullMetadata()));
            Clearable.tryClear(be);
            level.setBlock(p, sameWay(old, to.defaultBlockState()), FLAGS);
        }
        // fences, panes and walls join up with what's round them now
        for (RoadBuilder.Saved sv : saved) {
            BlockPos p = sv.pos();
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), FLAGS);
        }
        return saved;
    }

    public static void undo(ServerPlayer player, int x0, int z0, int x1, int z1, boolean groundOnly) {
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            player.displayClientMessage(Component.literal("No swaps to undo"), true);
            scan(player, x0, z0, x1, z1, groundOnly, "No swaps to undo");
            return;
        }
        Undo undo = history.pop();
        ServerLevel level = player.server.getLevel(undo.dimension());
        if (level != null) {
            RoadBuilder.restore(level, undo.blocks());
        }
        int ax = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (RoadBuilder.Saved sv : undo.blocks()) {
            ax = Math.min(ax, sv.pos().getX());
            az = Math.min(az, sv.pos().getZ());
            bx = Math.max(bx, sv.pos().getX());
            bz = Math.max(bz, sv.pos().getZ());
        }
        refreshMap(player, ax, az, bx, bz);
        scan(player, x0, z0, x1, z1, groundOnly, "Put back " + undo.blocks().size() + " blocks");
    }

    private static void refreshMap(ServerPlayer player, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                RoadMapTiles.resend(player, new ChunkPos(cx, cz));
            }
        }
    }

    /** The new block turned, stacked and shaped like the old one where they share a property. */
    static BlockState sameWay(BlockState from, BlockState to) {
        for (Property<?> p : from.getProperties()) {
            to = RoadBuilder.with(to, p.getName(), valueName(from, p));
        }
        return to;
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    /**
     * What an old build's blocks usually stood in for, best guess first: the 1.12 road mods'
     * tarmac, pavements and markings mostly came across as coal, concrete, wool and carpet.
     */
    public static List<String> suggestions(String id) {
        String n = id.substring(id.indexOf(':') + 1);
        if (n.endsWith("_carpet")) {
            return List.of("minecraft:air");                   // old road markings: the Road Planner lays real ones
        }
        if (n.equals("coal_block") || n.startsWith("black_") || n.equals("gray_concrete") || n.equals("gray_wool")
                || n.equals("gray_concrete_powder") || n.equals("blackstone") || n.equals("polished_blackstone")) {
            return List.of("ptmuk:tarmac", "ptmuk:paving_slabs", "ptmuk:block_paving_grey");
        }
        if (n.startsWith("light_gray_") || n.equals("stone") || n.equals("smooth_stone") || n.contains("andesite")
                || n.equals("stone_bricks") || n.equals("cobblestone") || n.equals("gravel")) {
            return List.of("ptmuk:paving_slabs", "ptmuk:block_paving_grey", "ptmuk:tarmac");
        }
        if (n.startsWith("red_") || n.contains("nether_brick")) {
            return List.of("ptmuk:tarmac_red", "ptmuk:block_paving_red", "ptmuk:red_brick");
        }
        if (n.equals("bricks") || n.contains("terracotta") || n.contains("granite")) {
            return List.of("ptmuk:block_paving_red", "ptmuk:red_brick", "ptmuk:london_stock_brick");
        }
        if (n.startsWith("white_") || n.startsWith("yellow_") || n.contains("quartz") || n.equals("snow_block")) {
            return List.of("ptmuk:tarmac", "ptmuk:paving_slabs");
        }
        if (n.endsWith("_slab") && (n.contains("stone") || n.contains("andesite"))) {
            return List.of("ptmuk:concrete_kerb");
        }
        return List.of();
    }
}
