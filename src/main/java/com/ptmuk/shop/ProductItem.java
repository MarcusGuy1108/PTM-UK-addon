package com.ptmuk.shop;

import com.ptmuk.money.CashItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Something from a shop. Off a shop's shelf it's tagged with its price and the shop, unpaid: you
 * can't eat or use it until you've paid at the till, and walking out with it sets the alarm off.
 * Food and drink fill you up (coffee and energy drinks put a spring in your step, a pint, lager
 * or wine makes you a bit wobbly); a scratchcard can be scratched for a prize; plasters and
 * painkillers help.
 */
public class ProductItem extends Item {
    private static final String PRICE = "ptmuk_price", UNPAID = "ptmuk_unpaid", SHOP = "ptmuk_shop";
    public final Product product;

    public ProductItem(Product product) {
        super(properties(product));
        this.product = product;
    }

    private static Item.Properties properties(Product p) {
        Item.Properties props = new Item.Properties().stacksTo(16);
        if (p.food > 0) {
            FoodProperties.Builder food = new FoodProperties.Builder().nutrition(p.food).saturationMod(p.saturation);
            if (p == Product.COFFEE) {
                food.effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 0), 1);
            } else if (p == Product.ENERGY_DRINK) {
                food.effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 0), 1)
                        .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 60, 0), 1);
            } else if (p == Product.PINT || p == Product.LAGER || p == Product.WINE) {
                food.effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 20 * 6, 0), 1);
            }
            props.food(food.alwaysEat().build());
        }
        return props;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return product.drink ? UseAnim.DRINK : super.getUseAnimation(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (unpaid(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("You haven't paid for that yet. Take it to the till.")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        switch (product) {
            case SCRATCHCARD -> {
                if (!level.isClientSide) {
                    scratch(player);
                    stack.shrink(1);
                }
                level.playSound(player, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1.4f);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
            case PLASTERS, PARACETAMOL, IBUPROFEN -> {
                if (!level.isClientSide) {
                    if (product == Product.PLASTERS) {
                        player.heal(4);
                    } else {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0));
                    }
                    stack.shrink(1);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
            default -> {
                return super.use(level, player, hand);
            }
        }
    }

    /** A scratchcard: most lose; some win £2, £5, £20, or (once in a long while) £100. */
    private static void scratch(Player player) {
        double r = player.getRandom().nextDouble();
        int win = r < 0.005 ? 10000 : r < 0.03 ? 2000 : r < 0.1 ? 500 : r < 0.3 ? 200 : 0;
        if (win == 0) {
            player.displayClientMessage(Component.literal("Not a winner this time.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CashItem.give(player, win, CashItem.DENOMINATIONS);
        player.displayClientMessage(Component.literal("You've won " + CashItem.money(win) + "!").withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
        if (who instanceof Player p && unpaid(stack)) {
            return stack;
        }
        return super.finishUsingItem(stack, level, who);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(PRICE)) {
            lines.add(Component.literal(CashItem.money(tag.getInt(PRICE))).withStyle(ChatFormatting.GOLD));
        }
        if (unpaid(stack)) {
            lines.add(Component.literal("Not paid for: take it to the till").withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------------ price tags

    /** A shop's stock: priced, unpaid, from that shop. */
    public static ItemStack priced(ItemStack stack, int pence, BlockPos shop) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(PRICE, pence);
        tag.putBoolean(UNPAID, true);
        tag.putLong(SHOP, shop.asLong());
        return stack;
    }

    public static boolean unpaid(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(UNPAID);
    }

    public static int price(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(PRICE) : 0;
    }

    /** Which shop's it is (the till's position), or null. */
    public static BlockPos shop(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(SHOP) ? BlockPos.of(stack.getTag().getLong(SHOP)) : null;
    }

    /** Paid for: the tags come off (so it stacks with the rest). */
    public static void paid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(PRICE);
            tag.remove(UNPAID);
            tag.remove(SHOP);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }
}
