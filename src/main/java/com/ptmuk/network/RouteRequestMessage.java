package com.ptmuk.network;

import com.ptmuk.map.RouteFinder;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: directions from where I am to (x, z), called `name`. */
public record RouteRequestMessage(double x, double z, String name) {
    private static final double REACH = 4096;

    public static void encode(RouteRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.z);
        buf.writeUtf(msg.name, 64);
    }

    public static RouteRequestMessage decode(FriendlyByteBuf buf) {
        return new RouteRequestMessage(buf.readDouble(), buf.readDouble(), buf.readUtf(64));
    }

    public static void handle(RouteRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Math.abs(msg.x - player.getX()) <= REACH && Math.abs(msg.z - player.getZ()) <= REACH) {
            RouteFinder.request(player, msg.x, msg.z, msg.name);
        }
    }
}
