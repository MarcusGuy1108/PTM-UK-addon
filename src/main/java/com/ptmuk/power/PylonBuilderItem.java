package com.ptmuk.power;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Places a whole tower, with the line running the way the player is looking. */
public class PylonBuilderItem extends Item {
    private final Supplier<Block> block;

    public PylonBuilderItem(Supplier<Block> block) {
        super(new Properties().stacksTo(16));
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        PylonBlock pylon = (PylonBlock) block.get();
        BlockPos origin = ctx.getClickedPos().relative(ctx.getClickedFace());
        Direction facing = ctx.getHorizontalDirection();
        PylonData data = PylonData.get(pylon.name);
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        ItemStack stack = ctx.getItemInHand();
        // a second use on the same spot within 10 seconds clears whatever is in the way
        CompoundTag pending = stack.getTagElement("pending");
        boolean force = pending != null && pending.getLong("pos") == origin.asLong() && pending.getInt("facing") == facing.get2DDataValue()
                && level.getGameTime() - pending.getLong("time") < 200;
        stack.removeTagKey("pending");
        int blocked = 0;
        BlockPos first = null;
        for (BlockPos cell : data.cells) {
            BlockPos p = origin.offset(PylonData.rotate(cell, facing));
            if (level.isOutsideBuildHeight(p)) {
                message(ctx, "The tower doesn't fit below the build limit here", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if (!level.getBlockState(p).canBeReplaced(place)) {
                blocked++;
                if (first == null) {
                    first = p;
                }
            }
        }
        if (blocked > 0 && !force) {
            CompoundTag t = stack.getOrCreateTagElement("pending");
            t.putLong("pos", origin.asLong());
            t.putInt("facing", facing.get2DDataValue());
            t.putLong("time", level.getGameTime());
            message(ctx, blocked + " block" + (blocked == 1 ? " is" : "s are") + " in the way (first at " + first.getX() + " " + first.getY()
                    + " " + first.getZ() + "). Use again to clear them and build anyway.", ChatFormatting.GOLD);
            return InteractionResult.FAIL;
        }
        for (int i = 0; i < data.cells.size(); i++) {
            BlockPos p = origin.offset(PylonData.rotate(data.cells.get(i), facing));
            BlockState there = level.getBlockState(p);
            if (there.getDestroySpeed(level, p) < 0 && !there.isAir() && !(there.getBlock() instanceof PylonBlock)) {
                continue;   // never clear bedrock and other unbreakable blocks
            }
            level.setBlock(p, pylon.defaultBlockState().setValue(PylonBlock.FACING, facing).setValue(pylon.cell, i),
                    Block.UPDATE_CLIENTS);
        }
        level.playSound(null, origin, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 0.8F);
        if (ctx.getPlayer() != null && !ctx.getPlayer().isCreative()) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    private static void message(UseOnContext ctx, String text, ChatFormatting colour) {
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.literal(text).withStyle(colour), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Places the whole tower; the line runs the way you face.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("If blocks are in the way, use it again to clear them.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Connect towers with the Overhead Line Tool; remove with the Dismantling Tool.").withStyle(ChatFormatting.GRAY));
    }
}
