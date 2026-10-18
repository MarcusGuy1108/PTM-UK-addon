package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.RoadTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Following an old road: a T-junction of coal roads laid into the ground well away from the test,
 * an 11-wide main road with a carpet centre line and 4-wide concrete pavements, and a 7-wide side
 * road. Followed from either,
 * the line runs down the middle to the ends, with a junction where they meet.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkRoadTraceGameTests {
    private UkRoadTraceGameTests() {
    }

    /** One column of old road (or pavement) at a fixed height, whatever an earlier run left there. */
    private static void column(ServerLevel level, int x, int y, int z, net.minecraft.world.level.block.Block top, boolean line) {
        level.getChunk(x >> 4, z >> 4);
        level.setBlock(new BlockPos(x, y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, y, z), top.defaultBlockState(), 2);
        for (int k = 1; k <= 6; k++) {
            level.setBlock(new BlockPos(x, y + k, z), (k == 1 && line ? Blocks.WHITE_CARPET : Blocks.AIR).defaultBlockState(), 2);
        }
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 200)
    public static void followsOldRoadsToTheJunction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        int x0 = o.getX() + 2000, y = o.getY(), z0 = o.getZ() + 2000;
        for (int x = x0 - 2; x <= x0 + 122; x++) {
            for (int z = z0 - 11; z <= z0 + 64; z++) {
                boolean main = x >= x0 && x <= x0 + 120 && Math.abs(z - z0) <= 9;
                boolean side = z > z0 + 5 && z <= z0 + 60 && Math.abs(x - (x0 + 60)) <= 3;
                column(level, x, y, z, side || main && Math.abs(z - z0) <= 5 ? Blocks.COAL_BLOCK
                        : main ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRASS_BLOCK, main && z == z0 && x % 6 < 3);   // 4-wide pavements
            }
        }
        RoadTrace.Result side = RoadTrace.trace(level, x0 + 60, z0 + 40);
        helper.assertTrue(side != null && side.x().length > 5, "the side road wasn't followed");
        int n = side.x().length;
        boolean endsAtJunction = side.junction()[0] || side.junction()[n - 1];
        helper.assertTrue(endsAtJunction, "the side road should end in the junction");
        int j = side.junction()[0] ? 0 : n - 1;
        helper.assertTrue(Math.abs(side.x()[j] - (x0 + 60.5)) < 1.5 && Math.abs(side.z()[j] - (z0 + 0.5)) < 2,
                "the side road's junction should be in the middle of the main road, was " + (side.x()[j] - x0) + "," + (side.z()[j] - z0));
        double farZ = Math.max(side.z()[0], side.z()[n - 1]);
        helper.assertTrue(Math.abs(farZ - (z0 + 60)) < 5, "the side road should reach its end, got z " + farZ);
        helper.assertTrue(Math.abs(side.x()[n / 2] - (x0 + 60.5)) < 1.5, "off the middle of the side road: x " + side.x()[n / 2]);
        helper.assertTrue(Math.abs(side.width()[n / 2] - 7) <= 1, "the side road is 7 wide, measured " + side.width()[n / 2]);

        RoadTrace.Result main = RoadTrace.trace(level, x0 + 20, z0 + 2);
        helper.assertTrue(main != null && main.x().length > 10, "the main road wasn't followed");
        int m = main.x().length;
        double lo = Math.min(main.x()[0], main.x()[m - 1]), hi = Math.max(main.x()[0], main.x()[m - 1]);
        helper.assertTrue(lo < x0 + 5 && hi > x0 + 115, "the main road should be followed end to end, got " + lo + ".." + hi);
        boolean junction = false;
        for (int i = 0; i < m; i++) {
            junction |= main.junction()[i] && Math.abs(main.x()[i] - (x0 + 60.5)) < 1.5;
            helper.assertTrue(Math.abs(main.z()[i] - (z0 + 0.5)) < 2, "the main road's line wandered to z " + main.z()[i]);
        }
        helper.assertTrue(junction, "no junction found where the side road joins");
        helper.assertTrue(Math.abs(main.width()[2] - 11) <= 1, "the main road is 11 wide, measured " + main.width()[2]);
        helper.assertTrue(main.pavement() == 4, "the pavements are 4 wide, measured " + main.pavement());
        helper.succeed();
    }
}
