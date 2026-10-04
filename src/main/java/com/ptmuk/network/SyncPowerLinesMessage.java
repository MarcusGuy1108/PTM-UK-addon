package com.ptmuk.network;

import com.ptmuk.client.power.ClientPowerLines;
import com.ptmuk.power.PowerLines;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Every power line span in the player's dimension. */
public record SyncPowerLinesMessage(List<PowerLines.Link> links) {
    public static void encode(SyncPowerLinesMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.links.size());
        msg.links.forEach(l -> l.write(buf));
    }

    public static SyncPowerLinesMessage decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), PowerLines.MAX_LINKS);
        List<PowerLines.Link> links = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            links.add(PowerLines.Link.read(buf));
        }
        return new SyncPowerLinesMessage(links);
    }

    public static void handle(SyncPowerLinesMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPowerLines.set(msg.links));
        ctx.get().setPacketHandled(true);
    }
}
