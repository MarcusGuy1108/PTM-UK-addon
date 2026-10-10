package com.ptmuk.building;

import com.ptmuk.client.ClientHooks;
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

/**
 * Places ready-made British buildings. Right-click places the chosen building where you look
 * (front towards you); sneak + right-click opens the catalogue; R turns it. Creative mode only.
 * The work happens on the client (aim, outline) and in PrefabToolMessage on the server.
 */
public class PrefabToolItem extends Item {
    public PrefabToolItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean allowed(Player player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.usePrefabTool(hand, player.isShiftKeyDown()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.usePrefabTool(context.getHand(), player.isShiftKeyDown()));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        PrefabCatalog.Entry e = PrefabPlacement.selected(stack);
        if (e != null) {
            tooltip.add(Component.literal(e.name()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(e.width() + " wide, " + e.depth() + " deep, " + e.height() + " high").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("No building chosen").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Right-click: place where you're looking").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak + right-click: choose a building").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("R: turn it 90 degrees").withStyle(ChatFormatting.DARK_GRAY));
    }
}
