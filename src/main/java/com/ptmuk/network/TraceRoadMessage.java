package com.ptmuk.network;

import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.road.RoadTrace;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: follow the old road through this point (the answer is a {@link TraceResultMessage}). */
public record TraceRoadMessage(int x, int z) {
    /** How far from the player a road can be picked. */
    private static final int REACH = 1024;

    public static void encode(TraceRoadMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x);
        buf.writeInt(msg.z);
    }

    public static TraceRoadMessage decode(FriendlyByteBuf buf) {
        return new TraceRoadMessage(buf.readInt(), buf.readInt());
    }

    public static void handle(TraceRoadMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !RoadPlannerItem.allowed(player) || !(player.getMainHandItem().getItem() instanceof RoadPlannerItem
                || player.getOffhandItem().getItem() instanceof RoadPlannerItem)
                || Math.abs(msg.x - player.getX()) > REACH || Math.abs(msg.z - player.getZ()) > REACH) {
            return;
        }
        RoadTrace.Result r = RoadTrace.trace(player.serverLevel(), msg.x, msg.z);
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), r == null || r.x().length < 2
                ? new TraceResultMessage(new double[0], new double[0], new int[0], new boolean[0], 0)
                : new TraceResultMessage(r.x(), r.z(), r.width(), r.junction(), r.pavement()));
    }
}
