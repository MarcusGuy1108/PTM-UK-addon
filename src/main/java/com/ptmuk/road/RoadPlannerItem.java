package com.ptmuk.road;

import com.ptmuk.road.plan.RoadPlan;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Opens the Road Planner: a top-down map to draw roads, junctions and roundabouts on, then build
 * them. The plan is kept on the item, so it can be picked up again to extend it. Creative mode only.
 */
public class RoadPlannerItem extends Item {
    public RoadPlannerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean allowed(Player player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.road.RoadPlannerScreen.open(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RoadPlan plan = RoadPlans.read(stack);
        int roads = plan.segments.size();
        long built = plan.segments.stream().filter(s -> s.built).count();
        tooltip.add(Component.literal(roads == 0 ? "Empty plan" : roads + " road" + (roads == 1 ? "" : "s") + " planned, " + built + " built")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click: open the planner map").withStyle(ChatFormatting.DARK_GRAY));
    }
}
