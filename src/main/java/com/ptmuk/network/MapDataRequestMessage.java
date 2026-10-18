package com.ptmuk.network;

import com.ptmuk.map.MapPlaces;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: send me what's on the map round me (shops, stops, named roads, buses...). */
public record MapDataRequestMessage() {
    public static void encode(MapDataRequestMessage msg, FriendlyByteBuf buf) {
    }

    public static MapDataRequestMessage decode(FriendlyByteBuf buf) {
        return new MapDataRequestMessage();
    }

    public static void handle(MapDataRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MapDataMessage(MapPlaces.gather(player)));
        }
    }
}
