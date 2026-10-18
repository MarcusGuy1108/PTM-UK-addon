package com.ptmuk.town;

import com.ptmuk.PtmUk;
import com.ptmuk.road.RoadBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What the Town Builder does where it's clicked: plant a tree (or grow one that's there a size
 * bigger), scatter bushes or trees round the spot, or run a hedge or a line of street furniture
 * from one click to the next, everything facing the road. The trees it plants are remembered so
 * they can be grown; Undo takes back the last few goes.
 */
public final class TownBuilder {
    public static final String TAG_KIND = "town_kind", TAG_SIZE = "town_size", TAG_SCATTER = "town_scatter",
            TAG_SPACING = "town_spacing", TAG_FROM = "town_from";
    private static final int UNDO_STEPS = 10;
    /** The longest line it lays in one go. */
    private static final int MAX_LINE = 160;

    private record Undo(ResourceKey<Level> dimension, List<RoadBuilder.Saved> blocks, List<Planted> added, List<Planted> removed) {
    }

    private static final Map<UUID, Deque<Undo>> HISTORY = new HashMap<>();

    private final ServerLevel level;
    private final List<RoadBuilder.Saved> saved = new ArrayList<>();
    private final Set<BlockPos> touched = new HashSet<>();
    private final List<Planted> added = new ArrayList<>();
    private final List<Planted> removed = new ArrayList<>();

    private TownBuilder(ServerLevel level) {
        this.level = level;
    }

    // ------------------------------------------------------------------ what's chosen (kept on the item)

    /** "tree:LONDON_PLANE" or "street:BENCH_WOOD". */
    public static String kind(ItemStack stack) {
        String k = stack.getOrCreateTag().getString(TAG_KIND);
        return k.isEmpty() ? "tree:" + TreeShape.Species.LONDON_PLANE.name() : k;
    }

    public static TreeShape.Species species(String kind) {
        try {
            return kind.startsWith("tree:") ? TreeShape.Species.valueOf(kind.substring(5)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static StreetItem street(String kind) {
        try {
            return kind.startsWith("street:") ? StreetItem.valueOf(kind.substring(7)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int size(ItemStack stack) {
        return Math.max(1, Math.min(4, stack.getOrCreateTag().contains(TAG_SIZE) ? stack.getOrCreateTag().getInt(TAG_SIZE) : 3));
    }

    public static int spacing(ItemStack stack) {
        StreetItem s = street(kind(stack));
        int v = stack.getOrCreateTag().getInt(TAG_SPACING);
        return v > 0 ? v : s == null ? 10 : s.spacing;
    }

    /** What the screen chose; a line half drawn is forgotten. */
    public static void choose(ItemStack stack, String kind, int size, boolean scatter, int spacing) {
        if (species(kind) == null && street(kind) == null) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_KIND, kind);
        tag.putInt(TAG_SIZE, Math.max(1, Math.min(4, size)));
        tag.putBoolean(TAG_SCATTER, scatter);
        tag.putInt(TAG_SPACING, Math.max(1, Math.min(64, spacing)));
        tag.remove(TAG_FROM);
    }

    // ------------------------------------------------------------------ the tool

    /** The player clicked `face` of block `hit`. */
    public static void use(ServerPlayer player, ItemStack stack, BlockPos hit, Direction face) {
        player.displayClientMessage(Component.literal(use(player.serverLevel(), player.getUUID(), stack, hit, face, player.getDirection(),
                player.getRandom())), true);
    }

    /** What a click does, and what to tell whoever clicked (`look` is the way they're facing). */
    public static String use(ServerLevel level, UUID who, ItemStack stack, BlockPos hit, Direction face, Direction look, RandomSource rand) {
        TownBuilder b = new TownBuilder(level);
        String kind = kind(stack);
        TreeShape.Species species = species(kind);
        String said;
        if (species != null && species.category != TreeShape.Category.HEDGE) {
            Planted tree = b.find(hit);
            if (tree != null) {
                said = b.grow(tree);
            } else if (stack.getOrCreateTag().getBoolean(TAG_SCATTER)) {
                said = b.scatter(species, size(stack), b.ground(hit, face), rand);
            } else {
                b.plant(species, size(stack), b.ground(hit, face), rand.nextLong());
                said = "Planted a " + TreeShape.SIZES[size(stack) - 1].toLowerCase() + " " + species.title.toLowerCase();
            }
        } else {
            // a line: the first click starts it, the second lays it
            CompoundTag tag = stack.getOrCreateTag();
            BlockPos at = b.ground(hit, face);
            if (!tag.contains(TAG_FROM)) {
                tag.putLong(TAG_FROM, at.asLong());
                return "Now click where it ends (the same spot again for just one)";
            }
            BlockPos from = BlockPos.of(tag.getLong(TAG_FROM));
            tag.remove(TAG_FROM);
            if (from.distManhattan(at) > MAX_LINE) {
                return "That's too long for one go (" + MAX_LINE + " blocks at most)";
            }
            Direction along = from.equals(at) ? look : Math.abs(at.getX() - from.getX()) >= Math.abs(at.getZ() - from.getZ())
                    ? Direction.fromAxisAndDirection(Direction.Axis.X, at.getX() >= from.getX() ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE)
                    : Direction.fromAxisAndDirection(Direction.Axis.Z, at.getZ() >= from.getZ() ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            said = species != null ? b.hedge(species, size(stack), from, at) : b.street(street(kind), spacing(stack), from, at, along);
        }
        if (!b.saved.isEmpty()) {
            Deque<Undo> history = HISTORY.computeIfAbsent(who, k -> new ArrayDeque<>());
            history.push(new Undo(b.level.dimension(), b.saved, b.added, b.removed));
            while (history.size() > UNDO_STEPS) {
                history.removeLast();
            }
        }
        return said;
    }

    public static void undo(ServerPlayer player) {
        player.displayClientMessage(Component.literal(undo(player.server, player.getUUID())), true);
    }

    public static String undo(net.minecraft.server.MinecraftServer server, UUID who) {
        Deque<Undo> history = HISTORY.get(who);
        if (history == null || history.isEmpty()) {
            return "Nothing to undo";
        }
        Undo undo = history.pop();
        ServerLevel level = server.getLevel(undo.dimension());
        if (level != null) {
            RoadBuilder.restore(level, undo.blocks());
            Trees trees = Trees.get(level);
            trees.list.removeAll(undo.added());
            trees.list.addAll(undo.removed());
            trees.setDirty();
        }
        return "Undone";
    }

    // ------------------------------------------------------------------ trees

    /** Where something stands for a click on that face of that block: on top of it, or on the ground in front of it. */
    private BlockPos ground(BlockPos hit, Direction face) {
        BlockPos p = face == Direction.UP ? hit.above() : hit.relative(face);
        for (int i = 0; i < 8 && level.getBlockState(p.below()).canBeReplaced(); i++) {
            p = p.below();
        }
        return p;
    }

    /** The tree that block is part of; the newest if they overlap. */
    private Planted find(BlockPos pos) {
        List<Planted> list = Trees.get(level).list;
        for (int i = list.size() - 1; i >= 0; i--) {
            Planted t = list.get(i);
            BlockPos rel = pos.subtract(t.base());
            if (Math.abs(rel.getX()) <= 20 && Math.abs(rel.getZ()) <= 20 && rel.getY() >= -1 && rel.getY() <= 50
                    && TreeShape.tree(t.species(), t.size(), t.seed()).containsKey(rel)) {
                return t;
            }
        }
        return null;
    }

    private void plant(TreeShape.Species species, int size, BlockPos base, long seed) {
        TreeShape.tree(species, size, seed).forEach((rel, state) -> {
            BlockPos p = base.offset(rel);
            BlockState now = level.getBlockState(p);
            // never into a wall or the ground; leaves give way to a trunk
            if (now.canBeReplaced() || (now.getBlock() instanceof LeavesBlock && !(state.getBlock() instanceof LeavesBlock))) {
                set(p, state);
            }
        });
        Planted t = new Planted(species, size, seed, base);
        Trees trees = Trees.get(level);
        trees.list.add(t);
        trees.setDirty();
        added.add(t);
    }

    private String grow(Planted t) {
        if (t.size() >= 4) {
            return "That " + t.species().title.toLowerCase() + " is as big as it grows";
        }
        TreeShape.tree(t.species(), t.size(), t.seed()).forEach((rel, state) -> {
            BlockPos p = t.base().offset(rel);
            if (level.getBlockState(p).getBlock() == state.getBlock()) {
                set(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        });
        Trees trees = Trees.get(level);
        trees.list.remove(t);
        removed.add(t);
        plant(t.species(), t.size() + 1, t.base(), t.seed());
        return "Grew the " + t.species().title.toLowerCase() + " to " + TreeShape.SIZES[t.size()].toLowerCase();
    }

    /** A few of them round the spot, on open ground, some smaller, not on top of each other. */
    private String scatter(TreeShape.Species species, int size, BlockPos centre, RandomSource rand) {
        int count = species.category == TreeShape.Category.BUSH ? 6 : 4 + size;
        double radius = Math.max(4, species.radius(size) * 2.5);
        List<BlockPos> done = new ArrayList<>();
        for (int tries = 0; tries < count * 8 && done.size() < count; tries++) {
            double a = rand.nextDouble() * Math.PI * 2, d = Math.sqrt(rand.nextDouble()) * radius;
            int x = (int) Math.floor(centre.getX() + 0.5 + Math.cos(a) * d), z = (int) Math.floor(centre.getZ() + 0.5 + Math.sin(a) * d);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            int sz = Math.max(1, size - rand.nextInt(2));
            boolean crowded = done.stream().anyMatch(q -> q.distSqr(p) < Math.pow(species.radius(sz) * 1.3 + 1, 2));
            if (Math.abs(y - centre.getY()) > 6 || crowded || !level.getBlockState(p.below()).is(BlockTags.DIRT)) {
                continue;
            }
            plant(species, sz, p, rand.nextLong());
            done.add(p);
        }
        return done.isEmpty() ? "Nowhere to plant there: it scatters onto grass and earth" : "Planted " + done.size() + " " + species.title.toLowerCase()
                + (species.category == TreeShape.Category.BUSH ? " bushes" : " trees");
    }

    // ------------------------------------------------------------------ lines

    /** Every block on the line from a to b, without diagonal gaps (so a hedge or rail is unbroken). */
    private static List<int[]> cells(BlockPos a, BlockPos b) {
        List<int[]> out = new ArrayList<>();
        int x = a.getX(), z = a.getZ(), dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        int sx = Integer.signum(dx), sz = Integer.signum(dz);
        out.add(new int[]{x, z});
        while (x != b.getX() || z != b.getZ()) {
            // step whichever way keeps closest to the true line
            long ex = Math.abs((long) (x + sx - a.getX()) * dz - (long) (z - a.getZ()) * dx);
            long ez = Math.abs((long) (x - a.getX()) * dz - (long) (z + sz - a.getZ()) * dx);
            if (x != b.getX() && (z == b.getZ() || ex <= ez)) {
                x += sx;
            } else {
                z += sz;
            }
            out.add(new int[]{x, z});
        }
        return out;
    }

    /** Where to stand at (x, z), near height y: an open space with something solid under it. */
    private int stand(int x, int z, int y) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (level.getBlockState(p).canBeReplaced() && !level.getBlockState(p.below()).canBeReplaced()) {
                return y + dy;
            }
        }
        return y;
    }

    private String hedge(TreeShape.Species species, int size, BlockPos a, BlockPos b) {
        BlockState leaf = species.leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        List<int[]> line = cells(a, b);
        for (int i = 0; i < line.size(); i++) {
            int[] c = line.get(i);
            int y = stand(c[0], c[1], a.getY() + (b.getY() - a.getY()) * i / Math.max(1, line.size() - 1));
            for (int k = 0; k < species.height(size); k++) {
                BlockPos p = new BlockPos(c[0], y + k, c[1]);
                if (level.getBlockState(p).canBeReplaced()) {
                    set(p, leaf);
                }
            }
        }
        return "Planted " + line.size() + " blocks of " + species.title.toLowerCase();
    }

    private String street(StreetItem item, int spacing, BlockPos a, BlockPos b, Direction along) {
        Block block = ForgeRegistries.BLOCKS.getValue(PtmUk.id(item.block));
        Block post = item.post == null ? null : ForgeRegistries.BLOCKS.getValue(PtmUk.id(item.post));
        Direction road = roadSide(a, b, along);
        List<int[]> line = cells(a, b);
        int n = 0;
        for (int i = 0; i < line.size(); i++) {
            if (item.postHeight >= 0 && i % spacing != 0) {
                continue;
            }
            int[] c = line.get(i);
            int y = stand(c[0], c[1], a.getY() + (b.getY() - a.getY()) * i / Math.max(1, line.size() - 1));
            BlockPos p = new BlockPos(c[0], y, c[1]);
            int tall = post == null ? 1 : item.postHeight + 1;
            boolean room = true;
            for (int k = 0; k < tall; k++) {
                room &= level.getBlockState(p.above(k)).canBeReplaced();
            }
            if (!room) {
                continue;
            }
            for (int k = 0; post != null && k < item.postHeight; k++) {
                set(p.above(k), with(with(post.defaultBlockState(), "base", k == 0), "cap", false));
            }
            BlockState s = block.defaultBlockState();
            set(p.above(tall - 1), s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.setValue(HorizontalDirectionalBlock.FACING, road) : s);
            n++;
        }
        // rails join up
        for (BlockPos p : touched) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), Block.UPDATE_CLIENTS);
        }
        return n == 0 ? "No room there" : item.postHeight < 0 ? "Put up " + n + " blocks of " + item.title.toLowerCase()
                : "Put " + n + " " + item.title.toLowerCase() + (n == 1 ? "" : "s") + " along there, facing the road";
    }

    private static BlockState with(BlockState s, String property, boolean value) {
        return s.getBlock().getStateDefinition().getProperty(property) instanceof BooleanProperty p ? s.setValue(p, value) : s;
    }

    /**
     * Which side of the line the road is: the nearer side with tarmac within ten blocks of the middle;
     * else the nearer side that drops a kerb's height; else away from a wall; else to the right.
     */
    private Direction roadSide(BlockPos a, BlockPos b, Direction along) {
        Direction right = along.getClockWise();
        int mx = (a.getX() + b.getX()) / 2, mz = (a.getZ() + b.getZ()) / 2;
        int y = stand(mx, mz, (a.getY() + b.getY()) / 2);
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 1; k <= 10; k++) {
                for (Direction d : new Direction[]{right, right.getOpposite()}) {
                    int x = mx + d.getStepX() * k, z = mz + d.getStepZ() * k;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x, top, z)).getBlock()));
                    if (pass == 0 ? id.contains("tarmac") || id.contains("asphalt") || id.contains("road") : top < y - 1) {
                        return d;
                    }
                }
            }
        }
        for (int k = 1; k <= 4; k++) {
            if (!level.getBlockState(new BlockPos(mx, y + 1, mz).relative(right, k)).canBeReplaced()) {
                return right.getOpposite();
            }
            if (!level.getBlockState(new BlockPos(mx, y + 1, mz).relative(right.getOpposite(), k)).canBeReplaced()) {
                return right;
            }
        }
        return right;
    }

    private void set(BlockPos pos, BlockState state) {
        if (touched.add(pos.immutable())) {
            saved.add(new RoadBuilder.Saved(pos.immutable(), level.getBlockState(pos), null));
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ the trees it has planted

    public record Planted(TreeShape.Species species, int size, long seed, BlockPos base) {
    }

    /** The trees and bushes planted in a world, so they can be grown. */
    public static final class Trees extends SavedData {
        final List<Planted> list = new ArrayList<>();

        static Trees get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Trees::load, Trees::new, "ptmuk_trees");
        }

        static Trees load(CompoundTag tag) {
            Trees t = new Trees();
            for (Tag e : tag.getList("trees", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                TreeShape.Species s = species("tree:" + c.getString("species"));
                if (s != null) {
                    t.list.add(new Planted(s, c.getInt("size"), c.getLong("seed"), BlockPos.of(c.getLong("base"))));
                }
            }
            return t;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag l = new ListTag();
            for (Planted p : list) {
                CompoundTag c = new CompoundTag();
                c.putString("species", p.species().name());
                c.putInt("size", p.size());
                c.putLong("seed", p.seed());
                c.putLong("base", p.base().asLong());
                l.add(c);
            }
            tag.put("trees", l);
            return tag;
        }
    }
}
