package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.block.SignalStyle;
import com.ptmuk.block.SignalType;
import com.ptmuk.block.UkTrafficSignal;
import com.ptmuk.registry.ModBlocks;
import com.rinventor.ptm2.core.properties.TrafficLightColors;
import com.rinventor.ptm2.core.properties.TrafficLightTypes;
import com.rinventor.ptm2.core.properties.VDMemoryIDs;
import com.rinventor.ptm2.dimension.virtual.storage.VDStorageHandler;
import com.rinventor.ptm2.dimension.virtual.types.VDTrafficLight;
import com.rinventor.ptm2.packets.sync.TrafficLightClientboundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.rinventor.ptm2.core.properties.TrafficLightStates;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Run with {@code ./gradlew runGameTestServer}. These drive the signal through the same
 * code path PTM2's intersection controller uses to switch physical lights.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public class UkSignalGameTests {
    private static final BlockPos SIGNAL = new BlockPos(2, 1, 2);

    @GameTest(template = "empty5x4x5")
    public static void registersWithPtmTraffic(GameTestHelper helper) {
        helper.setBlock(SIGNAL, ModBlocks.signal(SignalStyle.LED, SignalType.STANDARD).get());
        BlockPos abs = helper.absolutePos(SIGNAL);
        String data = VDStorageHandler.get(helper.getLevel(), abs, VDMemoryIDs.TRAFFIC_LIGHT);
        helper.assertTrue(data != null && !data.isEmpty(), "UK signal was not registered in PTM2 traffic light storage");
        VDTrafficLight light = new VDTrafficLight(data);
        helper.assertTrue(light.lightCount == 3, "expected a 3-aspect light, got " + light.lightCount);
        helper.assertTrue(light.direction == TrafficLightTypes.STRAIGHT, "expected STRAIGHT, got " + light.direction);
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void followsUkSequence(GameTestHelper helper) {
        helper.setBlock(SIGNAL, ModBlocks.signal(SignalStyle.CLASSIC, SignalType.STANDARD).get());
        BlockPos abs = helper.absolutePos(SIGNAL);

        helper.startSequence()
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.RED))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.RED, false))
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.YELLOW))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.YELLOW, true))   // red + amber
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.GREEN))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.GREEN, false))
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.BLINKING_GREEN))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.BLINKING_GREEN, false))
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.YELLOW))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.YELLOW, false))  // amber only
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.RED))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.RED, false))
                .thenSucceed();
    }

    @GameTest(template = "empty5x4x5")
    public static void filterArrowKeepsUkSequence(GameTestHelper helper) {
        helper.setBlock(SIGNAL, ModBlocks.signal(SignalStyle.LED, SignalType.LEFT_FILTER).get());
        BlockPos abs = helper.absolutePos(SIGNAL);

        // PTM2 encodes the filter arrow into the colour, e.g. RED_ARROW = red + arrow lit.
        helper.startSequence()
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.RED_ARROW))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.RED_ARROW, false))
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.YELLOW_ARROW))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.YELLOW_ARROW, true))   // red + amber + arrow
                .thenExecute(() -> ptmSwitch(helper, abs, TrafficLightColors.GREEN_ARROW))
                .thenIdle(2)
                .thenExecute(() -> expect(helper, abs, TrafficLightColors.GREEN_ARROW, false))
                .thenSucceed();
    }

    @GameTest(template = "empty5x4x5")
    public static void pedestrianSignalRegistersAsPedestrianLight(GameTestHelper helper) {
        helper.setBlock(SIGNAL, ModBlocks.signal(SignalStyle.LED, SignalType.PELICAN).get());
        VDTrafficLight light = new VDTrafficLight(VDStorageHandler.get(helper.getLevel(), helper.absolutePos(SIGNAL), VDMemoryIDs.TRAFFIC_LIGHT));
        helper.assertTrue(light.pedestrian, "pelican signal should be a PTM2 pedestrian light");
        helper.assertTrue(light.lightCount == 2, "expected a 2-aspect light, got " + light.lightCount);
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void everySignalRegistersWithPtm(GameTestHelper helper) {
        int x = 0;
        for (var block : ModBlocks.SIGNALS.values()) {
            BlockPos pos = new BlockPos(x % 5, 1 + x / 25, (x / 5) % 5);
            helper.setBlock(pos, block.get());
            String data = VDStorageHandler.get(helper.getLevel(), helper.absolutePos(pos), VDMemoryIDs.TRAFFIC_LIGHT);
            helper.assertTrue(data != null && !data.isEmpty(), block.getId() + " was not registered in PTM2 traffic light storage");
            x++;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void sneakRightClickTogglesBackingBoard(GameTestHelper helper) {
        helper.setBlock(SIGNAL, ModBlocks.signal(SignalStyle.LED_TUNNEL, SignalType.STANDARD).get());
        helper.assertTrue(helper.getBlockState(SIGNAL).getValue(UkTrafficSignal.BOARD), "vehicle heads should start with a board");
        Player player = helper.makeMockPlayer();
        player.setShiftKeyDown(true);
        helper.useBlock(SIGNAL, player);
        helper.assertFalse(helper.getBlockState(SIGNAL).getValue(UkTrafficSignal.BOARD), "sneak + right-click should remove the board");
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void headsMountOnUkPoles(GameTestHelper helper) {
        // poles on all four sides, so whichever way the player faces PTM2 should find a post
        for (BlockPos side : new BlockPos[]{SIGNAL.north(), SIGNAL.south(), SIGNAL.east(), SIGNAL.west()}) {
            helper.setBlock(side, ModBlocks.POLES.get("signal_pole_black").get());
        }
        Player player = helper.makeMockPlayer();
        player.setYRot(180);
        ItemStack stack = new ItemStack(ModBlocks.signal(SignalStyle.LED, SignalType.STANDARD).get());
        BlockPos below = helper.absolutePos(SIGNAL.below());
        helper.setBlock(SIGNAL.below(), Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        BlockState placed = helper.getBlockState(SIGNAL);
        helper.assertTrue(placed.getBlock() instanceof UkTrafficSignal, "signal was not placed: " + placed);
        TrafficLightStates attachment = placed.getValue(UkTrafficSignal.ATTACHMENT);
        helper.assertTrue(attachment == TrafficLightStates.POST || attachment == TrafficLightStates.LEFT_POST
                || attachment == TrafficLightStates.RIGHT_POST, "head should mount on the UK pole, got " + attachment);
        helper.succeed();
    }

    private static void ptmSwitch(GameTestHelper helper, BlockPos abs, TrafficLightColors colour) {
        TrafficLightClientboundPacket.handle(helper.getLevel(), colour, TrafficLightTypes.STRAIGHT, abs.getX(), abs.getY(), abs.getZ());
    }

    /** Checks both the block (what players see) and PTM2's traffic storage (what AI cars obey). */
    private static void expect(GameTestHelper helper, BlockPos abs, TrafficLightColors colour, boolean redAmber) {
        BlockState state = helper.getBlockState(SIGNAL);
        helper.assertTrue(state.getValue(UkTrafficSignal.STATE) == colour, "expected " + colour + " but was " + state.getValue(UkTrafficSignal.STATE));
        helper.assertTrue(state.getValue(UkTrafficSignal.RED_AMBER) == redAmber, "expected red_amber=" + redAmber + " at " + colour);
        expectStored(helper, abs, colour);
    }

    private static void expectStored(GameTestHelper helper, BlockPos abs, TrafficLightColors colour) {
        VDTrafficLight light = new VDTrafficLight(VDStorageHandler.get(helper.getLevel(), abs, VDMemoryIDs.TRAFFIC_LIGHT));
        helper.assertTrue(light.state == colour, "PTM2 storage has " + light.state + ", expected " + colour);
    }
}
