package com.ptmuk.power;

import com.ptmuk.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

/** The only way to take a tower down: right-click any part of it (from up to 96 blocks away). */
public class PylonDismantlerItem extends Item {
    public PylonDismantlerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return ctx.getPlayer() == null ? InteractionResult.PASS : dismantle(ctx.getLevel(), ctx.getPlayer(), ctx.getClickedPos());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        BlockPos found = CableToolItem.findTower(level, player.getEyePosition(), player.getLookAngle());
        ItemStack stack = player.getItemInHand(hand);
        return found == null ? InteractionResultHolder.pass(stack) : new InteractionResultHolder<>(dismantle(level, player, found), stack);
    }

    private static InteractionResult dismantle(Level level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PylonBlock pylon)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            pylon.dismantle(server, state, pos);
            if (!player.isCreative()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.PYLON_BUILDERS.get(pylon.name).get()));
            }
            level.playSound(null, pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.7F, 0.9F);
            player.displayClientMessage(Component.literal("Tower dismantled"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click a tower to take the whole thing down.").withStyle(ChatFormatting.GRAY));
    }
}
