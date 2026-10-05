package com.ptmuk.fare;

import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.PTM;
import com.rinventor.ptm2.core.init.ModSounds;
import com.rinventor.ptm2.dimension.virtual.util.PaymentUtils;
import com.rinventor.ptm2.engine.vehicle.ValidatorLocation;
import com.rinventor.ptm2.objects.entities.vehicle.Vehicle;
import com.rinventor.ptm2.objects.items.TransportCard;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pay-as-you-go bus fares on top of PTM2's economy. A tap costs the world's PTM2 single ticket
 * price; another tap within an hour is free (a hopper fare), whichever card paid the first one.
 *
 * <p>Cards: the Cube Card (stored credit, topped up at a machine), the Bus Pass (free travel),
 * PTM2's bank card (contactless, charged straight to the bank account) and PTM2's own transport
 * card (uses one of its rides, as PTM2's validators do).
 */
public final class Fares {
    /** How far from a reader a tap reaches, from the player's eyes, in blocks. */
    public static final double REACH = 2.0;
    /** Hopper: free taps for an hour of game ticks after a paid one. */
    public static final long HOPPER_TICKS = 72000;
    private static final String HOPPER_TAG = "ptmuk_hopper_until";

    public enum Card { CUBE_CARD, BUS_PASS, BANK_CARD, TRANSPORT_CARD }

    /** The kind of card this stack is, or null if it can't be tapped. */
    public static Card card(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(ModItems.CUBE_CARD.get())) {
            return Card.CUBE_CARD;
        }
        if (stack.is(ModItems.BUS_PASS.get())) {
            return Card.BUS_PASS;
        }
        if (stack.is(com.rinventor.ptm2.core.init.ModItems.BANK_CARD.get())) {
            return Card.BANK_CARD;
        }
        if (stack.is(com.rinventor.ptm2.core.init.ModItems.TRANSPORT_CARD.get())) {
            return Card.TRANSPORT_CARD;
        }
        return null;
    }

    /** One bus fare: the world's PTM2 single ticket price. */
    public static double fare(Level level) {
        try {
            return Math.max(0.0, PTM.DATA.get(level).PRC_TICKET);
        } catch (RuntimeException e) {
            return 1.75;
        }
    }

    /** An amount of money as PTM2 shows it, e.g. "30.00€" with the world's money label. */
    public static String money(Level level, double amount) {
        String label = "";
        try {
            label = PTM.DATA.get(level).MONEY_LABEL;
        } catch (RuntimeException ignored) {
        }
        return String.format(Locale.US, "%.2f", amount) + (label == null ? "" : label);
    }

    static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    /** The card reader of a PTM2 vehicle within reach of the player, or null. */
    public static Vec3 vehicleReader(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 best = null;
        double bestDist = REACH * REACH;
        for (Vehicle vehicle : player.level().getEntitiesOfClass(Vehicle.class, player.getBoundingBox().inflate(20))) {
            for (ValidatorLocation reader : vehicle.getValidators()) {
                if (!reader.isUpdated()) {
                    continue;
                }
                Vec3 at = new Vec3(reader.getX(), reader.getY(), reader.getZ());
                double d = at.distanceToSqr(eye);
                if (d < bestDist) {
                    bestDist = d;
                    best = at;
                }
            }
        }
        return best;
    }

    /**
     * Taps a card on a reader at {@code at} (server side). Charges the fare, plays the reader's
     * beep and tells the player what happened. Returns true if the card was accepted.
     */
    public static boolean tap(Player player, ItemStack stack, Vec3 at) {
        Level level = player.level();
        Card card = card(stack);
        if (card == null || level.isClientSide) {
            return false;
        }
        player.getCooldowns().addCooldown(stack.getItem(), 15);
        if (card == Card.TRANSPORT_CARD) {
            // PTM2's validator behaviour: one ride, with PTM2's own message and beep
            return TransportCard.useCard(player, 2);
        }
        if (card == Card.BUS_PASS) {
            return accept(player, at, Component.translatable("message.ptmuk.fare.pass_valid"));
        }
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(HOPPER_TAG) > now) {
            Component msg = Component.translatable("message.ptmuk.fare.hopper");
            if (card == Card.CUBE_CARD) {
                msg = Component.translatable("message.ptmuk.fare.hopper_balance", money(level, CubeCardItem.balance(stack)));
            }
            return accept(player, at, msg);
        }
        double fare = fare(level);
        if (card == Card.CUBE_CARD) {
            double balance = CubeCardItem.balance(stack);
            if (balance + 1e-6 < fare) {
                return refuse(player, at, Component.translatable("message.ptmuk.fare.no_credit", money(level, balance)));
            }
            CubeCardItem.setBalance(stack, round(balance - fare));
            startHopper(player);
            return accept(player, at, Component.translatable("message.ptmuk.fare.paid_card", money(level, fare),
                    money(level, CubeCardItem.balance(stack))));
        }
        // PTM2 bank card: contactless, straight from the bank account
        if (!PaymentUtils.hasIndefiniteMoney(player) && PaymentUtils.getBalance(player) + 1e-6 < fare) {
            return refuse(player, at, Component.translatable("message.ptmuk.fare.declined"));
        }
        PaymentUtils.pay(player, fare, Component.translatable("message.ptmuk.fare.bank_description").getString());
        startHopper(player);
        return accept(player, at, Component.translatable("message.ptmuk.fare.paid_contactless", money(level, fare)));
    }

    private static void startHopper(Player player) {
        player.getPersistentData().putLong(HOPPER_TAG, player.level().getGameTime() + HOPPER_TICKS);
    }

    private static boolean accept(Player player, Vec3 at, Component msg) {
        beep(player.level(), at, ModSounds.CARD_SUCCESS.get());
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private static boolean refuse(Player player, Vec3 at, Component msg) {
        beep(player.level(), at, ModSounds.CARD_FAIL.get());
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        return false;
    }

    static void beep(Level level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private Fares() {
    }
}
