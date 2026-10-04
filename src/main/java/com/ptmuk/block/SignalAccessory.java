package com.ptmuk.block;

import com.rinventor.ptm2.core.properties.TrafficLightStates;
import com.rinventor.ptm2.engine.base.PTMBlock;
import com.rinventor.ptm2.engine.base.PTMEntity;
import com.rinventor.ptm2.engine.computing.Rotation8;
import com.rinventor.ptm2.objects.blockentities.traffic_light.TrafficLight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Decorative signal furniture (sign plates, detectors, push-button units). Uses the same
 * rotation and attachment properties as PTM2's traffic lights so it lines up with the head
 * it sits under or above. Not a traffic light: PTM2 never switches it.
 */
public class SignalAccessory extends Block {
    public static final IntegerProperty ROTATION = TrafficLight.ROTATION;
    public static final EnumProperty<TrafficLightStates> ATTACHMENT = TrafficLight.ATTACHMENT;
    /** Push-button unit: WAIT indicator lit after the button is pressed. */
    public static final BooleanProperty WAIT = BooleanProperty.create("wait");
    private static final int WAIT_TICKS = 200;

    private final AccessoryType type;

    public SignalAccessory(AccessoryType type) {
        super(Properties.of().strength(1.0F, 10.0F).sound(SoundType.METAL).noOcclusion());
        this.type = type;
        registerDefaultState(stateDefinition.any()
                .setValue(ROTATION, 0)
                .setValue(ATTACHMENT, TrafficLightStates.CENTER)
                .setValue(UkTrafficSignal.BOARD, type.mount == AccessoryType.Mount.BELOW_HEAD)
                .setValue(WAIT, false));
    }

    public AccessoryType getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, ATTACHMENT, UkTrafficSignal.BOARD, WAIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int rot = Rotation8.get(PTMEntity.getYaw(context.getPlayer()));
        return defaultBlockState()
                .setValue(ROTATION, rot)
                .setValue(ATTACHMENT, attachmentFor(context.getLevel(), context.getClickedPos(), rot));
    }

    /** Sign plates follow the head's backing board: sneak + right-click with an empty hand toggles it. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (type.mount == AccessoryType.Mount.BELOW_HEAD && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.cycle(UkTrafficSignal.BOARD), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (type == AccessoryType.PUSH_BUTTON_UNIT) {
            // pressing the button lights WAIT for a while, like the real thing
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(WAIT, true), Block.UPDATE_ALL);
                level.scheduleTick(pos, this, WAIT_TICKS);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(WAIT)) {
            level.setBlock(pos, state.setValue(WAIT, false), Block.UPDATE_ALL);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double y1 = type.mount == AccessoryType.Mount.BELOW_HEAD ? 10 : type.mount == AccessoryType.Mount.ABOVE_HEAD ? 0 : 4;
        double y2 = type.mount == AccessoryType.Mount.BELOW_HEAD ? 16 : type.mount == AccessoryType.Mount.ABOVE_HEAD ? 5 : 12;
        int rot = state.getValue(ROTATION);
        Vec3 offset = state.getOffset(level, pos);
        if (rot % 2 == 1) {
            return Block.box(4, y1, 4, 12, y2, 12);
        }
        return switch (state.getValue(ATTACHMENT)) {
            case WALL -> PTMBlock.box(5, y1, 9, 11, y2, 16, offset, rot);
            case LEFT -> PTMBlock.box(10, y1, 4, 16, y2, 11, offset, rot);
            case RIGHT -> PTMBlock.box(0, y1, 4, 6, y2, 11, offset, rot);
            case POST -> PTMBlock.box(5, y1, 15, 11, y2, 22, offset, rot);
            case LEFT_POST -> PTMBlock.box(16, y1, 4, 22, y2, 11, offset, rot);
            case RIGHT_POST -> PTMBlock.box(-6, y1, 4, 0, y2, 11, offset, rot);
            default -> PTMBlock.box(5, y1, 4, 11, y2, 11, offset, rot);
        };
    }

    /**
     * Same attachment rules PTM2 uses when placing a traffic light (adapted from PTM2's
     * TrafficLight, MIT licence): prefer a streetpost behind or beside, then a solid block.
     */
    static TrafficLightStates attachmentFor(Level level, BlockPos pos, int rot) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (rot % 2 == 1) {
            // diagonal: the corner behind, then the two blocks either side of it
            int dx = rot == 1 || rot == 3 ? -1 : 1;
            int dz = rot == 1 || rot == 7 ? 1 : -1;
            int[][] candidates = {{dx, dz}, {0, dz}, {dx, 0}};
            for (int[] c : candidates) {
                if (post(level, x + c[0], y, z + c[1])) return TrafficLightStates.POST;
                if (solid(level, x + c[0], y, z + c[1])) return TrafficLightStates.WALL;
            }
            return TrafficLightStates.CENTER;
        }
        // "back" is the side the head is mounted on, "left"/"right" as seen from the road.
        int bx = 0, bz = 0, lx = 0, lz = 0;
        switch (rot) {
            case 0 -> { bz = 1; lx = 1; }
            case 2 -> { bx = -1; lz = 1; }
            case 4 -> { bz = -1; lx = -1; }
            default -> { bx = 1; lz = -1; }
        }
        if (post(level, x + lx, y, z + lz)) return TrafficLightStates.LEFT_POST;
        if (post(level, x - lx, y, z - lz)) return TrafficLightStates.RIGHT_POST;
        if (post(level, x + bx, y, z + bz)) return TrafficLightStates.POST;
        if (solid(level, x + lx, y, z + lz)) return TrafficLightStates.LEFT;
        if (solid(level, x - lx, y, z - lz)) return TrafficLightStates.RIGHT;
        if (solid(level, x + bx, y, z + bz)) return TrafficLightStates.WALL;
        return TrafficLightStates.CENTER;
    }

    private static boolean post(Level level, int x, int y, int z) {
        return PTMBlock.isStreetpost(level, x, y, z);
    }

    private static boolean solid(Level level, int x, int y, int z) {
        return PTMBlock.isSolid(level, x, y, z);
    }
}
