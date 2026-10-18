package com.ptmuk.network;

import com.ptmuk.road.BlockSwap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: the blocks in a block swap box and how many of each, with a note on what just happened. */
public record BlockSwapListMessage(String note, List<String> ids, int[] counts) {
    public static void encode(BlockSwapListMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.note);
        buf.writeCollection(msg.ids, FriendlyByteBuf::writeUtf);
        buf.writeVarIntArray(msg.counts);
    }

    public static BlockSwapListMessage decode(FriendlyByteBuf buf) {
        return new BlockSwapListMessage(buf.readUtf(256), buf.readCollection(n -> new ArrayList<>(Math.min(n, BlockSwap.MAX_KINDS)),
                b -> b.readUtf(256)), buf.readVarIntArray(BlockSwap.MAX_KINDS));
    }

    public static void handle(BlockSwapListMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.road.RoadPlannerScreen.swapList(msg));
    }
}
