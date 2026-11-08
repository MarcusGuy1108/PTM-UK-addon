package com.ptmuk.network;

import com.ptmuk.building.Sections;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: the saved sections, for the Section Mover's screen. */
public record SectionListMessage(List<Sections.Info> sections) {
    public static void encode(SectionListMessage msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.sections, (b, s) -> {
            b.writeUtf(s.name(), 64);
            b.writeVarIntArray(new int[]{s.sx(), s.sy(), s.sz(), s.ax(), s.ay(), s.az()});
        });
    }

    public static SectionListMessage decode(FriendlyByteBuf buf) {
        return new SectionListMessage(buf.readList(b -> {
            String name = b.readUtf(64);
            int[] v = b.readVarIntArray(6);
            return new Sections.Info(name, v[0], v[1], v[2], v[3], v[4], v[5], BlockPos.ZERO);
        }));
    }

    public static void handle(SectionListMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ptmuk.client.building.SectionScreen.list(msg.sections));
    }
}
