package com.ptmuk.home;

import com.ptmuk.road.RoadPlannerItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Right-click the floor of an empty room, pick what it is, and it's furnished. Right-click the air to undo. */
public class InteriorDesignerItem extends Item {
    public InteriorDesignerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide && context.getPlayer() != null && RoadPlannerItem.allowed(context.getPlayer())) {
            // clicking a wall low down means the floor in front of it
            BlockPos floor = context.getClickedFace() == Direction.UP ? context.getClickedPos()
                    : context.getClickedPos().relative(context.getClickedFace()).below();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.home.InteriorDesignerScreen.open(floor));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide && RoadPlannerItem.allowed(player)) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.home.InteriorDesignerScreen.open(null));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click the floor of a room: furnish it as a shop, cafe, office or a room of a house")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click the air: undo").withStyle(ChatFormatting.DARK_GRAY));
    }
}
