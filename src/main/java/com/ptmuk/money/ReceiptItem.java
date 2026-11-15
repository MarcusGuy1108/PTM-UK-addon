package com.ptmuk.money;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A till or cash machine receipt: who it's from and its lines, shown when you hover over it. */
public class ReceiptItem extends Item {
    public ReceiptItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack make(Item item, String from, List<String> lines) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("from", from);
        ListTag list = new ListTag();
        lines.forEach(l -> list.add(StringTag.valueOf(l)));
        tag.put("lines", list);
        stack.setHoverName(Component.literal("Receipt: " + from).withStyle(s -> s.withItalic(false)));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        for (Tag t : tag.getList("lines", Tag.TAG_STRING)) {
            lines.add(Component.literal(t.getAsString()).withStyle(ChatFormatting.GRAY));
        }
    }
}
