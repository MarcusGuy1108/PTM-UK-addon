package com.ptmuk.network;

import com.ptmuk.map.MapData;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: what's on the map round the player. */
public record MapDataMessage(MapData data) {
    public static void encode(MapDataMessage msg, FriendlyByteBuf buf) {
        msg.data.write(buf);
    }

    public static MapDataMessage decode(FriendlyByteBuf buf) {
        return new MapDataMessage(MapData.read(buf));
    }

    public static void handle(MapDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.map.MapClient.received(msg.data));
    }
}
