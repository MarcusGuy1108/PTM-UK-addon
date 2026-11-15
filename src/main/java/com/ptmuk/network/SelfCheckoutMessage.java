package com.ptmuk.network;

import com.ptmuk.shop.SelfCheckout;
import com.ptmuk.shop.SelfCheckoutBlock;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: pay at a self-checkout, by card or cash. */
public record SelfCheckoutMessage(BlockPos pos, boolean card) {
    public static void encode(SelfCheckoutMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.card);
    }

    public static SelfCheckoutMessage decode(FriendlyByteBuf buf) {
        return new SelfCheckoutMessage(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(SelfCheckoutMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || player.distanceToSqr(msg.pos.getCenter()) > 64
                || !(player.level().getBlockState(msg.pos).getBlock() instanceof SelfCheckoutBlock)) {
            return;
        }
        SelfCheckout.Reply r = SelfCheckout.pay(player, msg.pos, msg.card);
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SelfCheckoutReplyMessage(r.result(), r.total(), r.change()));
    }
}
