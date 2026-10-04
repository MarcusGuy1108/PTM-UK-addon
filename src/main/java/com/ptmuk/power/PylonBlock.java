package com.ptmuk.power;

import com.ptmuk.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block cell of a transmission tower. Towers are real blocks so they show up in Distant
 * Horizons' LODs; each cell's model holds the members whose centres fall inside it. Placed as a
 * whole by {@link PylonBuilderItem}, and breaking any cell removes the whole tower.
 */
public class PylonBlock extends HorizontalDirectionalBlock {
    private static final ThreadLocal<Integer> CELLS = new ThreadLocal<>();
    private static boolean removing;

    public final String name;
    public final IntegerProperty cell;

    public PylonBlock(String name) {
        super(props(name));
        this.name = name;
        this.cell = (IntegerProperty) stateDefinition.getProperty("cell");
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static Properties props(String name) {
        CELLS.set(PylonCells.COUNTS.get(name));
        boolean wood = name.contains("pole");
        return Properties.of().strength(3.0F, 8.0F).sound(wood ? SoundType.WOOD : SoundType.METAL).noOcclusion().noCollission()
                .dynamicShape();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IntegerProperty.create("cell", 0, Math.max(1, CELLS.get()) - 1));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** Where the tower's base centre is, from any of its cells. */
    public BlockPos origin(BlockState state, BlockPos pos) {
        PylonData data = PylonData.get(name);
        int i = state.getValue(cell);
        if (i >= data.cells.size()) {
            return pos;
        }
        return pos.subtract(PylonData.rotate(data.cells.get(i), state.getValue(FACING)));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, new ItemStack(ModItems.PYLON_BUILDERS.get(name).get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(this) && !removing && level instanceof ServerLevel server) {
            removing = true;
            try {
                Direction facing = state.getValue(FACING);
                BlockPos origin = origin(state, pos);
                PylonData data = PylonData.get(name);
                for (int i = 0; i < data.cells.size(); i++) {
                    BlockPos p = origin.offset(PylonData.rotate(data.cells.get(i), facing));
                    BlockState s = level.getBlockState(p);
                    if (s.is(this) && s.getValue(cell) == i && s.getValue(FACING) == facing) {
                        level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                PowerLines.get(server).removeAt(server, origin);
            } finally {
                removing = false;
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
