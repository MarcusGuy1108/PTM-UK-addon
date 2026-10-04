package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.motorway.LaneAspect;
import com.ptmuk.motorway.LaneSignalBlock;
import com.ptmuk.power.PylonBlock;
import com.ptmuk.power.PylonData;
import com.ptmuk.registry.ModBlocks;
import com.ptmuk.registry.ModItems;
import com.ptmuk.sign.DirectionSignBlockEntity;
import com.ptmuk.sign.SignDiagram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkFurnitureGameTests {
    private UkFurnitureGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void pylonBuilderPlacesAndRemovesWholeTower(GameTestHelper helper) {
        BlockPos ground = new BlockPos(2, 0, 2);
        helper.setBlock(ground, Blocks.STONE);
        Player player = helper.makeMockPlayer();
        ItemStack stack = new ItemStack(ModItems.PYLON_BUILDERS.get("wood_pole_11kv").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(ground);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)));
        PylonBlock pole = (PylonBlock) ModBlocks.PYLONS.get("wood_pole_11kv").get();
        PylonData data = PylonData.get("wood_pole_11kv");
        helper.assertTrue(!data.cells.isEmpty(), "pole layout did not load");
        BlockPos origin = abs.above();
        int placed = 0;
        Direction facing = null;
        for (BlockPos cell : data.cells) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState s = helper.getLevel().getBlockState(origin.offset(PylonData.rotate(cell, d)));
                if (s.is(pole) && s.getValue(PylonBlock.FACING) == d) {
                    facing = d;
                }
            }
        }
        helper.assertTrue(facing != null, "no pole cells placed");
        for (BlockPos cell : data.cells) {
            if (helper.getLevel().getBlockState(origin.offset(PylonData.rotate(cell, facing))).is(pole)) {
                placed++;
            }
        }
        helper.assertTrue(placed == data.cells.size(), "placed " + placed + " of " + data.cells.size() + " cells");
        BlockPos top = origin.offset(PylonData.rotate(data.cells.get(data.cells.size() - 1), facing));
        BlockState topState = helper.getLevel().getBlockState(top);
        helper.assertTrue(pole.origin(topState, top).equals(origin), "origin worked out wrongly from the top cell");
        helper.getLevel().destroyBlock(top, false);
        for (BlockPos cell : data.cells) {
            helper.assertTrue(!helper.getLevel().getBlockState(origin.offset(PylonData.rotate(cell, facing))).is(pole),
                    "breaking one cell should remove the whole pole");
        }
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void laneSignalsChangeTogetherAndRedXOnRedstone(GameTestHelper helper) {
        BlockState signal = ModBlocks.MOTORWAY.get("lane_signal").get().defaultBlockState().setValue(LaneSignalBlock.FACING, Direction.NORTH);
        for (int x = 0; x < 3; x++) {
            helper.setBlock(new BlockPos(x, 2, 2), signal);
        }
        Player player = helper.makeMockPlayer();
        BlockPos mid = helper.absolutePos(new BlockPos(1, 2, 2));
        helper.getBlockState(new BlockPos(1, 2, 2)).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(mid), Direction.NORTH, mid, false));
        for (int x = 0; x < 3; x++) {
            helper.assertTrue(helper.getBlockState(new BlockPos(x, 2, 2)).getValue(LaneSignalBlock.ASPECT) == LaneAspect.SPEED_70,
                    "whole row should change together");
        }
        helper.setBlock(new BlockPos(0, 3, 2), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(new BlockPos(0, 2, 2)).getValue(LaneSignalBlock.POWERED),
                "redstone should force the red X"));
    }

    @GameTest(template = "empty5x4x5")
    public static void directionSignKeepsEdits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.DIRECTION_SIGNS.get("direction_sign_primary").get());
        DirectionSignBlockEntity sign = (DirectionSignBlockEntity) helper.getBlockEntity(pos);
        CompoundTag edit = new CompoundTag();
        edit.putString("diagram", "ROUNDABOUT");
        edit.putInt("width", 99);
        edit.putString("ahead", "[A34] Oxford");
        sign.applyEdit(edit);
        CompoundTag saved = sign.saveWithoutMetadata();
        DirectionSignBlockEntity copy = (DirectionSignBlockEntity) helper.getBlockEntity(pos);
        copy.load(saved);
        helper.assertTrue(copy.diagram == SignDiagram.ROUNDABOUT, "diagram not kept");
        helper.assertTrue(copy.width == DirectionSignBlockEntity.MAX_WIDTH, "width should be clamped, got " + copy.width);
        helper.assertTrue(copy.ahead.equals("[A34] Oxford"), "text not kept");
        helper.succeed();
    }
}
