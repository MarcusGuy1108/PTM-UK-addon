package com.ptmuk.network;

import com.ptmuk.money.Atm;
import com.ptmuk.money.AtmBlock;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: a step at a cash machine (card in, PIN, withdraw...); the answer comes back as an AtmReplyMessage. */
public record AtmMessage(BlockPos pos, Atm.Action action, String pin, long amount, boolean receipt) {
    public static void encode(AtmMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.pin, 8);
        buf.writeLong(msg.amount);
        buf.writeBoolean(msg.receipt);
    }

    public static AtmMessage decode(FriendlyByteBuf buf) {
        return new AtmMessage(buf.readBlockPos(), buf.readEnum(Atm.Action.class), buf.readUtf(8), buf.readLong(), buf.readBoolean());
    }

    public static void handle(AtmMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || player.distanceToSqr(msg.pos.getCenter()) > 64
                || !(player.level().getBlockState(msg.pos).getBlock() instanceof AtmBlock)) {
            return;
        }
        Atm.Reply r = Atm.handle(player, msg.pos, msg.action, msg.pin, msg.amount, msg.receipt);
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AtmReplyMessage(r.action(), r.status(), r.balance(), r.n()));
    }
}
