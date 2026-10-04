package com.ptmuk.block;

import com.rinventor.ptm2.core.properties.TrafficLightColors;
import com.rinventor.ptm2.core.properties.TrafficLightTypes;
import com.rinventor.ptm2.objects.blockentities.traffic_light.TrafficLight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A UK three-aspect traffic signal.
 *
 * <p>Extends PTM2's own {@link TrafficLight} so PTM2 registers it in its virtual traffic
 * storage, its intersection controller switches it, and its AI vehicles obey it exactly
 * like PTM2's built-in lights.
 *
 * <p>PTM2 drives lights RED -> YELLOW -> GREEN -> BLINKING_GREEN -> YELLOW -> RED. The UK
 * sequence is red -> red+amber -> green -> amber -> red, so a YELLOW that follows RED is
 * displayed as red+amber (tracked by {@link #RED_AMBER}), and a YELLOW that follows green
 * is plain amber. UK signals never flash green, so BLINKING_GREEN is shown as steady green.
 */
public class UkTrafficSignal extends TrafficLight {
    public static final BooleanProperty RED_AMBER = BooleanProperty.create("red_amber");

    private final SignalStyle style;

    public UkTrafficSignal(SignalStyle style) {
        super(3, TrafficLightTypes.STRAIGHT, false, false);
        this.style = style;
        registerDefaultState(defaultBlockState().setValue(RED_AMBER, false));
    }

    public SignalStyle getStyle() {
        return style;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RED_AMBER);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level.isClientSide || !state.is(this) || !oldState.is(this)) {
            return;
        }
        TrafficLightColors previous = oldState.getValue(STATE);
        TrafficLightColors current = state.getValue(STATE);
        if (previous == current) {
            return;
        }

        // Changing our own state here would stop PTM2 syncing the new colour into its traffic
        // storage (which the AI cars read), so the red+amber flag is applied on the next tick.
        boolean redToAmber = previous == TrafficLightColors.RED && current == TrafficLightColors.YELLOW;
        if (redToAmber || state.getValue(RED_AMBER)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Only scheduled on red -> amber (flag on) or when leaving amber with the flag still set (flag off). */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean redAmber = state.getValue(STATE) == TrafficLightColors.YELLOW;
        if (state.getValue(RED_AMBER) != redAmber) {
            level.setBlock(pos, state.setValue(RED_AMBER, redAmber), Block.UPDATE_ALL);
        }
    }
}
