package com.ptmuk.network;

import com.ptmuk.building.PrefabToolItem;
import com.ptmuk.building.SectionToolItem;
import com.ptmuk.building.Sections;
import com.ptmuk.road.RoadPlannerItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Client -> server: copying and pasting sections from the Road Planner's map, by x and z alone:
 * a box to copy (its first corner is where it's pasted from) or a point to paste at, on the
 * ground there or at the height it was copied from.
 */
public record SectionMapMessage(Action action, String name, int x0, int z0, int x1, int z1, int turns, boolean mirror,
        boolean keepAir, boolean keepHeight, boolean offhand) {
    public enum Action { LIST, COPY, PASTE, UNDO, DELETE }

    public static void encode(SectionMapMessage m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeUtf(m.name == null ? "" : m.name, 64);
        buf.writeVarIntArray(new int[]{m.x0, m.z0, m.x1, m.z1, m.turns});
        buf.writeBoolean(m.mirror);
        buf.writeBoolean(m.keepAir);
        buf.writeBoolean(m.keepHeight);
        buf.writeBoolean(m.offhand);
    }

    public static SectionMapMessage decode(FriendlyByteBuf buf) {
        Action a = buf.readEnum(Action.class);
        String name = buf.readUtf(64);
        int[] v = buf.readVarIntArray(5);
        return new SectionMapMessage(a, name, v[0], v[1], v[2], v[3], v[4], buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    /** The top of the ground (or whatever's built on it, leaves aside) at x, z. */
    static int ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    public static void handle(SectionMapMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        Item held = player.getItemInHand(msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND).getItem();
        if (!(held instanceof RoadPlannerItem) && !(held instanceof SectionToolItem)) {
            return;
        }
        if (!PrefabToolItem.allowed(player)) {
            player.displayClientMessage(Component.literal("Copying and pasting sections works in creative mode only"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        String said = null;
        switch (msg.action) {
            case COPY -> {
                String name = Sections.clean(msg.name);
                if (name == null) {
                    said = "Give the section a name (letters and numbers)";
                } else if (Math.abs(msg.x1 - msg.x0) >= Sections.MAX_SIDE || Math.abs(msg.z1 - msg.z0) >= Sections.MAX_SIDE) {
                    said = "That's too big: up to " + Sections.MAX_SIDE + " blocks each way";
                } else {
                    said = Sections.copy(level, player.getUUID(), name, new BlockPos(msg.x0, ground(level, msg.x0, msg.z0), msg.z0),
                            new BlockPos(msg.x1, ground(level, msg.x1, msg.z1), msg.z1));
                    if (said == null) {
                        said = "Copying " + name + "...";
                    }
                }
            }
            case PASTE -> {
                String name = Sections.clean(msg.name);
                Sections.Info s = name == null ? null : Sections.info(name);
                if (s == null) {
                    said = "Pick a section to paste";
                } else {
                    int y = msg.keepHeight ? s.origin().getY() + s.ay() : ground(level, msg.x0, msg.z0);
                    said = Sections.paste(level, player.getUUID(), s, new BlockPos(msg.x0, y, msg.z0), Rotation.values()[Math.floorMod(msg.turns, 4)],
                            msg.mirror ? Mirror.FRONT_BACK : Mirror.NONE, msg.keepAir);
                    if (said == null) {
                        said = "Pasting " + name + "...";
                    }
                }
            }
            case UNDO -> said = Sections.undo(level, player.getUUID());
            case DELETE -> {
                String name = Sections.clean(msg.name);
                said = name != null && Sections.delete(name) ? "Deleted " + name : null;
            }
            case LIST -> { }
        }
        if (said != null) {
            player.displayClientMessage(Component.literal(said), true);
        }
        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SectionListMessage(Sections.list()));
    }
}
