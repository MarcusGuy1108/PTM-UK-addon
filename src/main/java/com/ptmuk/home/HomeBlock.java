package com.ptmuk.home;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of home furniture. It faces whoever placed it and, depending on its type, you can sit on
 * it, keep things in it, switch it on, run its taps (and fill a bucket or bottle) or cook in it.
 * Tall and wide pieces keep their behaviour in the base block (the lower or left one).
 */
public class HomeBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    protected final HomeFurnitureType type;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public HomeBlock(HomeFurnitureType type) {
        super(Properties.of().strength(0.8F, 3.0F).sound(type.sound).noOcclusion()
                .lightLevel(state -> state.hasProperty(LIT) && state.getValue(LIT) ? type.light : 0));
        this.type = type;
        registerDefaultState(initialState(stateDefinition.any()));
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            shapes.put(dir, rotated(type.box, 0, dir));
        }
    }

    public HomeFurnitureType type() {
        return type;
    }

    protected BlockState initialState(BlockState state) {
        return state.setValue(FACING, Direction.NORTH).setValue(LIT, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** A box given in pixels for a north-facing block, moved down by dy, kept inside the block and turned to face dir. */
    static VoxelShape rotated(double[] b, double dy, Direction dir) {
        double y1 = Math.max(0, b[1] + dy);
        double y2 = Math.min(16, Math.max(y1 + 0.5, b[4] + dy));
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        return switch (dir) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // ------------------------------------------------------------ the base block of tall and wide pieces

    /** Where a multi-block piece keeps its storage and switch: the lower or left block. */
    protected BlockPos basePos(BlockState state, BlockPos pos) {
        return pos;
    }

    protected boolean isBase(BlockState state) {
        return true;
    }

    /** Every block of this piece (for switching all of it on and off together). */
    protected List<BlockPos> parts(BlockState state, BlockPos base) {
        return List.of(base);
    }

    // ------------------------------------------------------------ using it

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos base = basePos(state, pos);
        BlockState baseState = level.getBlockState(base);
        ItemStack held = player.getItemInHand(hand);
        HomeFurnitureType.Effect effect = type.effect;
        boolean taps = effect == HomeFurnitureType.Effect.TAP || effect == HomeFurnitureType.Effect.BATH;
        double hitY = hit.getLocation().y - pos.getY();
        // the sink's taps are on the worktop, its cupboard underneath
        boolean onTaps = taps && (type.rows == 0 || hitY > 13.0 / 16);
        if (onTaps && (held.is(Items.BUCKET) || held.is(Items.GLASS_BOTTLE))) {
            if (!level.isClientSide) {
                ItemStack filled = held.is(Items.BUCKET) ? new ItemStack(Items.WATER_BUCKET)
                        : PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled));
                level.playSound(null, pos, held.is(Items.BUCKET) ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL,
                        SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (effect == HomeFurnitureType.Effect.FLUSH && player.isShiftKeyDown()) {
            level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 0.7F);
            level.playSound(player, pos, SoundEvents.PLAYER_SPLASH, SoundSource.BLOCKS, 0.5F, 1.4F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (type.seat > 0 && !player.isShiftKeyDown() && !onTaps) {
            return sit(state, level, pos, player);
        }
        if (type.oven) {
            if (!level.isClientSide && level.getBlockEntity(base) instanceof MenuProvider oven) {
                player.openMenu(oven);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (type.rows > 0 && !onTaps) {
            if (!level.isClientSide && level.getBlockEntity(base) instanceof HomeStorageBlockEntity box) {
                player.openMenu(box);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (type.toggle && (effect != HomeFurnitureType.Effect.BATH || player.isShiftKeyDown() || type.seat == 0)) {
            if (!level.isClientSide) {
                setLit(level, baseState, base, !baseState.getValue(LIT));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult sit(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger() || !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty()) {
            return InteractionResult.PASS;
        }
        SeatEntity seat = new SeatEntity(HomeBlocks.SEAT.get(), level);
        Vec3 at = Vec3.atBottomCenterOf(pos).add(0, type.seat / 16.0, 0);
        seat.setPos(at.x, at.y, at.z);
        seat.setYRot(state.getValue(FACING).toYRot());
        level.addFreshEntity(seat);
        player.startRiding(seat);
        if (player instanceof ServerPlayer sp) {           // turn round to face out of the seat
            Vec3 ahead = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(state.getValue(FACING).getNormal()).scale(4)).add(0, 0.6, 0);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, ahead);
        }
        return InteractionResult.CONSUME;
    }

    /** Switch the whole piece on or off, with its sound, and set it to switch itself off again. */
    protected void setLit(Level level, BlockState baseState, BlockPos base, boolean on) {
        for (BlockPos p : parts(baseState, base)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() == this) {
                level.setBlock(p, s.setValue(LIT, on), 3);
            }
        }
        SoundEvent sound = switch (type.effect) {
            case TAP, BATH, SHOWER -> on ? SoundEvents.BUCKET_EMPTY : SoundEvents.LEVER_CLICK;
            case FIRE -> on ? SoundEvents.FIRECHARGE_USE : SoundEvents.LEVER_CLICK;
            case TOASTER -> on ? SoundEvents.CHAIN_PLACE : SoundEvents.CHAIN_BREAK;
            case MICROWAVE -> on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.NOTE_BLOCK_BELL.get();
            case KETTLE -> on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.FIRE_EXTINGUISH;
            default -> on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF;
        };
        level.playSound(null, base, sound, SoundSource.BLOCKS, type.effect == HomeFurnitureType.Effect.KETTLE && !on ? 0.4F : 0.7F, 1);
        if (on && type.run > 0) {
            level.scheduleTick(base, this, type.run);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (type.run > 0 && state.getValue(LIT) && isBase(state)) {
            setLit(level, state, pos, false);
        }
    }

    // ------------------------------------------------------------ steam, water and dishes on the client

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || !isBase(state)) {
            return;
        }
        Direction f = state.getValue(FACING);
        switch (type.effect) {
            case TAP -> {
                double y = type.rows > 0 ? 17.0 / 16 : 15.2 / 16;
                Vec3 p = local(pos, f, 8, y * 16, 9.8);
                level.addParticle(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 0, 0, 0);
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.SPLASH, p.x, pos.getY() + (type.rows > 0 ? 0.88 : 0.8), p.z, 0, 0, 0);
                }
            }
            case BATH -> {
                Vec3 p = local(pos, f, 2.5, 10.5, 11.8);
                level.addParticle(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 0, 0, 0);
                level.addParticle(ParticleTypes.SPLASH, p.x, pos.getY() + 0.2, p.z, 0, 0, 0);
            }
            case SHOWER -> {
                for (int i = 0; i < 3; i++) {
                    Vec3 p = local(pos, f, 5.8 + random.nextDouble() * 4.4, 26.4, 5.8 + random.nextDouble() * 4.4);
                    level.addParticle(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 0, 0, 0);
                }
                level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 0, 0, 0);
                if (random.nextInt(10) == 0) {
                    level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, SoundEvents.WEATHER_RAIN,
                            SoundSource.BLOCKS, 0.3F, 1.4F, false);
                }
            }
            case KETTLE -> {
                Vec3 p = local(pos, f, 4.6, 6, 8.5);
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.CLOUD, p.x, p.y + levelDrop(state), p.z, 0, 0.03, 0);
                }
            }
            case WASHER -> {
                if (random.nextInt(12) == 0) {
                    level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.MINECART_RIDING,
                            SoundSource.BLOCKS, 0.08F, 0.6F, false);
                }
            }
            case FIRE -> {
                if (random.nextInt(24) == 0) {
                    level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT,
                            SoundSource.BLOCKS, 0.4F, 1.0F, false);
                }
            }
            default -> {
            }
        }
    }

    /** How far a tabletop piece sinks into the furniture under it, in blocks (negative). */
    protected double levelDrop(BlockState state) {
        return 0;
    }

    /** A point given in pixels in the north-facing model, turned to face f, in world space. */
    static Vec3 local(BlockPos pos, Direction f, double x, double y, double z) {
        double lx = x / 16 - 0.5, lz = z / 16 - 0.5;
        double wx, wz;
        switch (f) {
            case SOUTH -> { wx = -lx; wz = -lz; }
            case EAST -> { wx = -lz; wz = lx; }
            case WEST -> { wx = lz; wz = -lx; }
            default -> { wx = lx; wz = lz; }
        }
        return new Vec3(pos.getX() + 0.5 + wx, pos.getY() + y / 16, pos.getZ() + 0.5 + wz);
    }

    // ------------------------------------------------------------ storage and the oven

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isBase(state)) {
            return null;
        }
        if (type.oven) {
            return new OvenBlockEntity(pos, state);
        }
        return type.rows > 0 ? new HomeStorageBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        if (!type.oven || level.isClientSide || beType != HomeBlocks.OVEN_BE.get()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (l, p, s, be) -> AbstractFurnaceBlockEntity.serverTick(l, p, s, (AbstractFurnaceBlockEntity) be);
        return ticker;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HomeStorageBlockEntity box) {
                Containers.dropContents(level, pos, box);
            } else if (be instanceof OvenBlockEntity oven) {
                Containers.dropContents(level, pos, oven);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    static VoxelShape empty() {
        return Shapes.empty();
    }
}
