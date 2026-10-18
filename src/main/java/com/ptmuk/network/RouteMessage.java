package com.ptmuk.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: the way there (points along the roads and the turns), or why there isn't one. */
public record RouteMessage(float[] x, float[] z, List<Step> steps, String error, String destination) {
    /** What to do at point `index` of the route. */
    public record Step(int index, String text) {
    }

    public static RouteMessage none(String why) {
        return new RouteMessage(new float[0], new float[0], List.of(), why, "");
    }

    public static void encode(RouteMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.x.length);
        for (int i = 0; i < msg.x.length; i++) {
            buf.writeFloat(msg.x[i]);
            buf.writeFloat(msg.z[i]);
        }
        buf.writeCollection(msg.steps, (b, s) -> {
            b.writeVarInt(s.index());
            b.writeUtf(s.text(), 128);
        });
        buf.writeUtf(msg.error, 128);
        buf.writeUtf(msg.destination, 64);
    }

    public static RouteMessage decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 20000);
        float[] x = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = buf.readFloat();
            z[i] = buf.readFloat();
        }
        List<Step> steps = new ArrayList<>(buf.readList(b -> new Step(b.readVarInt(), b.readUtf(128))));
        return new RouteMessage(x, z, steps, buf.readUtf(128), buf.readUtf(64));
    }

    public static void handle(RouteMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.map.Navigation.received(msg));
    }
}
