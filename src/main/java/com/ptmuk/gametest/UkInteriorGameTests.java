package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.home.HomeBlock;
import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.RoomDesigner;
import com.ptmuk.road.RoadBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Interior Designer in a 7 x 6 room with a door onto the street, a window, and a plant
 * and a table already in it: every kind of room gets furnished with the doorway left clear, and undo puts the room back.
 * Built high above the test so it doesn't run into the others.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkInteriorGameTests {
    private static final int Y = 60;

    private UkInteriorGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void furnishesEveryKindOfRoomAndUndoes(GameTestHelper helper) {
        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 10; z++) {
                for (int y = 0; y <= 5; y++) {
                    boolean wall = x == 0 || x == 8 || z == 0 || z == 7;
                    boolean inside = x >= 0 && x <= 8 && z >= 0 && z <= 7;
                    helper.setBlock(x, Y + y, z, y == 0 && (inside || x == 4) || inside && (y == 4 || wall && y < 4) ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        helper.setBlock(4, Y + 1, 7, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
        helper.setBlock(4, Y + 2, 7, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(0, Y + 2, 3, Blocks.GLASS_PANE);
        helper.setBlock(1, Y + 1, 1, HomeBlocks.BLOCKS.get(HomeFurnitureType.HOUSEPLANT).get());
        helper.setBlock(3, Y + 1, 4, HomeBlocks.BLOCKS.get(HomeFurnitureType.DINING_TABLE).get());

        helper.assertTrue(RoomDesigner.designAt(helper.getLevel(), helper.absolutePos(new BlockPos(4, Y, 9)), RoomDesigner.Room.CAFE) == null,
                "the street outside isn't a room");
        for (RoomDesigner.Room room : RoomDesigner.Room.values()) {
            List<RoadBuilder.Saved> saved = RoomDesigner.designAt(helper.getLevel(), helper.absolutePos(new BlockPos(3, Y, 3)), room);
            helper.assertTrue(saved != null, room.title + ": didn't find the room");
            BlockPos table = helper.absolutePos(new BlockPos(3, Y + 1, 4));
            helper.assertTrue(saved.stream().anyMatch(s -> s.pos().equals(table)), room.title + ": the table already there wasn't taken out");
            int pieces = 0;
            for (int x = 1; x <= 7; x++) {
                for (int z = 1; z <= 6; z++) {
                    if (helper.getBlockState(new BlockPos(x, Y + 1, z)).getBlock() instanceof HomeBlock) {
                        pieces++;
                    }
                }
            }
            helper.assertTrue(pieces >= 4, room.title + ": only " + pieces + " pieces of furniture");
            helper.assertBlockPresent(Blocks.AIR, 4, Y + 1, 6);
            helper.assertBlockPresent(Blocks.AIR, 4, Y + 1, 5);
            RoadBuilder.restore(helper.getLevel(), saved);
            helper.assertBlockPresent(HomeBlocks.BLOCKS.get(HomeFurnitureType.HOUSEPLANT).get(), 1, Y + 1, 1);
            helper.assertBlockPresent(HomeBlocks.BLOCKS.get(HomeFurnitureType.DINING_TABLE).get(), 3, Y + 1, 4);
            helper.assertBlockPresent(Blocks.AIR, 3, Y + 1, 3);
        }
        helper.succeed();
    }
}
