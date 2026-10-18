package com.ptmuk.home;

import com.ptmuk.road.RoadBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.Tags;

/**
 * The Interior Designer: furnishes an empty room in a shop or house with the mod's furniture.
 * The room is found from the floor that was clicked, out to its walls and doors (it has to be
 * indoors and no bigger than 40 x 40). Doorways are kept clear, tall things stay out of windows,
 * and the layout follows the kind of room: shelves round the walls and gondolas in rows for a
 * shop, a counter and tables for a cafe, desks for an office, a sofa and TV for a living room
 * and so on. Furniture already in the room is replaced; Undo puts the room back.
 */
public final class RoomDesigner {
    public enum Room {
        CONVENIENCE("Convenience store"), NEWSAGENT("Newsagent"), PHARMACY("Pharmacy"), GAMES("Game shop"), CLOTHES("Clothes shop"),
        CAFE("Cafe"), RESTAURANT("Restaurant"), TAKEAWAY("Takeaway"), PUB("Pub or bar"), OFFICE("Office"),
        LIVING("Living room"), BEDROOM("Bedroom"), KITCHEN("Kitchen"), BATHROOM("Bathroom");

        public final String title;

        Room(String title) {
            this.title = title;
        }
    }

    private static final int MAX_CELLS = 1600;
    private static final int MAX_SPAN = 40;
    private static final int UNDO_STEPS = 5;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private record Undo(ResourceKey<Level> dimension, List<RoadBuilder.Saved> blocks) {
    }

    private static final Map<UUID, Deque<Undo>> HISTORY = new HashMap<>();

    private final ServerLevel level;
    private final int y;
    /** The room's floor cells (x, z at foot level y), in the order they were found. */
    private final Set<Long> room = new LinkedHashSet<>();
    private final Set<Long> taken = new HashSet<>();
    private final Set<Long> clear = new HashSet<>();
    /** Doorway sides: key(x * 8 + side, z). */
    private final Set<Long> doors = new HashSet<>();
    private final List<RoadBuilder.Saved> saved = new ArrayList<>();
    private final Set<BlockPos> touched = new HashSet<>();
    private Direction front = Direction.SOUTH;
    private int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
    private int placed;

    private RoomDesigner(ServerLevel level, int y) {
        this.level = level;
        this.y = y;
    }

    // ------------------------------------------------------------------ the room

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int kz(long k) {
        return (int) k;
    }

    private BlockPos at(long k, int dy) {
        return new BlockPos(kx(k), y + dy, kz(k));
    }

    /** Somewhere to stand: nothing in the way at feet and head height (furniture counts as nothing), a floor under it. */
    private boolean walkable(int x, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return clearOrFurniture(level.getBlockState(feet)) && clearOrFurniture(level.getBlockState(feet.above()))
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private boolean roofed(int x, int z) {
        for (int dy = 2; dy < 12; dy++) {
            if (!level.getBlockState(new BlockPos(x, y + dy, z)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Part of the room: walkable and indoors (so an open doorway onto the street ends the room). */
    private boolean open(int x, int z) {
        return walkable(x, z) && roofed(x, z);
    }

    private static boolean clearOrFurniture(BlockState s) {
        return (s.canBeReplaced() && s.getFluidState().isEmpty()) || furniture(s);
    }

    /** What a redesign takes out: the mod's home furniture (tables too) and beds. */
    private static boolean furniture(BlockState s) {
        return s.getBlock() instanceof HomeBlock || s.getBlock() instanceof HomeTableBlock || s.getBlock() instanceof BedBlock;
    }

    private static boolean door(BlockState s) {
        return s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock;
    }

    /** Finds the room; "" if it can be furnished, else why not. */
    private String find(int sx, int sz) {
        if (!open(sx, sz)) {
            return walkable(sx, sz) ? "That's outdoors: the Interior Designer works inside a building" : "Click the floor of the room";
        }
        Deque<Long> todo = new ArrayDeque<>();
        todo.add(key(sx, sz));
        room.add(key(sx, sz));
        while (!todo.isEmpty()) {
            long k = todo.poll();
            for (Direction d : SIDES) {
                long n = step(k, d, 1);
                if (room.contains(n) || !open(kx(n), kz(n))) {
                    continue;
                }
                room.add(n);
                if (room.size() > MAX_CELLS) {
                    return "That room's bigger than " + MAX_SPAN + " x " + MAX_SPAN + ", or isn't closed off";
                }
                todo.add(n);
            }
        }
        for (long k : room) {
            x0 = Math.min(x0, kx(k));
            x1 = Math.max(x1, kx(k));
            z0 = Math.min(z0, kz(k));
            z1 = Math.max(z1, kz(k));
        }
        if (x1 - x0 >= MAX_SPAN || z1 - z0 >= MAX_SPAN) {
            return "That room's bigger than " + MAX_SPAN + " x " + MAX_SPAN + ", or isn't closed off";
        }
        if (room.size() < 4) {
            return "That room's too small to furnish";
        }
        // doorways (doors, or a gap onto somewhere walkable): the cells inside stay clear; the main door, onto the street, is the front
        int best = -1;
        for (long k : room) {
            for (Direction d : SIDES) {
                long out = step(k, d, 1);
                boolean isDoor = door(level.getBlockState(at(k, 0).relative(d)));
                if (room.contains(out) || !(isDoor || walkable(kx(out), kz(out)))) {
                    continue;
                }
                doors.add(key(kx(k) * 8 + d.ordinal(), kz(k)));
                clear.add(k);
                clear.add(step(k, d, -1));
                clear.add(step(k, d.getClockWise(), 1));
                clear.add(step(k, d.getCounterClockWise(), 1));
                long beyond = step(k, d, isDoor ? 2 : 1);
                int score = (roofed(kx(beyond), kz(beyond)) ? 0 : 1000) + (d.getAxis() == Direction.Axis.X ? z1 - z0 : x1 - x0);
                if (score > best) {
                    best = score;
                    front = d;
                }
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ what's where

    private boolean free(long k) {
        return room.contains(k) && !taken.contains(k) && !clear.contains(k);
    }

    private static long step(long k, Direction d, int n) {
        return key(kx(k) + d.getStepX() * n, kz(k) + d.getStepZ() * n);
    }

    /** The walls round a cell (not doorways, not more room). */
    private List<Direction> walls(long k) {
        List<Direction> out = new ArrayList<>();
        for (Direction d : SIDES) {
            if (!room.contains(step(k, d, 1)) && !doors.contains(key(kx(k) * 8 + d.ordinal(), kz(k)))) {
                out.add(d);
            }
        }
        return out;
    }

    private boolean window(long k, Direction wall) {
        for (int dy = 0; dy <= 1; dy++) {
            BlockState s = level.getBlockState(at(k, dy).relative(wall));
            if (s.getBlock() instanceof IronBarsBlock || s.is(Tags.Blocks.GLASS) || s.is(Tags.Blocks.GLASS_PANES)) {
                return true;
            }
        }
        return false;
    }

    /** How far a cell is from the nearest wall (or doorway), in steps: 1 is against it. */
    private int depth(long k) {
        for (int n = 1; n < MAX_SPAN; n++) {
            for (Direction d : SIDES) {
                if (!room.contains(step(k, d, n))) {
                    return n;
                }
            }
        }
        return MAX_SPAN;
    }

    /** Within r steps of any of the cells. */
    private static boolean near(long k, Set<Long> cells, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (cells.contains(key(kx(k) + dx, kz(k) + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Along a direction: larger further that way. */
    private static int along(long k, Direction d) {
        return kx(k) * d.getStepX() + kz(k) * d.getStepZ();
    }

    /** Free cells n steps out from the wall on side `wall` (1 is against it), in order along it. */
    private List<Long> line(Direction wall, int n) {
        List<Long> out = new ArrayList<>();
        for (long k : room) {
            if (free(k) && room.contains(step(k, wall, n - 1)) && !room.contains(step(k, wall, n)) && walls(step(k, wall, n - 1)).contains(wall)) {
                out.add(k);
            }
        }
        out.sort(Comparator.comparingInt(k -> along(k, wall.getClockWise())));
        return out;
    }

    // ------------------------------------------------------------------ putting things down

    private void set(BlockPos pos, BlockState state) {
        if (touched.add(pos.immutable())) {
            BlockEntity be = level.getBlockEntity(pos);
            saved.add(new RoadBuilder.Saved(pos.immutable(), level.getBlockState(pos), be == null ? null : be.saveWithFullMetadata()));
            Clearable.tryClear(be);
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static BlockState of(HomeFurnitureType t, Direction facing) {
        BlockState s = HomeBlocks.BLOCKS.get(t).get().defaultBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.setValue(HorizontalDirectionalBlock.FACING, facing) : s;      // tables have none
    }

    /** One piece of furniture facing `facing`; tall ones get their top half, wide ones their right half. */
    private boolean put(long k, HomeFurnitureType t, Direction facing) {
        if (!free(k)) {
            return false;
        }
        BlockState s = of(t, facing);
        if (t.kind == HomeFurnitureType.Kind.WIDE) {
            long right = step(k, facing.getClockWise(), 1);
            if (!free(right)) {
                return false;
            }
            set(at(right, 0), s.setValue(WideHomeBlock.PART, WideHomeBlock.Part.RIGHT));
            taken.add(right);
            s = s.setValue(WideHomeBlock.PART, WideHomeBlock.Part.LEFT);
        }
        if (t.kind == HomeFurnitureType.Kind.TALL) {
            set(at(k, 0), s.setValue(TallHomeBlock.HALF, DoubleBlockHalf.LOWER));
            set(at(k, 1), s.setValue(TallHomeBlock.HALF, DoubleBlockHalf.UPPER));
        } else {
            set(at(k, 0), s);
        }
        taken.add(k);
        placed++;
        return true;
    }

    /** Something small on top of the counter, table or unit at k (it sets its own height from what it's on). */
    private void onTop(long k, HomeFurnitureType t, Direction facing) {
        if (level.getBlockState(at(k, 0)).getBlock() instanceof HomeBlock && level.getBlockState(at(k, 1)).isAir()) {
            set(at(k, 1), of(t, facing));
            placed++;
        }
    }

    /** Up to max pieces round the walls, facing into the room, cycling through the list; tall ones keep out of windows. */
    private void alongWalls(List<HomeFurnitureType> things, int max, LongPredicate where) {
        int i = 0;
        for (long k : room) {
            if (i >= max || !free(k) || walls(k).isEmpty() || !where.test(k)) {
                continue;
            }
            Direction wall = walls(k).get(0);
            HomeFurnitureType t = things.get(i % things.size());
            if (!(t.kind == HomeFurnitureType.Kind.TALL && window(k, wall)) && put(k, t, wall.getOpposite())) {
                i++;
            }
        }
    }

    /** A radiator under each window. */
    private void radiators(HomeFurnitureType radiator) {
        for (long k : room) {
            if (free(k) && !walls(k).isEmpty() && window(k, walls(k).get(0))) {
                put(k, radiator, walls(k).get(0).getOpposite());
            }
        }
    }

    /**
     * A counter `depth` steps out from a wall, with the space behind it kept for the staff and a way in at the end;
     * along a side wall it starts at the front, along the back wall it's in the middle. Stools in front if there's a stool.
     */
    private List<Long> counter(Direction wall, int depth, List<HomeFurnitureType> units, HomeFurnitureType stool) {
        List<Long> line = line(wall, depth);
        if (wall.getAxis() != front.getAxis()) {
            line.sort(Comparator.comparingInt(k -> -along(k, front)));
        }
        int n = Math.min(units.size(), line.size() - 1);
        int start = wall == front.getOpposite() ? Math.max(0, (line.size() - n) / 2) : 0;
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long k = line.get(start + i);
            if (put(k, units.get(i), wall.getOpposite())) {
                out.add(k);
                if (stool == null || !put(step(k, wall, -1), stool, wall)) {
                    clear.add(step(k, wall, -1));
                }
            }
        }
        for (int i = Math.max(0, start - 1); i <= Math.min(line.size() - 1, start + n); i++) {
            for (int b = 0; b < depth; b++) {
                if (i < start || i >= start + n || b > 0) {
                    clear.add(step(line.get(i), wall, b));
                }
            }
        }
        return out;
    }

    /** Tables in a grid across the middle, each with chairs on the free sides (two, beside it, or all four). */
    private void tables(int spacing, int chairs, HomeFurnitureType table, HomeFurnitureType chair) {
        for (long k : room) {
            if (!free(k) || depth(k) < 2 || Math.floorMod(kx(k) - x0, spacing) != 1 || Math.floorMod(kz(k) - z0, spacing) != 1
                    || near(k, clear, 1)) {
                continue;
            }
            put(k, table, front);
            for (Direction d : chairs == 2 ? new Direction[]{front.getClockWise(), front.getCounterClockWise()} : SIDES) {
                put(step(k, d, 1), chair, d.getOpposite());
            }
        }
    }

    /** Back-to-back shelving in rows across the middle, aisles between, and a gap every few to cut through. */
    private void gondolas(List<HomeFurnitureType> things) {
        Direction side = front.getClockWise();
        int i = 0;
        for (long k : room) {
            int row = Math.floorMod(along(k, front), 4);
            if (!free(k) || depth(k) < 2 || near(k, clear, 1) || (row != 0 && row != 3) || Math.floorMod(along(k, side), 7) == 0) {
                continue;
            }
            put(k, things.get(i++ % things.size()), row == 0 ? front : front.getOpposite());
        }
    }

    /** A plant in each free corner. */
    private void plants() {
        for (long k : room) {
            if (free(k) && walls(k).size() >= 2) {
                put(k, HomeFurnitureType.HOUSEPLANT, front);
            }
        }
    }

    /** The wall with the longest run of free cells against it, not the front. */
    private Direction longestWall() {
        Direction best = front.getOpposite();
        int most = -1;
        for (Direction wall : SIDES) {
            int n = line(wall, 1).size();
            if (wall != front && n > most) {
                most = n;
                best = wall;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ rooms

    private void design(Room type) {
        Direction back = front.getOpposite();
        Direction side = front.getClockWise();
        HomeFurnitureType unit = HomeFurnitureType.KITCHEN_UNIT_WHITE;
        switch (type) {
            case CONVENIENCE, NEWSAGENT, PHARMACY, GAMES -> {
                List<HomeFurnitureType> shelves = switch (type) {
                    case NEWSAGENT -> List.of(HomeFurnitureType.SHELVES_NEWSAGENT, HomeFurnitureType.SHELVES_SNACKS, HomeFurnitureType.SHELVES_DRINKS);
                    case PHARMACY -> List.of(HomeFurnitureType.SHELVES_PHARMACY);
                    case GAMES -> List.of(HomeFurnitureType.SHELVES_GAMES);
                    default -> List.of(HomeFurnitureType.SHELVES_GROCERIES, HomeFurnitureType.SHELVES_SNACKS,
                            HomeFurnitureType.SHELVES_HOUSEHOLD, HomeFurnitureType.SHELVES_DRINKS);
                };
                List<Long> till = counter(side, 2, List.of(unit, unit, unit), null);
                if (!till.isEmpty()) {
                    onTop(till.get(till.size() / 2), HomeFurnitureType.TILL, side.getOpposite());
                }
                if (type == Room.CONVENIENCE) {
                    alongWalls(List.of(HomeFurnitureType.CHILLER_CABINET, HomeFurnitureType.CHILLER_CABINET, HomeFurnitureType.UPRIGHT_FREEZER),
                            Integer.MAX_VALUE, k -> walls(k).contains(back));
                }
                alongWalls(shelves, Integer.MAX_VALUE, k -> true);
                gondolas(shelves);
            }
            case CLOTHES -> {
                List<Long> till = counter(back, 2, List.of(unit, unit, unit), null);
                if (!till.isEmpty()) {
                    onTop(till.get(1 % till.size()), HomeFurnitureType.TILL, front);
                }
                alongWalls(List.of(HomeFurnitureType.CLOTHES_RAIL), Integer.MAX_VALUE, k -> walls(k).size() == 1);
                for (long k : room) {
                    if (free(k) && depth(k) >= 3 && !near(k, clear, 1) && Math.floorMod(kx(k) - x0, 3) == 1 && Math.floorMod(kz(k) - z0, 3) == 1) {
                        put(k, HomeFurnitureType.CLOTHES_RAIL, side);
                    }
                }
                plants();
            }
            case CAFE, RESTAURANT -> {
                List<Long> c = counter(back, 2, type == Room.CAFE
                        ? List.of(unit, unit, HomeFurnitureType.CAKE_DISPLAY, HomeFurnitureType.CAKE_DISPLAY, unit)
                        : List.of(unit, unit, unit), null);
                if (!c.isEmpty()) {
                    onTop(c.get(c.size() - 1), HomeFurnitureType.TILL, front);
                    if (type == Room.CAFE) {
                        onTop(c.get(0), HomeFurnitureType.COFFEE_MACHINE, front);
                    }
                }
                tables(type == Room.CAFE ? 3 : 4, type == Room.CAFE ? 2 : 4, HomeFurnitureType.DINING_TABLE, HomeFurnitureType.DINING_CHAIR);
                plants();
            }
            case TAKEAWAY -> {
                alongWalls(List.of(HomeFurnitureType.FRYER, unit, HomeFurnitureType.FRYER, HomeFurnitureType.PIZZA_OVEN), Integer.MAX_VALUE,
                        k -> walls(k).contains(back));
                List<Long> c = counter(back, 3, List.of(unit, unit, unit, unit, unit, unit, unit, unit), null);
                if (!c.isEmpty()) {
                    onTop(c.get(c.size() / 2), HomeFurnitureType.TILL, front);
                }
                alongWalls(List.of(HomeFurnitureType.BAR_STOOL), Integer.MAX_VALUE, k -> !walls(k).contains(back) && !walls(k).contains(front));
            }
            case PUB -> {
                alongWalls(List.of(HomeFurnitureType.BACK_BAR), Integer.MAX_VALUE, k -> walls(k).contains(back));
                HomeFurnitureType oak = HomeFurnitureType.KITCHEN_UNIT_OAK;
                List<Long> bar = counter(back, 3, List.of(oak, oak, oak, oak, oak, oak, oak, oak), HomeFurnitureType.BAR_STOOL);
                for (int i = 1; i < bar.size(); i += 3) {
                    onTop(bar.get(i), HomeFurnitureType.BEER_PUMPS, front);
                }
                alongWalls(List.of(HomeFurnitureType.FRUIT_MACHINE), 2, k -> walls(k).contains(side));
                tables(4, 4, HomeFurnitureType.DINING_TABLE, HomeFurnitureType.DINING_CHAIR);
            }
            case OFFICE -> {
                alongWalls(List.of(HomeFurnitureType.BOOKCASE), Integer.MAX_VALUE, k -> walls(k).contains(back) && walls(k).size() == 1);
                for (long k : room) {
                    if (free(k) && depth(k) >= 2 && !near(k, clear, 1) && Math.floorMod(along(k, front), 3) == 0
                            && free(step(k, front, 1)) && put(k, HomeFurnitureType.DESK, front)) {
                        onTop(k, HomeFurnitureType.COMPUTER, front);
                        put(step(k, front, 1), HomeFurnitureType.OFFICE_CHAIR, back);
                    }
                }
                plants();
            }
            case LIVING -> {
                Direction w = longestWall();
                List<Long> wall = line(w, 1);
                if (!wall.isEmpty()) {
                    int mid = wall.size() / 2;
                    for (int i = Math.max(0, mid - 1); i <= Math.min(wall.size() - 1, mid + 1); i++) {
                        put(wall.get(i), HomeFurnitureType.SOFA_GREY, w.getOpposite());
                    }
                    long sofa = wall.get(mid);
                    put(step(sofa, w, -2), HomeFurnitureType.COFFEE_TABLE, w.getOpposite());
                    // the TV against the far wall, facing the sofa
                    for (int n = 3; n < MAX_SPAN && room.contains(step(sofa, w, -n)); n++) {
                        long k = step(sofa, w, -n);
                        if (!room.contains(step(k, w, -1))) {
                            if (put(k, HomeFurnitureType.TV_UNIT, w)) {
                                onTop(k, HomeFurnitureType.TV, w);
                            }
                        }
                    }
                    if (mid + 2 < wall.size()) {
                        put(wall.get(mid + 2), HomeFurnitureType.FLOOR_LAMP, w.getOpposite());
                    }
                }
                radiators(HomeFurnitureType.RADIATOR);
                alongWalls(List.of(HomeFurnitureType.BOOKCASE), 2, k -> walls(k).size() == 1 && !near(k, taken, 1));
                plants();
            }
            case BEDROOM -> {
                Direction w = longestWall();
                List<Long> wall = line(w, 1);
                if (!wall.isEmpty()) {
                    long head = wall.get(wall.size() / 2), foot = step(head, w, -1);
                    if (free(head) && free(foot)) {
                        set(at(head, 0), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, w).setValue(BedBlock.PART, BedPart.HEAD));
                        set(at(foot, 0), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, w).setValue(BedBlock.PART, BedPart.FOOT));
                        taken.add(head);
                        taken.add(foot);
                        placed++;
                        put(step(head, w.getClockWise(), 1), HomeFurnitureType.BEDSIDE_TABLE_WHITE, w.getOpposite());
                        put(step(head, w.getCounterClockWise(), 1), HomeFurnitureType.BEDSIDE_TABLE_WHITE, w.getOpposite());
                    }
                }
                radiators(HomeFurnitureType.RADIATOR);
                alongWalls(List.of(HomeFurnitureType.WARDROBE_WHITE, HomeFurnitureType.CHEST_OF_DRAWERS_WHITE), 3,
                        k -> !walls(k).contains(w) && !near(k, taken, 1));
                plants();
            }
            case KITCHEN -> {
                alongWalls(List.of(HomeFurnitureType.FRIDGE_FREEZER), 1, k -> walls(k).size() >= 2);
                radiators(HomeFurnitureType.KITCHEN_SINK_WHITE);
                alongWalls(List.of(unit, HomeFurnitureType.KITCHEN_DRAWERS_WHITE, unit, HomeFurnitureType.KITCHEN_OVEN_WHITE, unit,
                        HomeFurnitureType.WASHING_MACHINE, unit), Integer.MAX_VALUE, k -> true);
                HomeFurnitureType[] tops = {HomeFurnitureType.KETTLE, HomeFurnitureType.MICROWAVE, HomeFurnitureType.TOASTER};
                int n = 0;
                for (long k : room) {
                    BlockState s = level.getBlockState(at(k, 0));
                    if (n < tops.length && s.getBlock() == HomeBlocks.BLOCKS.get(unit).get()) {
                        onTop(k, tops[n++], s.getValue(HorizontalDirectionalBlock.FACING));
                    }
                }
                tables(4, 4, HomeFurnitureType.DINING_TABLE, HomeFurnitureType.DINING_CHAIR);
            }
            case BATHROOM -> {
                Direction w = longestWall();
                for (long k : line(w, 1)) {
                    if (put(k, HomeFurnitureType.BATH, w.getOpposite())) {
                        break;
                    }
                }
                alongWalls(List.of(HomeFurnitureType.TOILET, HomeFurnitureType.BASIN, HomeFurnitureType.TOWEL_RADIATOR), 3, k -> true);
            }
        }
    }

    // ------------------------------------------------------------------ the tool

    /** Furnish the room whose floor was clicked (pos is the floor block). */
    public static void design(ServerPlayer player, BlockPos floor, Room type) {
        ServerLevel level = player.serverLevel();
        RoomDesigner d = new RoomDesigner(level, floor.getY() + 1);
        String why = d.furnish(floor.getX(), floor.getZ(), type);
        if (!why.isEmpty()) {
            player.displayClientMessage(Component.literal(why), true);
            return;
        }
        Deque<Undo> history = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        history.push(new Undo(level.dimension(), d.saved));
        while (history.size() > UNDO_STEPS) {
            history.removeLast();
        }
        player.displayClientMessage(Component.literal("Furnished the " + type.title.toLowerCase() + ": " + d.placed + " pieces"), true);
    }

    /** For tests: furnish without a player; the blocks it changed (to put back), or null if there's no room there. */
    public static List<RoadBuilder.Saved> designAt(ServerLevel level, BlockPos floor, Room type) {
        RoomDesigner d = new RoomDesigner(level, floor.getY() + 1);
        return d.furnish(floor.getX(), floor.getZ(), type).isEmpty() ? d.saved : null;
    }

    /** "" if it furnished the room, else why it couldn't. */
    private String furnish(int x, int z, Room type) {
        String why = find(x, z);
        if (!why.isEmpty()) {
            return why;
        }
        empty();
        design(type);
        // tables join up, sofas turn their corners, things on counters sit at the right height
        for (BlockPos p : touched) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), Block.UPDATE_CLIENTS);
        }
        return "";
    }

    /** Takes out the room's furniture first (beds and the mod's furniture), so it can be designed again. */
    private void empty() {
        for (long k : room) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockState s = level.getBlockState(at(k, dy));
                if (furniture(s)) {
                    set(at(k, dy), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    public static void undo(ServerPlayer player) {
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            player.displayClientMessage(Component.literal("No rooms to undo"), true);
            return;
        }
        Undo undo = history.pop();
        ServerLevel level = player.server.getLevel(undo.dimension());
        if (level != null) {
            RoadBuilder.restore(level, undo.blocks());
        }
        player.displayClientMessage(Component.literal("Put the room back as it was"), true);
    }
}
