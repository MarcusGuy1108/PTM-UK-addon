package com.ptmuk.network;

import com.ptmuk.building.PrefabToolItem;
import com.ptmuk.building.SectionToolItem;
import com.ptmuk.building.Sections;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Client -> server: what the Section Mover does (corners, copy, pick, turn, paste, undo, delete, list). */
public record SectionMessage(Action action, String name, BlockPos pos, boolean offhand) {
    public enum Action { CORNER, COPY, PICK, ROTATE, MIRROR, KEEP_AIR, PASTE, UNDO, DELETE, LIST, CLEAR }

    /** How far away a corner or a paste can be aimed (the client aims up to 128 blocks). */
    private static final double MAX_DISTANCE = 160;

    public static void encode(SectionMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.name == null ? "" : msg.name, 64);
        buf.writeBlockPos(msg.pos == null ? BlockPos.ZERO : msg.pos);
        buf.writeBoolean(msg.offhand);
    }

    public static SectionMessage decode(FriendlyByteBuf buf) {
        return new SectionMessage(buf.readEnum(Action.class), buf.readUtf(64), buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(SectionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof SectionToolItem)) {
            return;
        }
        if (!PrefabToolItem.allowed(player)) {
            player.displayClientMessage(Component.literal("The Section Mover works in creative mode only"), true);
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        boolean near = player.distanceToSqr(msg.pos.getCenter()) <= MAX_DISTANCE * MAX_DISTANCE;
        switch (msg.action) {
            case CORNER -> {
                if (!near) {
                    return;
                }
                String key = tag.getInt(SectionToolItem.NEXT) == 0 ? SectionToolItem.C1 : SectionToolItem.C2;
                tag.putLong(key, msg.pos.asLong());
                tag.putInt(SectionToolItem.NEXT, 1 - tag.getInt(SectionToolItem.NEXT));
                BlockPos c1 = SectionToolItem.corner(stack, SectionToolItem.C1), c2 = SectionToolItem.corner(stack, SectionToolItem.C2);
                player.displayClientMessage(Component.literal(key.equals(SectionToolItem.C1) ? "First corner set (the section is pasted from here)"
                        : "Second corner set: " + (Math.abs(c1.getX() - c2.getX()) + 1) + " x " + (Math.abs(c1.getZ() - c2.getZ()) + 1)
                        + ". Sneak + right-click to copy it"), true);
            }
            case CLEAR -> {
                tag.remove(SectionToolItem.C1);
                tag.remove(SectionToolItem.C2);
                tag.putInt(SectionToolItem.NEXT, 0);
                tag.putString(SectionToolItem.PICKED, "");
            }
            case COPY -> {
                String name = Sections.clean(msg.name);
                BlockPos c1 = SectionToolItem.corner(stack, SectionToolItem.C1), c2 = SectionToolItem.corner(stack, SectionToolItem.C2);
                if (name == null) {
                    player.displayClientMessage(Component.literal("Give the section a name (letters and numbers)"), true);
                } else if (c1 == null || c2 == null) {
                    player.displayClientMessage(Component.literal("Right-click both corners first"), true);
                } else if (Math.abs(c1.getX() - c2.getX()) >= Sections.MAX_SIDE || Math.abs(c1.getZ() - c2.getZ()) >= Sections.MAX_SIDE) {
                    player.displayClientMessage(Component.literal("That's too big: up to " + Sections.MAX_SIDE + " blocks each way"), true);
                } else {
                    say(player, Sections.copy(player.serverLevel(), player.getUUID(), name, c1, c2));
                }
            }
            case PICK -> {
                Sections.Info s = Sections.info(Sections.clean(msg.name) == null ? "" : Sections.clean(msg.name));
                tag.putString(SectionToolItem.PICKED, s == null ? "" : s.name());
                if (s != null) {
                    tag.putIntArray(SectionToolItem.SIZE, new int[]{s.sx(), s.sy(), s.sz(), s.ax(), s.ay(), s.az()});
                }
            }
            case ROTATE -> tag.putInt(SectionToolItem.ROT, Math.floorMod(tag.getInt(SectionToolItem.ROT) + 1, 4));
            case MIRROR -> tag.putBoolean(SectionToolItem.MIRROR, !tag.getBoolean(SectionToolItem.MIRROR));
            case KEEP_AIR -> tag.putBoolean(SectionToolItem.KEEP_AIR, !tag.getBoolean(SectionToolItem.KEEP_AIR));
            case PASTE -> {
                Sections.Info s = Sections.info(tag.getString(SectionToolItem.PICKED));
                if (s == null) {
                    player.displayClientMessage(Component.literal("Pick a section to paste first (sneak + right-click)"), true);
                } else if (near) {
                    say(player, Sections.paste(player.serverLevel(), player.getUUID(), s, msg.pos, SectionToolItem.rotation(stack),
                            SectionToolItem.mirror(stack), tag.getBoolean(SectionToolItem.KEEP_AIR)));
                }
            }
            case UNDO -> say(player, Sections.undo(player.serverLevel(), player.getUUID()));
            case DELETE -> {
                String name = Sections.clean(msg.name);
                if (name != null && Sections.delete(name)) {
                    player.displayClientMessage(Component.literal("Deleted " + name), true);
                    if (name.equals(tag.getString(SectionToolItem.PICKED))) {
                        tag.putString(SectionToolItem.PICKED, "");
                    }
                }
            }
            case LIST -> { }
        }
        if (msg.action == SectionMessage.Action.LIST || msg.action == SectionMessage.Action.DELETE) {
            PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SectionListMessage(Sections.list()));
        }
    }

    private static void say(ServerPlayer player, String what) {
        if (what != null) {
            player.displayClientMessage(Component.literal(what), true);
        }
    }
}
