package com.ptmuk.map;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A British street nameplate: black capitals on a white plate, the district and postcode under
 * them. On a wall it's fixed flat to it; on the ground it stands on two short legs. Whatever it
 * says becomes the name of the road beside it on the map.
 */
public class NameplateBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Stood on two legs on the ground rather than fixed to a wall. */
    public static final BooleanProperty POSTS = BooleanProperty.create("posts");

    private static final VoxelShape[] WALL = {
            Block.box(0, 4, 0, 16, 13, 2), Block.box(14, 4, 0, 16, 13, 16), Block.box(0, 4, 14, 16, 13, 16), Block.box(0, 4, 0, 2, 13, 16)};
    private static final VoxelShape[] POST = {
            Block.box(0, 0, 7, 16, 13, 9), Block.box(7, 0, 0, 9, 13, 16), Block.box(0, 0, 7, 16, 13, 9), Block.box(7, 0, 0, 9, 13, 16)};

    public NameplateBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POSTS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POSTS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        boolean wall = face.getAxis().isHorizontal();
        return defaultBlockState().setValue(FACING, wall ? face : context.getHorizontalDirection().getOpposite()).setValue(POSTS, !wall);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(POSTS) ? POST : WALL)[state.getValue(FACING).get2DDataValue()];
    }

    /** A new plate says what the road beside it is called, or gives it a name if it hasn't one. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (level instanceof net.minecraft.server.level.ServerLevel server && level.getBlockEntity(pos) instanceof NameplateBlockEntity plate
                && plate.name.isBlank()) {
            String name = MapPlaces.nameNear(server, pos.getX() + 0.5, pos.getZ() + 0.5, 14);
            boolean named = name != null;
            if (!named) {
                name = StreetNames.pick(level.getRandom(), new java.util.HashSet<>());
            }
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("name", name);
            if (named) {
                plate.read(tag);                            // just show it; the road already has it
                plate.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            } else {
                plate.applyEdit(tag);                       // and name the road beside it
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NameplateBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openNameplateEditor(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
