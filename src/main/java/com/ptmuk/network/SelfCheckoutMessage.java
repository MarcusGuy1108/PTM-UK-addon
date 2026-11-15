package com.ptmuk.network;

import com.ptmuk.shop.SelfCheckout;
import com.ptmuk.shop.SelfCheckoutBlock;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: pay at a self-checkout, by card or cash: with the loose things looked up (product ordinals, how many), bags, a receipt or not. */
public record SelfCheckoutMessage(BlockPos pos, boolean card, int[] extraIds, int[] extraCounts, int bags, boolean receipt) {
    public static void encode(SelfCheckoutMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.card);
        buf.writeVarIntArray(msg.extraIds);
        buf.writeVarIntArray(msg.extraCounts);
        buf.writeVarInt(msg.bags);
        buf.writeBoolean(msg.receipt);
    }

    public static SelfCheckoutMessage decode(FriendlyByteBuf buf) {
        return new SelfCheckoutMessage(buf.readBlockPos(), buf.readBoolean(), buf.readVarIntArray(64), buf.readVarIntArray(64), buf.readVarInt(),
                buf.readBoolean());
    }

    public static void handle(SelfCheckoutMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || player.distanceToSqr(msg.pos.getCenter()) > 64
                || !(player.level().getBlockState(msg.pos).getBlock() instanceof SelfCheckoutBlock)) {
            return;
        }
        SelfCheckout.Reply r = SelfCheckout.pay(player, msg.pos, msg.card, msg.extraIds, msg.extraCounts, msg.bags, msg.receipt);
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SelfCheckoutReplyMessage(r.result(), r.total(), r.change()));
    }
}
