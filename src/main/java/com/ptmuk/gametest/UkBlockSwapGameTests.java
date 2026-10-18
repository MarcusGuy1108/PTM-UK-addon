package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.BlockSwap;
import com.ptmuk.road.RoadBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Block swap on an old build: a coal road with a carpet line beside a little coal building and a
 * step. Open ground swaps the road and the step (turned the same way) but not the building; undo
 * puts it all back.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkBlockSwapGameTests {
    private UkBlockSwapGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void swapsOpenGroundAndUndoes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block tarmac = ForgeRegistries.BLOCKS.getValue(PtmUk.id("tarmac"));
        for (int x = 0; x < 5; x++) {
            helper.setBlock(x, 0, 0, Blocks.DIRT);
            helper.setBlock(x, 1, 0, Blocks.COAL_BLOCK);
            helper.setBlock(x, 2, 0, Blocks.WHITE_CARPET);
            helper.setBlock(x, 0, 2, Blocks.DIRT);
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(x, y, 2, Blocks.COAL_BLOCK);
            }
        }
        helper.setBlock(0, 0, 4, Blocks.DIRT);
        helper.setBlock(0, 1, 4, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        BlockPos a = helper.absolutePos(BlockPos.ZERO), b = helper.absolutePos(new BlockPos(4, 0, 4));
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX()), z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());

        Map<Block, Integer> ground = BlockSwap.count(level, BlockSwap.blocks(level, x0, z0, x1, z1, true));
        helper.assertTrue(ground.getOrDefault(Blocks.COAL_BLOCK, 0) == 5, "open ground should have the 5 road blocks, had " + ground.get(Blocks.COAL_BLOCK));
        helper.assertTrue(ground.getOrDefault(Blocks.WHITE_CARPET, 0) == 5, "the carpet line should count, had " + ground.get(Blocks.WHITE_CARPET));
        Map<Block, Integer> all = BlockSwap.count(level, BlockSwap.blocks(level, x0, z0, x1, z1, false));
        helper.assertTrue(all.getOrDefault(Blocks.COAL_BLOCK, 0) == 20, "everything should take in the building too, had " + all.get(Blocks.COAL_BLOCK));

        List<RoadBuilder.Saved> saved = BlockSwap.swap(level, x0, z0, x1, z1, true,
                Map.of(Blocks.COAL_BLOCK, tarmac, Blocks.WHITE_CARPET, Blocks.AIR, Blocks.OAK_STAIRS, Blocks.STONE_STAIRS));
        for (int x = 0; x < 5; x++) {
            helper.assertBlockPresent(tarmac, x, 1, 0);
            helper.assertBlockPresent(Blocks.AIR, x, 2, 0);
            helper.assertBlockPresent(Blocks.COAL_BLOCK, x, 3, 2);
            helper.assertBlockPresent(Blocks.COAL_BLOCK, x, 1, 2);
        }
        helper.assertBlockProperty(new BlockPos(0, 1, 4), StairBlock.FACING, Direction.EAST);
        helper.assertBlockPresent(Blocks.STONE_STAIRS, 0, 1, 4);

        RoadBuilder.restore(level, saved);
        helper.assertBlockPresent(Blocks.COAL_BLOCK, 2, 1, 0);
        helper.assertBlockPresent(Blocks.WHITE_CARPET, 2, 2, 0);
        helper.assertBlockPresent(Blocks.OAK_STAIRS, 0, 1, 4);
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void suggestsUkBlocks(GameTestHelper helper) {
        for (String id : List.of("minecraft:coal_block", "minecraft:light_gray_concrete", "minecraft:red_concrete", "minecraft:white_carpet",
                "minecraft:bricks", "minecraft:smooth_stone_slab")) {
            List<String> s = BlockSwap.suggestions(id);
            helper.assertTrue(!s.isEmpty(), "no suggestion for " + id);
            for (String to : s) {
                helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(new net.minecraft.resources.ResourceLocation(to)), id + " suggests a missing block " + to);
            }
        }
        helper.assertTrue(BlockSwap.suggestions("minecraft:coal_block").get(0).equals("ptmuk:tarmac"), "coal should become tarmac first");
        helper.succeed();
    }
}
