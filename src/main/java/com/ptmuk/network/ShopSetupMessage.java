package com.ptmuk.network;

import com.ptmuk.shop.ShopBlockEntity;
import com.ptmuk.shop.ShopKind;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: a shop set up by hand at its till (its kind, "" to work it out; its name; a new shopkeeper). */
public record ShopSetupMessage(BlockPos pos, String kind, String name, boolean newPerson) {
    public static void encode(ShopSetupMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.kind, 32);
        buf.writeUtf(msg.name, 48);
        buf.writeBoolean(msg.newPerson);
    }

    public static ShopSetupMessage decode(FriendlyByteBuf buf) {
        return new ShopSetupMessage(buf.readBlockPos(), buf.readUtf(32), buf.readUtf(48), buf.readBoolean());
    }

    public static void handle(ShopSetupMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || player.distanceToSqr(msg.pos.getCenter()) > 100 || !player.mayBuild()
                || !(player.level().getBlockEntity(msg.pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        ShopKind kind = null;
        for (ShopKind k : ShopKind.values()) {
            if (k.name().equals(msg.kind)) {
                kind = k;
            }
        }
        shop.configure(kind, msg.name, msg.newPerson);
    }
}
