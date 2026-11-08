package com.ptmuk.building;

import com.ptmuk.client.ClientHooks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Copies big sections of the world to files and pastes them anywhere, in any world (Sections).
 * Right-click two corners (where you aim, up to 128 blocks off, or under your feet), sneak +
 * right-click for the copy, the list of sections, turning and undo; with a section picked,
 * right-click pastes it where you aim. Creative mode (or op) only. Its settings live on the stack.
 */
public class SectionToolItem extends Item {
    public static final String C1 = "c1", C2 = "c2", NEXT = "next", PICKED = "picked", ROT = "rot", MIRROR = "mirror", KEEP_AIR = "keepAir",
            SIZE = "size";

    public SectionToolItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static BlockPos corner(ItemStack stack, String key) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(key) ? BlockPos.of(t.getLong(key)) : null;
    }

    /** The picked section's size and anchor (sx, sy, sz, ax, ay, az), or null with none picked. */
    public static int[] size(ItemStack stack) {
        CompoundTag t = stack.getTag();
        int[] s = t == null ? new int[0] : t.getIntArray(SIZE);
        return t != null && !t.getString(PICKED).isEmpty() && s.length == 6 ? s : null;
    }

    public static Rotation rotation(ItemStack stack) {
        return Rotation.values()[Math.floorMod(stack.getOrCreateTag().getInt(ROT), 4)];
    }

    public static Mirror mirror(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(MIRROR) ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.useSectionTool(hand, player.isShiftKeyDown()));
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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.useSectionTool(context.getHand(), player.isShiftKeyDown()));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String picked = stack.getTag() == null ? "" : stack.getTag().getString(PICKED);
        tooltip.add(Component.literal(picked.isEmpty() ? "Picking an area" : "Pasting " + picked).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Right-click: a corner (or paste the picked section)").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak + right-click: copy, sections, turn, undo").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("R: turn the section 90 degrees").withStyle(ChatFormatting.DARK_GRAY));
    }
}
