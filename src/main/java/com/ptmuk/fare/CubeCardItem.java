package com.ptmuk.fare;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Cube Card: a pay-as-you-go travel card holding credit (in PTM2 money). Top it up at a
 * top-up machine, then tap it on a bus's card reader or a card reader block.
 */
public class CubeCardItem extends Item {
    private static final String BALANCE = "Balance";

    public CubeCardItem() {
        super(new Properties().stacksTo(1));
    }

    public static double balance(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0.0 : tag.getDouble(BALANCE);
    }

    public static void setBalance(ItemStack stack, double balance) {
        stack.getOrCreateTag().putDouble(BALANCE, Fares.round(Math.max(0.0, balance)));
    }

    /** Right-click away from a reader: show the balance (taps are handled by FareEvents). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.ptmuk.card.balance",
                    Fares.money(level, balance(stack))).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        String amount = level == null ? String.format(java.util.Locale.US, "%.2f", balance(stack)) : Fares.money(level, balance(stack));
        lines.add(Component.translatable("tooltip.ptmuk.cube_card.balance", amount).withStyle(ChatFormatting.AQUA));
        if (level != null) {
            lines.add(Component.translatable("tooltip.ptmuk.cube_card.fare", Fares.money(level, Fares.fare(level)))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.ptmuk.cube_card.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
