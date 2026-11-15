package com.ptmuk.network;

import com.ptmuk.shop.Product;
import com.ptmuk.shop.SelfCheckout;
import com.ptmuk.shop.SelfCheckoutBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: a touch on a self-checkout's menu (scan a thing from your inventory, look one up, bags, pay...). */
public record SelfCheckoutMessage(BlockPos pos, Action action, int arg) {
    public enum Action { SCAN_SLOT, SCAN_ALL, EXTRA_ADD, EXTRA_REMOVE, BAGS, RECEIPT, HELP, PAY_CARD, PAY_CASH, CANCEL }

    public static void encode(SelfCheckoutMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.arg);
    }

    public static SelfCheckoutMessage decode(FriendlyByteBuf buf) {
        return new SelfCheckoutMessage(buf.readBlockPos(), buf.readEnum(Action.class), buf.readVarInt());
    }

    public static void handle(SelfCheckoutMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || player.distanceToSqr(msg.pos.getCenter()) > 64
                || !(player.level().getBlockEntity(msg.pos) instanceof SelfCheckoutBlockEntity till)) {
            return;
        }
        Product product = msg.arg >= 0 && msg.arg < Product.values().length ? Product.values()[msg.arg] : null;
        switch (msg.action) {
            case SCAN_SLOT -> {
                if (msg.arg >= 0 && msg.arg < player.getInventory().items.size()) {
                    ItemStack s = player.getInventory().items.get(msg.arg);
                    till.scan(player, s);
                }
            }
            case SCAN_ALL -> till.scanAll(player);
            case EXTRA_ADD -> {
                if (product != null) {
                    till.extra(player, product, 1);
                }
            }
            case EXTRA_REMOVE -> {
                if (product != null) {
                    till.extra(player, product, -1);
                }
            }
            case BAGS -> till.bags(player, msg.arg > 0 ? 1 : -1);
            case RECEIPT -> till.toggleReceipt(player);
            case HELP -> till.help(player);
            case PAY_CARD, PAY_CASH -> {
                SelfCheckout.Reply r = till.pay(player, msg.action == Action.PAY_CARD);
                PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SelfCheckoutReplyMessage(r.result(), r.total(), r.change()));
            }
            case CANCEL -> till.cancel(player);
        }
    }
}
