package com.ptmuk.fare;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A concessionary bus pass: every tap is accepted and nothing is charged. */
public class BusPassItem extends Item {
    public BusPassItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.ptmuk.bus_pass.free").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("tooltip.ptmuk.cube_card.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
