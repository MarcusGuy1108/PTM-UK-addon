package com.ptmuk.network;

import com.ptmuk.building.PrefabPlacement;
import com.ptmuk.building.PrefabToolItem;
import com.ptmuk.building.schematic.CustomSchematics;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server: one piece of a schematic the player chose in the Prefab Tool's Custom tab
 * (read and converted on the client, gzipped, sent in pieces small enough for a packet). When the
 * last piece is in, the server keeps it for that player and the tool is set to place it.
 */
public record SchematicUploadMessage(int upload, int index, int total, String file, boolean offhand, byte[] data) {
    public static final int PIECE = 30000;

    public static void encode(SchematicUploadMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.upload);
        buf.writeVarInt(msg.index);
        buf.writeVarInt(msg.total);
        buf.writeUtf(msg.file, 256);
        buf.writeBoolean(msg.offhand);
        buf.writeByteArray(msg.data);
    }

    public static SchematicUploadMessage decode(FriendlyByteBuf buf) {
        return new SchematicUploadMessage(buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readBoolean(),
                buf.readByteArray(PIECE + 64));
    }

    public static void handle(SchematicUploadMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof PrefabToolItem) || !PrefabToolItem.allowed(player)) {
            return;
        }
        byte[] all = CustomSchematics.receive(player.getUUID(), msg.upload, msg.index, msg.total, msg.data);
        if (all == null) {
            return;
        }
        try {
            CustomSchematics.Loaded s = CustomSchematics.store(player.getUUID(), player.serverLevel(), all);
            CompoundTag c = new CompoundTag();
            c.putString("name", s.name());
            c.putString("file", msg.file);
            c.putInt("w", s.width());
            c.putInt("h", s.height());
            c.putInt("d", s.depth());
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(PrefabPlacement.TAG_ID, CustomSchematics.ID_PREFIX + s.name());
            tag.put(PrefabPlacement.TAG_CUSTOM, c);
            String note = s.missing() > 0 ? " - " + s.missing() + " kinds of block from mods that aren't installed are left out" : "";
            player.displayClientMessage(Component.literal("Ready to place " + s.name() + " (" + s.width() + " x " + s.depth() + ", "
                    + s.height() + " high)" + note), true);
        } catch (IOException | RuntimeException e) {
            player.displayClientMessage(Component.literal("Couldn't import " + msg.file + ": " + e.getMessage()), false);
        }
    }
}
