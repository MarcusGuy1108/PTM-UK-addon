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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block cell of a transmission tower. Towers are real blocks so they show up in Distant
 * Horizons' LODs; each cell's model holds the members whose centres fall inside it. Placed as a
 * whole by {@link PylonBuilderItem}; they can't be broken by hand, explosions or pistons, only
 * taken down as a whole with the {@link PylonDismantlerItem}.
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
        // unbreakable by hand and immune to explosions and pistons: use the Pylon Dismantling Tool
        return Properties.of().mapColor(wood ? MapColor.WOOD : MapColor.METAL).strength(-1.0F, 3600000.0F)
                .sound(wood ? SoundType.WOOD : SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK).noLootTable();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IntegerProperty.create("cell", 0, Math.max(1, CELLS.get()) - 1));
    }

    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    /** The steelwork bounds of this cell, so towers are solid (and Distant Horizons draws them). */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = state.getValue(cell);
        Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(i * 4 + facing.get2DDataValue(), k -> {
            PylonData data = PylonData.get(name);
            if (i >= data.boxes.size()) {
                return Shapes.block();
            }
            double[] b = data.boxes.get(i);
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            return switch (facing) {
                case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
                case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
                case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
                default -> Block.box(x0, b[1], z0, x1, b[4], z1);
            };
        });
    }

    /** Removes the whole tower this cell belongs to (used by the dismantling tool). */
    public void dismantle(ServerLevel level, BlockState state, BlockPos pos) {
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
