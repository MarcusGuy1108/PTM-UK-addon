package com.ptmuk.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: an old road followed down its middle (empty if there wasn't one). */
public record TraceResultMessage(double[] x, double[] z, int[] width, boolean[] junction, int pavement) {
    /** Two followings of 250 steps, and the start. */
    private static final int MAX_POINTS = 502;

    public static void encode(TraceResultMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.x.length);
        for (int i = 0; i < msg.x.length; i++) {
            buf.writeFloat((float) msg.x[i]);
            buf.writeFloat((float) msg.z[i]);
            buf.writeVarInt(msg.width[i]);
            buf.writeBoolean(msg.junction[i]);
        }
        buf.writeVarInt(msg.pavement);
    }

    public static TraceResultMessage decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_POINTS);
        double[] x = new double[n], z = new double[n];
        int[] w = new int[n];
        boolean[] j = new boolean[n];
        for (int i = 0; i < n; i++) {
            x[i] = buf.readFloat();
            z[i] = buf.readFloat();
            w[i] = buf.readVarInt();
            j[i] = buf.readBoolean();
        }
        return new TraceResultMessage(x, z, w, j, buf.readVarInt());
    }

    public static void handle(TraceResultMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.road.RoadPlannerScreen.traced(msg));
    }
}
