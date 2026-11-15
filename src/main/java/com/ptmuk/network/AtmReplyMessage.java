package com.ptmuk.network;

import com.ptmuk.money.Atm;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: how a cash machine step went (to the machine's screen, if it's open). */
public record AtmReplyMessage(Atm.Action action, Atm.Status status, long balance, long n) {
    public static void encode(AtmReplyMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeEnum(msg.status);
        buf.writeLong(msg.balance);
        buf.writeLong(msg.n);
    }

    public static AtmReplyMessage decode(FriendlyByteBuf buf) {
        return new AtmReplyMessage(buf.readEnum(Atm.Action.class), buf.readEnum(Atm.Status.class), buf.readLong(), buf.readLong());
    }

    public static void handle(AtmReplyMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.money.AtmScreen.reply(msg));
    }
}
