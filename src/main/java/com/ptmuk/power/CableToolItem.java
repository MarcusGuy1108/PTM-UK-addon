package com.ptmuk.power;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Strings conductors between two towers: right-click one tower, then the next. Sneak + right-click
 * a tower to take down every span at it.
 */
public class CableToolItem extends Item {
    public static final int MAX_SPAN = 256;

    public CableToolItem() {
        super(new Properties().stacksTo(1));
    }

    public static final double REACH = 96;

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        return apply(ctx.getLevel(), ctx.getPlayer(), ctx.getItemInHand(), ctx.getClickedPos());
    }

    /**
     * Towers are big and usually out of reach, and mostly air: march along the player's view and
     * take the first tower cell within a block of it.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos found = findTower(level, player.getEyePosition(), player.getLookAngle());
        if (found == null) {
            return InteractionResultHolder.pass(stack);
        }
        return new InteractionResultHolder<>(apply(level, player, stack, found), stack);
    }

    static BlockPos findTower(Level level, Vec3 eye, Vec3 look) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double d = 0; d < REACH; d += 0.5) {
            Vec3 at = eye.add(look.scale(d));
            BlockPos centre = BlockPos.containing(at);
            if (d > 2 && !level.getBlockState(centre).getCollisionShape(level, centre).isEmpty()) {
                return null;   // hit the ground or a wall first
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        p.setWithOffset(centre, dx, dy, dz);
                        if (level.getBlockState(p).getBlock() instanceof PylonBlock) {
                            return p.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private InteractionResult apply(Level level, Player player, ItemStack stack, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof PylonBlock pylon)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos origin = pylon.origin(state, clicked);
        Direction facing = state.getValue(PylonBlock.FACING);
        if (player.isShiftKeyDown()) {
            int n = PowerLines.get(server).removeAt(server, origin);
            stack.removeTagKey("from");
            player.displayClientMessage(Component.literal("Removed " + n + " span" + (n == 1 ? "" : "s")), true);
            return InteractionResult.CONSUME;
        }
        CompoundTag from = stack.getTagElement("from");
        if (from == null) {
            CompoundTag t = stack.getOrCreateTagElement("from");
            t.putLong("pos", origin.asLong());
            t.putString("type", pylon.name);
            t.putInt("facing", facing.get2DDataValue());
            player.displayClientMessage(Component.literal("Line started; now right-click the next tower"), true);
            return InteractionResult.CONSUME;
        }
        BlockPos a = BlockPos.of(from.getLong("pos"));
        stack.removeTagKey("from");
        if (a.equals(origin)) {
            player.displayClientMessage(Component.literal("Cancelled"), true);
            return InteractionResult.CONSUME;
        }
        if (Math.sqrt(a.distSqr(origin)) > MAX_SPAN) {
            player.displayClientMessage(Component.literal("Span too long (max " + MAX_SPAN + " blocks)").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        boolean added = PowerLines.get(server).add(server, new PowerLines.Link(a, from.getString("type"),
                Direction.from2DDataValue(from.getInt("facing")), origin, pylon.name, facing));
        player.displayClientMessage(Component.literal(added ? "Conductors strung" : "Those towers are already connected"), true);
        if (added) {
            level.playSound(null, origin, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getTagElement("from") != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click a tower (from up to 96 blocks away), then the next one.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak + right-click a tower to remove its spans.").withStyle(ChatFormatting.GRAY));
    }
}
