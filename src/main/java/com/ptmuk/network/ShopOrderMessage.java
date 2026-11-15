package com.ptmuk.network;

import com.ptmuk.shop.ShopBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: an order at a counter (how many of each thing on its menu). */
public record ShopOrderMessage(BlockPos pos, int[] quantities) {
    public static void encode(ShopOrderMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarIntArray(msg.quantities);
    }

    public static ShopOrderMessage decode(FriendlyByteBuf buf) {
        return new ShopOrderMessage(buf.readBlockPos(), buf.readVarIntArray(32));
    }

    public static void handle(ShopOrderMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.distanceToSqr(msg.pos.getCenter()) <= 100
                && player.level().getBlockEntity(msg.pos) instanceof ShopBlockEntity shop) {
            shop.order(player, msg.quantities);
        }
    }
}
