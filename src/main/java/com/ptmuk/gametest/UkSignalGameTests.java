package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
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
        helper.setBlock(SIGNAL, ModBlocks.UK_SIGNAL_MODERN.get());
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
        helper.setBlock(SIGNAL, ModBlocks.UK_SIGNAL_CLASSIC.get());
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
