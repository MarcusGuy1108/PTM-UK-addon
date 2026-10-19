package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.home.HomeBlock;
import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.SeatEntity;
import com.ptmuk.home.WideHomeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Home furniture that works: the oven and microwave cook on mains power, the bath sits you in it and fills. */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkHomeGameTests {
    private UkHomeGameTests() {
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 300)
    public static void ovenAndMicrowaveCookWithoutFuel(GameTestHelper helper) {
        BlockPos oven = new BlockPos(1, 1, 1), microwave = new BlockPos(3, 1, 1);
        helper.setBlock(oven, block(HomeFurnitureType.KITCHEN_OVEN_WHITE));
        helper.setBlock(microwave, block(HomeFurnitureType.MICROWAVE));
        for (BlockPos p : new BlockPos[]{oven, microwave}) {
            ((Container) helper.getBlockEntity(p)).setItem(0, new ItemStack(Items.BEEF, 2));
        }
        // the first steak done and the second still cooking, the oven showing it's on
        helper.succeedWhen(() -> {
            for (BlockPos p : new BlockPos[]{oven, microwave}) {
                helper.assertTrue(((Container) helper.getBlockEntity(p)).getItem(2).is(Items.COOKED_BEEF), "nothing cooked at " + p);
                helper.assertBlockProperty(p, HomeBlock.LIT, true);
            }
        });
    }

    @GameTest(template = "empty5x4x5")
    public static void bathSitsYouInItAndFills(GameTestHelper helper) {
        Block bath = block(HomeFurnitureType.BATH);
        BlockPos left = new BlockPos(1, 1, 2);
        BlockState state = bath.defaultBlockState().setValue(HomeBlock.FACING, Direction.NORTH);
        helper.setBlock(left, state.setValue(WideHomeBlock.PART, WideHomeBlock.Part.LEFT));
        helper.setBlock(left.east(), state.setValue(WideHomeBlock.PART, WideHomeBlock.Part.RIGHT));
        BlockPos at = helper.absolutePos(left);
        Player player = helper.makeMockPlayer();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);

        helper.getLevel().getBlockState(at).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(at)).isEmpty(), "didn't sit in the bath");
        helper.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(at)).forEach(e -> e.discard());

        player.setShiftKeyDown(true);
        helper.getLevel().getBlockState(at).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertBlockProperty(left, HomeBlock.LIT, true);
        helper.assertBlockProperty(left.east(), HomeBlock.LIT, true);
        helper.getLevel().getBlockState(at).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertBlockProperty(left, HomeBlock.LIT, false);
        helper.succeed();
    }

    private static Block block(HomeFurnitureType type) {
        return HomeBlocks.BLOCKS.get(type).get();
    }
}
