package com.ptmuk.darts;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A set of darts (never used up). Hold use to draw back and steady your aim (about a second),
 * let go to throw: a dart thrown in a hurry flies wide; sneaking steadies your hand.
 */
public class DartsItem extends Item {
    public DartsItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity who, int left) {
        int held = getUseDuration(stack) - left;
        if (held < 4 || !(who instanceof Player player)) {
            return;
        }
        float steady = Math.min(1, held / 20f);
        if (!level.isClientSide) {
            DartEntity dart = new DartEntity(level, player);
            float wobble = (player.isShiftKeyDown() ? 0.5f : 1.1f) * (1.6f - steady);
            dart.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.1f + 0.5f * steady, wobble);
            level.addFreshEntity(dart);
            player.getCooldowns().addCooldown(this, 8);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.4f, 1.8f);
    }
}
