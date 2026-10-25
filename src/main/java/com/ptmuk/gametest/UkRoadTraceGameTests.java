package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.RoadTrace;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.Terrain;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
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
        RoadTrace.Result side = RoadTrace.trace(level, x0 + 60, z0 + 40, 0);
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

        RoadTrace.Result main = RoadTrace.trace(level, x0 + 20, z0 + 2, 0);
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

    /**
     * A patchy old road: grey concrete with specks of andesite and concrete powder, a full-block
     * white centre line, stone brick pavements, and halfway along a step up a slope with a row of
     * slabs. Clicked on the line or on a speck it's followed end to end at its full width; asked for
     * a piece, it goes about that far each way and says the road carries on at both ends.
     */
    @GameTest(template = "empty5x4x5", timeoutTicks = 200)
    public static void followsPatchyRoadsAndPieces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        int x0 = o.getX() + 2400, y = o.getY(), z0 = o.getZ() + 2400;
        net.minecraft.world.level.block.Block[] patch = {Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE,
                Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.ANDESITE, Blocks.ANDESITE, Blocks.ANDESITE, Blocks.GRAY_CONCRETE_POWDER};
        int speckX = 0, speckZ = 0;
        for (int x = x0 - 3; x <= x0 + 142; x++) {
            for (int z = z0 - 10; z <= z0 + 10; z++) {
                int dz = Math.abs(z - z0), up = x >= x0 + 70 ? 1 : 0;
                boolean road = x >= x0 && x <= x0 + 139 && dz <= 4, pave = x >= x0 && x <= x0 + 139 && dz <= 7;
                net.minecraft.world.level.block.Block top = !pave ? Blocks.GRASS_BLOCK : !road ? Blocks.STONE_BRICKS
                        : z == z0 && (x - x0) % 6 < 3 ? Blocks.WHITE_CONCRETE : patch[Math.floorMod(x * 7 + z * 13 + x * z, 10)];
                if (road && top == Blocks.ANDESITE && x > x0 + 90 && speckX == 0) {
                    speckX = x;
                    speckZ = z;
                }
                level.getChunk(x >> 4, z >> 4);
                for (int k = -1; k <= 7; k++) {
                    net.minecraft.world.level.block.state.BlockState b = k < up ? Blocks.DIRT.defaultBlockState() : k == up ? top.defaultBlockState()
                            : k == up + 1 && x == x0 + 69 && pave ? (road ? Blocks.ANDESITE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(x, y + k, z), b, 2);
                }
            }
        }
        for (int[] at : new int[][]{{x0 + 30, z0}, {speckX, speckZ}}) {
            RoadTrace.Result r = RoadTrace.trace(level, at[0], at[1], 0);
            String from = "from " + (at[0] - x0) + "," + (at[1] - z0);
            helper.assertTrue(r != null && r.x().length > 10, "the patchy road wasn't followed " + from);
            int n = r.x().length;
            double lo = Math.min(r.x()[0], r.x()[n - 1]), hi = Math.max(r.x()[0], r.x()[n - 1]);
            helper.assertTrue(lo < x0 + 6 && hi > x0 + 133, "the patchy road should be followed end to end " + from + ", got " + (lo - x0) + ".." + (hi - x0));
            helper.assertTrue(Math.abs(r.width()[n / 2] - 9) <= 1, "the patchy road is 9 wide, measured " + r.width()[n / 2] + " " + from);
            helper.assertTrue(!r.openStart() && !r.openEnd(), "the whole road ends at both ends " + from);
            helper.assertTrue(r.pavement() == 3, "the pavements are 3 wide, measured " + r.pavement() + " " + from);
            for (int i = 0; i < n; i++) {
                helper.assertTrue(Math.abs(r.z()[i] - (z0 + 0.5)) < 2, "the line wandered to z " + (r.z()[i] - z0) + " " + from);
            }
        }
        RoadTrace.Result piece = RoadTrace.trace(level, x0 + 40, z0 + 1, 20);
        helper.assertTrue(piece != null && piece.x().length >= 2, "no piece of road followed");
        int m = piece.x().length;
        double lo = Math.min(piece.x()[0], piece.x()[m - 1]), hi = Math.max(piece.x()[0], piece.x()[m - 1]);
        helper.assertTrue(lo > x0 + 14 && lo < x0 + 24 && hi > x0 + 56 && hi < x0 + 66, "a piece 20 each way from 40 went " + (lo - x0) + ".." + (hi - x0));
        helper.assertTrue(piece.openStart() && piece.openEnd(), "the road carries on past both ends of a piece");
        helper.succeed();
    }

    /**
     * A road built to an open end (an old road carries on there) stops square: no kerb, pavement
     * or sloped ground round the end, while its other end, a real dead end, has its kerb round it.
     */
    @GameTest(template = "empty5x4x5")
    public static void openEndLeavesTheOldRoadBeyond(GameTestHelper helper) {
        RoadPlan plan = new RoadPlan();
        RoadPlan.Node a = plan.addNode(0, 0), b = plan.addNode(60, 0);
        plan.addSegment(a.id, b.id, RoadStyle.residential());
        b.open = true;
        Layout l = Layout.of(plan, Terrain.flat(64));
        for (int x = 62; x <= 75; x++) {
            for (int z = -14; z <= 14; z++) {
                int idx = l.index(x, z);
                helper.assertTrue(idx < 0 || l.kind[idx] == Layout.NONE, "kind " + l.kind[idx] + " past the open end at " + x + "," + z);
            }
        }
        boolean kerb = false;
        for (int x = -4; x <= -1; x++) {
            int idx = l.index(x, 0);
            kerb |= idx >= 0 && l.kind[idx] == Layout.KERB;
        }
        helper.assertTrue(kerb, "the dead end should still have its kerb round it");
        helper.succeed();
    }
}
