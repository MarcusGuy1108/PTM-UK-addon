package com.ptmuk.network;

import com.ptmuk.road.RoadMapTiles;
import com.ptmuk.road.RoadPlannerItem;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: the Road Planner was opened here; send the map round it (RoadMapTiles). */
public record RoadMapRequestMessage(int x, int z, int radiusChunks) {
    public static void encode(RoadMapRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x);
        buf.writeInt(msg.z);
        buf.writeVarInt(msg.radiusChunks);
    }

    public static RoadMapRequestMessage decode(FriendlyByteBuf buf) {
        return new RoadMapRequestMessage(buf.readInt(), buf.readInt(), buf.readVarInt());
    }

    public static void handle(RoadMapRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !RoadPlannerItem.allowed(player)) {
            return;
        }
        // only round the player: the map can't be used to look at distant parts of a server
        if (Math.abs(msg.x - player.getBlockX()) > 64 || Math.abs(msg.z - player.getBlockZ()) > 64) {
            return;
        }
        RoadMapTiles.request(player, msg.x, msg.z, msg.radiusChunks);
    }
}
