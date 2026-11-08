package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.building.Sections;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Section Mover: a stone block, a stair facing north and a chest with a diamond in it, copied
 * to a section and pasted turned round beside them (the stair faces south, the diamond's still in
 * the chest), and undone (the ground is as it was).
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkSectionGameTests {
    private UkSectionGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void copiesPastesTurnedAndUndoes(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        UUID who = UUID.randomUUID();
        helper.setBlock(1, 1, 0, Blocks.STONE);              // (clear of the test's structure block at 0,-1,0)
        helper.setBlock(2, 1, 0, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        helper.setBlock(3, 1, 0, Blocks.CHEST);
        ((ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 0)))).setItem(0, new ItemStack(Items.DIAMOND));
        String name = "gametest-" + who.toString().substring(0, 8);
        helper.assertTrue(Sections.copy(level, who, name, helper.absolutePos(new BlockPos(1, 1, 0)), helper.absolutePos(new BlockPos(3, 1, 1))) == null,
                "copying had to wait");
        Sections.runAll();
        Sections.Info s = Sections.info(name);
        helper.assertTrue(s != null && s.sx() == 3 && s.sz() == 2 && s.ay() == helper.absolutePos(new BlockPos(1, 1, 0)).getY() - s.origin().getY()
                && s.ay() <= Sections.BELOW, "the section: " + s);
        // pasted turned round with its first corner (the stone) at 3,1,4: the rest runs back towards x 1, z 3
        String said = Sections.paste(level, who, s, helper.absolutePos(new BlockPos(3, 1, 4)), Rotation.CLOCKWISE_180, Mirror.NONE, false);
        helper.assertTrue(said == null, said);
        Sections.runAll();
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 4));
        helper.assertBlockProperty(new BlockPos(2, 1, 4), StairBlock.FACING, Direction.SOUTH);
        helper.assertBlockPresent(Blocks.CHEST, new BlockPos(1, 1, 4));
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 4)));
        helper.assertTrue(chest != null && chest.getItem(0).is(Items.DIAMOND), "the diamond didn't come with the chest");
        Sections.undo(level, who);
        Sections.runAll();
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 4));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 4));
        helper.assertTrue(Sections.undo(level, who) != null, "undo twice");
        helper.assertTrue(Sections.delete(name) && Sections.info(name) == null, "couldn't delete it");
        helper.succeed();
    }
}
