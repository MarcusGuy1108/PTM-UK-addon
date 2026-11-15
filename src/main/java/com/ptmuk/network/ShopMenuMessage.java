package com.ptmuk.network;

import com.ptmuk.shop.ShopBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: a counter's menu (or today's rails), to order from. */
public record ShopMenuMessage(BlockPos pos, String shop, String keeper, List<ShopBlockEntity.Line> offers) {
    public static void encode(ShopMenuMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.shop, 64);
        buf.writeUtf(msg.keeper, 32);
        buf.writeVarInt(msg.offers.size());
        for (ShopBlockEntity.Line l : msg.offers) {
            buf.writeUtf(l.title(), 64);
            buf.writeVarInt(l.pence());
            buf.writeItem(l.give());
        }
    }

    public static ShopMenuMessage decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String shop = buf.readUtf(64), keeper = buf.readUtf(32);
        int n = Math.min(32, buf.readVarInt());
        List<ShopBlockEntity.Line> offers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            offers.add(new ShopBlockEntity.Line(buf.readUtf(64), buf.readVarInt(), buf.readItem()));
        }
        return new ShopMenuMessage(pos, shop, keeper, offers);
    }

    public static void handle(ShopMenuMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.shop.OrderScreen.open(msg));
    }
}
