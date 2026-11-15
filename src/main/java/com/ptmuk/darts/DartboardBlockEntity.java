package com.ptmuk.darts;

import com.ptmuk.PtmUk;
import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A dartboard that keeps score: 501 each, three darts a visit, finishing on a double (or the
 * bull); going under (or to 1, or to 0 off a single) is bust, back to where the visit began.
 * A Darts Caller (the microphone) near the board calls each visit, "Game on", "Bust" and "Game
 * shot"; everyone close sees each dart and the totals.
 */
public class DartboardBlockEntity extends BlockEntity {
    /** The numbers round the board, clockwise from the top. */
    public static final int[] ORDER = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    /** The double ring's outer edge from the middle (blocks); the rings as fractions of it (tools/shop_textures.py draws the same). */
    public static final double DOUBLE_EDGE = 0.78 * 7.5 / 16, BULL = 0.06, OUTER_BULL = 0.14, TREBLE_IN = 0.55, TREBLE_OUT = 0.64, DOUBLE_IN = 0.90;
    private static final double FACE = 15.18 / 16;

    private static final class Thrower {
        String name;
        int left = 501, visitFrom = 501, darts, visit;
        boolean started;
        final List<String> hits = new ArrayList<>();
    }

    private final Map<UUID, Thrower> throwers = new LinkedHashMap<>();
    private String pending;
    private int pendingIn;
    private BlockPos caller;

    public DartboardBlockEntity(BlockPos pos, BlockState state) {
        super(DartsRegistry.BOARD_BE.get(), pos, state);
    }

    /**
     * Where a dart at this point scores: {points, ring} where ring is 0 off the scoring area,
     * 1 single, 2 double, 3 treble, 25 outer bull, 50 bull.
     */
    public static int[] score(Direction facing, BlockPos pos, Vec3 at) {
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 rel = at.subtract(Vec3.atCenterOf(pos).subtract(normal.scale(FACE - 0.5)));
        Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal());
        double u = rel.dot(right) / DOUBLE_EDGE, v = rel.y / DOUBLE_EDGE, r = Math.hypot(u, v);
        if (r > 1) {
            return new int[]{0, 0};
        }
        if (r < BULL) {
            return new int[]{50, 50};
        }
        if (r < OUTER_BULL) {
            return new int[]{25, 25};
        }
        double angle = Math.toDegrees(Math.atan2(u, v));    // clockwise from the top
        int number = ORDER[(int) (((angle + 9) % 360 + 360) % 360 / 18) % 20];
        int ring = r >= DOUBLE_IN ? 2 : r >= TREBLE_IN && r <= TREBLE_OUT ? 3 : 1;
        return new int[]{number * ring, ring};
    }

    private static String label(int[] s) {
        return switch (s[1]) {
            case 0 -> "Off the board";
            case 50 -> "Bullseye!";
            case 25 -> "Outer bull";
            case 2 -> "Double " + s[0] / 2;
            case 3 -> "Treble " + s[0] / 3;
            default -> String.valueOf(s[0]);
        };
    }

    /** A dart's landed in the board. */
    public void hit(Player who, Vec3 at, DartEntity dart) {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        int[] s = score(facing, worldPosition, at);
        Thrower t = throwers.computeIfAbsent(who.getUUID(), k -> new Thrower());
        t.name = who.getName().getString();
        if (t.darts == 0) {
            t.visitFrom = t.left;
            t.visit = 0;
            t.hits.clear();
            if (!t.started) {
                t.started = true;
                call("game_on", 1);
            }
        }
        t.darts++;
        t.visit += s[0];
        t.hits.add(label(s));
        int left = t.left - s[0];
        boolean finishing = s[1] == 2 || s[1] == 50;
        if (left < 0 || left == 1 || left == 0 && !finishing) {
            t.left = t.visitFrom;
            tellAll(Component.literal(t.name + ": " + label(s) + "... BUST! Back to " + t.left).withStyle(ChatFormatting.RED), false);
            call("bust", 10);
            endVisit(who, t);
        } else if (left == 0) {
            tellAll(Component.literal(t.name + " checks out on " + label(s) + "! GAME SHOT (" + t.visit + " to finish)").withStyle(ChatFormatting.GOLD), false);
            call("game_shot", 12);
            t.left = 501;
            t.started = false;
            endVisit(who, t);
        } else {
            t.left = left;
            tellAll(Component.literal(t.name + ": " + label(s) + (s[1] >= 1 ? " (" + s[0] + ")" : "") + "   " + t.left + " left").withStyle(ChatFormatting.AQUA), true);
            if (t.darts >= 3) {
                tellAll(Component.literal(t.name + " scored " + t.visit + " (" + String.join(", ", t.hits) + ")   " + t.left + " left"
                        + (t.left <= 170 ? ", you require " + t.left : "")).withStyle(ChatFormatting.WHITE), false);
                call("score_" + Math.min(180, t.visit), 14);
                endVisit(who, t);
            }
        }
        setChanged();
    }

    private void endVisit(Player who, Thrower t) {
        t.darts = 0;
        for (DartEntity d : level.getEntitiesOfClass(DartEntity.class, new AABB(worldPosition).inflate(1.5))) {   // its darts come out
            if (d.getOwner() == who) {
                d.collect(50);
            }
        }
    }

    /** Where this player is. */
    public void tell(Player who) {
        Thrower t = throwers.get(who.getUUID());
        who.displayClientMessage(Component.literal(t == null ? "501 to go. Throw your darts (hold, then let go)."
                : t.left + " to go" + (t.darts > 0 ? ", " + (3 - t.darts) + " darts left this visit" : "")
                + (t.left <= 170 ? ". You require " + t.left : "")).withStyle(ChatFormatting.AQUA), true);
    }

    /** What this player has left (501 before they've thrown). */
    public int left(Player who) {
        Thrower t = throwers.get(who.getUUID());
        return t == null ? 501 : t.left;
    }

    public void newGame(Player who) {
        throwers.remove(who.getUUID());
        who.displayClientMessage(Component.literal("New game: 501, finish on a double").withStyle(ChatFormatting.AQUA), true);
        setChanged();
    }

    private void tellAll(Component text, boolean bar) {
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(12))) {
            if (bar) {
                p.displayClientMessage(text, true);
            } else {
                p.sendSystemMessage(text);
            }
        }
    }

    /** The caller says it, a beat later (if there's a caller near the board). */
    private void call(String line, int delay) {
        pending = line;
        pendingIn = delay;
    }

    public void tick() {
        if (pending != null && --pendingIn <= 0) {
            BlockPos at = callerPos();
            if (at != null) {
                level.playSound(null, at, SoundEvent.createVariableRangeEvent(PtmUk.id("darts." + pending)), SoundSource.VOICE, 2.5F, 1.0F);
            }
            pending = null;
        }
    }

    /** The nearest Darts Caller within 16 blocks (remembered while it's there). */
    private BlockPos callerPos() {
        var block = HomeBlocks.BLOCKS.get(HomeFurnitureType.DARTS_CALLER).get();
        if (caller != null && level.getBlockState(caller).is(block)) {
            return caller;
        }
        caller = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-16, -6, -16), worldPosition.offset(16, 6, 16))) {
            if (level.getBlockState(p).is(block) && p.distSqr(worldPosition) < best) {
                best = p.distSqr(worldPosition);
                caller = p.immutable();
            }
        }
        return caller;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        throwers.forEach((id, t) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", id);
            c.putString("name", t.name == null ? "" : t.name);
            c.putInt("left", t.left);
            c.putInt("from", t.visitFrom);
            c.putInt("darts", t.darts);
            c.putInt("visit", t.visit);
            c.putBoolean("started", t.started);
            list.add(c);
        });
        tag.put("throwers", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        throwers.clear();
        for (Tag x : tag.getList("throwers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            Thrower t = new Thrower();
            t.name = c.getString("name");
            t.left = c.getInt("left");
            t.visitFrom = c.getInt("from");
            t.darts = c.getInt("darts");
            t.visit = c.getInt("visit");
            t.started = c.getBoolean("started");
            throwers.put(c.getUUID("id"), t);
        }
    }
}
