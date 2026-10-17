package com.ptmuk.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: one chunk of the Road Planner's map, its colours and ground heights. */
public record RoadMapTileMessage(int cx, int cz, int[] colours, short[] ground) {
    public static void encode(RoadMapTileMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.cx);
        buf.writeInt(msg.cz);
        for (int i = 0; i < 256; i++) {
            buf.writeInt(msg.colours[i]);
            buf.writeShort(msg.ground[i]);
        }
    }

    public static RoadMapTileMessage decode(FriendlyByteBuf buf) {
        int cx = buf.readInt(), cz = buf.readInt();
        int[] colours = new int[256];
        short[] ground = new short[256];
        for (int i = 0; i < 256; i++) {
            colours[i] = buf.readInt();
            ground[i] = buf.readShort();
        }
        return new RoadMapTileMessage(cx, cz, colours, ground);
    }

    public static void handle(RoadMapTileMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.road.RoadPlannerScreen.mapTile(msg));
    }
}
