package com.ptmuk.town;

import com.ptmuk.road.RoadPlannerItem;
import java.util.List;
import net.minecraft.ChatFormatting;
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

/** Trees, bushes, hedges and street furniture: right-click to put them down (from up to 128 blocks), sneak-right-click to choose. */
public class TownBuilderItem extends Item {
    public TownBuilderItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide && RoadPlannerItem.allowed(player)) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.town.TownBuilderClient.use(hand, player.isShiftKeyDown()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getLevel().isClientSide && player != null && RoadPlannerItem.allowed(player)) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.town.TownBuilderClient.use(context.getHand(), player.isShiftKeyDown()));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String kind = TownBuilder.kind(stack);
        TreeShape.Species s = TownBuilder.species(kind);
        StreetItem item = TownBuilder.street(kind);
        if (s != null) {
            tooltip.add(Component.literal(s.title + ", " + (s.category == TreeShape.Category.HEDGE ? TreeShape.HEDGE_SIZES : TreeShape.SIZES)[TownBuilder.size(stack) - 1]
                    .toLowerCase()).withStyle(ChatFormatting.GOLD));
        } else if (item != null) {
            tooltip.add(Component.literal(item.title + ", every " + TownBuilder.spacing(stack) + " blocks").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.literal("Right-click: plant a tree (or grow one), or start and end a line").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak + right-click: choose what, and undo").withStyle(ChatFormatting.DARK_GRAY));
    }
}
