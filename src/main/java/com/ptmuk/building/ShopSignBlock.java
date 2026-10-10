package com.ptmuk.building;

import com.ptmuk.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Shop fascia: a painted board fixed to a wall above a shop front. Boards side by side (same
 * facing, same row) make one sign; the left-hand board (as you look at it) holds the name, which
 * is drawn centred across the whole row. Right-click any board to edit it.
 */
public class ShopSignBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 16, 3),    // facing south: board against the wall to the north
            Block.box(13, 0, 0, 16, 16, 16),  // facing west
            Block.box(0, 0, 13, 16, 16, 16),  // facing north
            Block.box(0, 0, 0, 3, 16, 16)};   // facing east

    public ShopSignBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F).sound(SoundType.WOOD).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopSignBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            BlockPos anchor = ShopSignBlockEntity.anchor(level, pos, state.getValue(FACING));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openShopSignEditor(anchor));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
