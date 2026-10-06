package com.ptmuk.block;

import com.rinventor.ptm2.core.properties.TrafficLightColors;
import com.rinventor.ptm2.objects.blockentities.traffic_light.TrafficLight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A UK traffic signal head.
 *
 * <p>Extends PTM2's own {@link TrafficLight} so PTM2 registers it in its virtual traffic
 * storage, its intersection controller switches it, and its AI vehicles and pedestrians obey
 * it exactly like PTM2's built-in lights.
 *
 * <p>PTM2 drives lights RED -> YELLOW -> GREEN -> BLINKING_GREEN -> YELLOW -> RED. The UK
 * sequence is red -> red+amber -> green -> amber -> red, so a yellow that follows red is
 * displayed as red+amber (tracked by {@link #RED_AMBER}), and a yellow that follows green
 * is plain amber. How each colour is drawn per head type lives in the blockstate files.
 */
public class UkTrafficSignal extends TrafficLight {
    public static final BooleanProperty RED_AMBER = BooleanProperty.create("red_amber");
    public static final BooleanProperty BOARD = BooleanProperty.create("board");

    private final SignalStyle style;
    private final SignalType type;

    public UkTrafficSignal(SignalStyle style, SignalType type) {
        super(type.lightCount, type.direction, type.pedestrian, false);
        this.style = style;
        this.type = type;
        registerDefaultState(defaultBlockState().setValue(RED_AMBER, false).setValue(BOARD, type.boardable && style != SignalStyle.LED_SLIM));
    }

    public SignalStyle getStyle() {
        return style;
    }

    public SignalType getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RED_AMBER, BOARD);
    }

    /** Classic (bulb) heads get a block entity so the client can fade the lamps. */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return style.isBulb() ? new ClassicLampBlockEntity(pos, state) : super.newBlockEntity(pos, state);
    }

    /** Sneak + right-click with an empty hand adds or removes the backing board. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (type.boardable && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.cycle(BOARD), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level.isClientSide || !state.is(this) || !oldState.is(this)) {
            return;
        }
        TrafficLightColors previous = mainColour(oldState.getValue(STATE));
        TrafficLightColors current = mainColour(state.getValue(STATE));
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
        boolean redAmber = mainColour(state.getValue(STATE)) == TrafficLightColors.YELLOW;
        if (state.getValue(RED_AMBER) != redAmber) {
            level.setBlock(pos, state.setValue(RED_AMBER, redAmber), Block.UPDATE_ALL);
        }
    }

    /**
     * The colour of the main (round) aspects. PTM2 encodes a filter arrow into the state as
     * {@code main + 5 * arrow}, e.g. RED_ARROW is red with the arrow lit.
     */
    public static TrafficLightColors mainColour(TrafficLightColors colour) {
        int id = colour.getID();
        return id == 0 ? TrafficLightColors.OFF : TrafficLightColors.fromID((id - 1) % 5 + 1);
    }
}
