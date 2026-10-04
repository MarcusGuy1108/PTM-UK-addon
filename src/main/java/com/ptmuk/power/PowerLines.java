package com.ptmuk.power;

import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.SyncPowerLinesMessage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

/** Overhead line spans between towers, per dimension. Synced to every player in it. */
public class PowerLines extends SavedData {
    /** A span between two towers, each given by its base centre, type and facing. */
    public record Link(BlockPos a, String typeA, Direction facingA, BlockPos b, String typeB, Direction facingB) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("a", a.asLong());
            t.putString("ta", typeA);
            t.putInt("fa", facingA.get2DDataValue());
            t.putLong("b", b.asLong());
            t.putString("tb", typeB);
            t.putInt("fb", facingB.get2DDataValue());
            return t;
        }

        static Link load(CompoundTag t) {
            return new Link(BlockPos.of(t.getLong("a")), t.getString("ta"), Direction.from2DDataValue(t.getInt("fa")),
                    BlockPos.of(t.getLong("b")), t.getString("tb"), Direction.from2DDataValue(t.getInt("fb")));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(a);
            buf.writeUtf(typeA);
            buf.writeByte(facingA.get2DDataValue());
            buf.writeBlockPos(b);
            buf.writeUtf(typeB);
            buf.writeByte(facingB.get2DDataValue());
        }

        public static Link read(FriendlyByteBuf buf) {
            return new Link(buf.readBlockPos(), buf.readUtf(), Direction.from2DDataValue(buf.readByte()),
                    buf.readBlockPos(), buf.readUtf(), Direction.from2DDataValue(buf.readByte()));
        }

        boolean touches(BlockPos p) {
            return a.equals(p) || b.equals(p);
        }
    }

    public static final int MAX_LINKS = 4096;
    public final List<Link> links = new ArrayList<>();

    public static PowerLines get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PowerLines::load, PowerLines::new, "ptmuk_power_lines");
    }

    private static PowerLines load(CompoundTag tag) {
        PowerLines lines = new PowerLines();
        for (Tag t : tag.getList("links", Tag.TAG_COMPOUND)) {
            lines.links.add(Link.load((CompoundTag) t));
        }
        return lines;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        links.forEach(l -> list.add(l.save()));
        tag.put("links", list);
        return tag;
    }

    public boolean add(ServerLevel level, Link link) {
        for (Link l : links) {
            if ((l.a.equals(link.a) && l.b.equals(link.b)) || (l.a.equals(link.b) && l.b.equals(link.a))) {
                return false;
            }
        }
        if (links.size() >= MAX_LINKS) {
            return false;
        }
        links.add(link);
        changed(level);
        return true;
    }

    public int removeAt(ServerLevel level, BlockPos origin) {
        int before = links.size();
        links.removeIf(l -> l.touches(origin));
        if (links.size() != before) {
            changed(level);
        }
        return before - links.size();
    }

    private void changed(ServerLevel level) {
        setDirty();
        PtmUkNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), new SyncPowerLinesMessage(links));
    }

    public static void sendTo(ServerPlayer player) {
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncPowerLinesMessage(get(player.serverLevel()).links));
    }
}
