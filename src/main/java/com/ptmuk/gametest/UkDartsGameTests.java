package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.darts.DartboardBlockEntity;
import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Darts: where on the board scores what, and a game of 501 (a 180, a bust, a checkout on a double). */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkDartsGameTests {
    private static final double D = DartboardBlockEntity.DOUBLE_EDGE;

    private UkDartsGameTests() {
    }

    /** A point on a north-facing board at block 0,0,0: u to the thrower's right (west), v up, in double-ring radii. */
    private static Vec3 at(BlockPos pos, double u, double v) {
        return new Vec3(pos.getX() + 0.5 - u * D, pos.getY() + 0.5 + v * D, pos.getZ() + 15.18 / 16);
    }

    private static int points(BlockPos pos, double u, double v) {
        return DartboardBlockEntity.score(Direction.NORTH, pos, at(pos, u, v))[0];
    }

    @GameTest(template = "empty5x4x5")
    public static void dartboardScores(GameTestHelper helper) {
        BlockPos o = BlockPos.ZERO;
        helper.assertTrue(points(o, 0, 0) == 50, "the bull is 50");
        helper.assertTrue(points(o, 0, 0.1) == 25, "the outer bull is 25");
        helper.assertTrue(points(o, 0, 0.6) == 60, "treble 20 is 60, got " + points(o, 0, 0.6));
        helper.assertTrue(points(o, 0, 0.3) == 20, "single 20");
        helper.assertTrue(points(o, 0.95, 0) == 12, "double 6 (to the right) is 12, got " + points(o, 0.95, 0));
        helper.assertTrue(points(o, -0.3, 0) == 11, "single 11 (to the left), got " + points(o, -0.3, 0));
        helper.assertTrue(points(o, 0, -0.6) == 9, "treble 3 (at the bottom) is 9, got " + points(o, 0, -0.6));
        helper.assertTrue(points(o, 0, 1.1) == 0, "off the board scores nothing");
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void aGameOf501(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel, HomeBlocks.BLOCKS.get(HomeFurnitureType.DARTBOARD).get().defaultBlockState());
        DartboardBlockEntity board = (DartboardBlockEntity) helper.getBlockEntity(rel);
        BlockPos pos = helper.absolutePos(rel);
        ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "darts-test"));
        Vec3 t20 = at(pos, 0, 0.6), t19 = at(pos, -0.185, -0.571), d12 = at(pos, -0.55, 0.78);
        helper.assertTrue(DartboardBlockEntity.score(Direction.NORTH, pos, t19)[0] == 57, "aimed at treble 19: "
                + DartboardBlockEntity.score(Direction.NORTH, pos, t19)[0]);
        helper.assertTrue(DartboardBlockEntity.score(Direction.NORTH, pos, d12)[0] == 24, "aimed at double 12: "
                + DartboardBlockEntity.score(Direction.NORTH, pos, d12)[0]);
        for (int i = 0; i < 3; i++) {
            board.hit(p, t20, null);
        }
        helper.assertTrue(board.left(p) == 321, "one hundred and eighty: 321 left, has " + board.left(p));
        for (int i = 0; i < 3; i++) {
            board.hit(p, t20, null);
        }
        helper.assertTrue(board.left(p) == 141, "another 180: 141 left");
        board.hit(p, t20, null);
        board.hit(p, t20, null);
        board.hit(p, t20, null);                            // 21 left, then 60: bust
        helper.assertTrue(board.left(p) == 141, "bust: back to 141, has " + board.left(p));
        board.hit(p, t20, null);
        board.hit(p, t19, null);
        board.hit(p, d12, null);                            // 60, 57, double 12: game shot
        helper.assertTrue(board.left(p) == 501, "game shot: a new leg from 501, has " + board.left(p));
        helper.succeed();
    }
}
