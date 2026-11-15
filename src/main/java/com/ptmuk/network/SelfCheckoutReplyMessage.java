package com.ptmuk.network;

import com.ptmuk.shop.SelfCheckout;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: how paying at a self-checkout went. */
public record SelfCheckoutReplyMessage(SelfCheckout.Result result, long total, long change) {
    public static void encode(SelfCheckoutReplyMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.result);
        buf.writeLong(msg.total);
        buf.writeLong(msg.change);
    }

    public static SelfCheckoutReplyMessage decode(FriendlyByteBuf buf) {
        return new SelfCheckoutReplyMessage(buf.readEnum(SelfCheckout.Result.class), buf.readLong(), buf.readLong());
    }

    public static void handle(SelfCheckoutReplyMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.shop.SelfCheckoutScreen.reply(msg));
    }
}
