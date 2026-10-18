package com.ptmuk.network;

import com.ptmuk.home.InteriorDesignerItem;
import com.ptmuk.home.RoomDesigner;
import com.ptmuk.road.RoadPlannerItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: furnish the room on this floor as room type `room`, or (room -1) undo the last one. */
public record DesignRoomMessage(BlockPos floor, int room) {
    private static final double REACH = 16;

    public static void encode(DesignRoomMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.floor);
        buf.writeInt(msg.room);
    }

    public static DesignRoomMessage decode(FriendlyByteBuf buf) {
        return new DesignRoomMessage(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(DesignRoomMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !RoadPlannerItem.allowed(player) || !(player.getMainHandItem().getItem() instanceof InteriorDesignerItem
                || player.getOffhandItem().getItem() instanceof InteriorDesignerItem)) {
            return;
        }
        if (msg.room < 0) {
            RoomDesigner.undo(player);
        } else if (msg.room < RoomDesigner.Room.values().length && player.position().distanceTo(msg.floor.getCenter()) <= REACH) {
            RoomDesigner.design(player, msg.floor, RoomDesigner.Room.values()[msg.room]);
        }
    }
}
