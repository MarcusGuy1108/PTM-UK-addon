package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.registry.ModItems;
import com.ptmuk.town.TownBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Town Builder well away from the other tests (its grass would be their ground), in its own batch: a lime tree planted, grown a size and put back with undo;
 * a row of benches along a pavement facing the road a step down beside it; a hedge from one click to the next.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkTownGameTests {
    private static final int Y = 10;
    /** Off to one side of all the tests. */
    private static final int Z = -400;

    private UkTownGameTests() {
    }

    private static int topLog(GameTestHelper helper, int x, int z) {
        int top = -1;
        for (int y = Y + 1; y < Y + 60; y++) {
            if (helper.getBlockState(new BlockPos(x, y, Z + z)).is(Blocks.OAK_LOG)) {
                top = y;
            }
        }
        return top;
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 200, batch = "town")
    public static void plantsGrowsAndLaysOutAStreet(GameTestHelper helper) {
        for (int x = -12; x <= 30; x++) {
            for (int z = -12; z <= 14; z++) {
                for (int y = 0; y < 60; y++) {
                    helper.setBlock(x, Y + y, Z + z, y == 0 ? Blocks.GRASS_BLOCK : Blocks.AIR);
                }
            }
        }
        UUID who = UUID.randomUUID();
        RandomSource rand = RandomSource.create(7);
        ItemStack stack = new ItemStack(ModItems.TOWN_BUILDER.get());

        // a tree, grown a size, then undone
        TownBuilder.choose(stack, "tree:LIME", 2, false, 0);
        TownBuilder.use(helper.getLevel(), who, stack, helper.absolutePos(new BlockPos(0, Y, Z)), Direction.UP, Direction.EAST, rand);
        int young = topLog(helper, 0, 0);
        helper.assertTrue(young > Y + 4, "the lime should have a trunk, top log at " + young);
        TownBuilder.use(helper.getLevel(), who, stack, helper.absolutePos(new BlockPos(0, Y + 2, Z)), Direction.NORTH, Direction.EAST, rand);
        int grown = topLog(helper, 0, 0);
        helper.assertTrue(grown > young + 4, "growing should make it taller: " + young + " then " + grown);
        TownBuilder.undo(helper.getLevel().getServer(), who);
        helper.assertTrue(topLog(helper, 0, 0) == young, "undo should put the smaller tree back");
        TownBuilder.undo(helper.getLevel().getServer(), who);
        helper.assertBlockPresent(Blocks.AIR, 0, Y + 1, Z);

        // benches along a pavement (z = 10), the road a step down at z = 12
        Block tarmac = ForgeRegistries.BLOCKS.getValue(PtmUk.id("tarmac"));
        for (int x = 0; x <= 20; x++) {
            helper.setBlock(x, Y + 1, Z + 10, Blocks.SMOOTH_STONE);
            helper.setBlock(x, Y + 1, Z + 11, Blocks.SMOOTH_STONE);
            helper.setBlock(x, Y, Z + 12, tarmac);
            helper.setBlock(x, Y, Z + 13, tarmac);
        }
        TownBuilder.choose(stack, "street:BENCH_WOOD", 0, false, 5);
        TownBuilder.use(helper.getLevel(), who, stack, helper.absolutePos(new BlockPos(0, Y + 1, Z + 10)), Direction.UP, Direction.EAST, rand);
        TownBuilder.use(helper.getLevel(), who, stack, helper.absolutePos(new BlockPos(20, Y + 1, Z + 10)), Direction.UP, Direction.EAST, rand);
        Block bench = ForgeRegistries.BLOCKS.getValue(PtmUk.id("bench_wood"));
        for (int x = 0; x <= 20; x++) {
            if (x % 5 == 0) {
                helper.assertBlockPresent(bench, x, Y + 2, Z + 10);
                helper.assertBlockProperty(new BlockPos(x, Y + 2, Z + 10), HorizontalDirectionalBlock.FACING, Direction.SOUTH);
            } else {
                helper.assertBlockPresent(Blocks.AIR, x, Y + 2, Z + 10);
            }
        }

        // a garden hedge, two high
        TownBuilder.choose(stack, "tree:PRIVET_HEDGE", 2, false, 0);
        TownBuilder.use(helper.getLevel(), who, stack, helper.absolutePos(new BlockPos(-10, Y, Z - 10)), Direction.UP, Direction.EAST, rand);
        TownBuilder.use(helper.getLevel(), who, stack, helper.absolutePos(new BlockPos(-10, Y, Z - 1)), Direction.UP, Direction.EAST, rand);
        for (int z = -10; z <= -1; z++) {
            helper.assertBlockPresent(Blocks.OAK_LEAVES, -10, Y + 1, Z + z);
            helper.assertBlockPresent(Blocks.OAK_LEAVES, -10, Y + 2, Z + z);
            helper.assertBlockPresent(Blocks.AIR, -10, Y + 3, Z + z);
        }
        helper.succeed();
    }
}
