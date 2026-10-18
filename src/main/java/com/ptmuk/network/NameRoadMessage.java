package com.ptmuk.network;

import com.ptmuk.map.MapPlaces;
import com.ptmuk.road.RoadPlannerItem;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: name the road through (x, z) on the map (blank takes the name off). Creative or op only. */
public record NameRoadMessage(int x, int z, String name) {
    private static final int REACH = 1024;

    public static void encode(NameRoadMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x);
        buf.writeInt(msg.z);
        buf.writeUtf(msg.name, 40);
    }

    public static NameRoadMessage decode(FriendlyByteBuf buf) {
        return new NameRoadMessage(buf.readInt(), buf.readInt(), buf.readUtf(40));
    }

    public static void handle(NameRoadMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        if (!RoadPlannerItem.allowed(player)) {
            player.displayClientMessage(Component.literal("Naming roads needs creative mode (or a street nameplate)"), true);
            return;
        }
        if (Math.abs(msg.x - player.getX()) > REACH || Math.abs(msg.z - player.getZ()) > REACH) {
            return;
        }
        player.displayClientMessage(Component.literal(MapPlaces.nameRoad(player.serverLevel(), msg.x, msg.z, msg.name)), false);   // in chat: the map covers the action bar
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MapDataMessage(MapPlaces.gather(player)));
    }
}
