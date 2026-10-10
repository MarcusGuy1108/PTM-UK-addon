package com.ptmuk.network;

import com.ptmuk.building.PrefabCatalog;
import com.ptmuk.building.PrefabPlacement;
import com.ptmuk.building.PrefabPlacer;
import com.ptmuk.building.PrefabToolItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: choose, turn, mirror, place or undo with the Prefab Tool. */
public record PrefabToolMessage(Action action, String id, BlockPos target, Direction look, boolean offhand) {
    public enum Action { SELECT, ROTATE, MIRROR, PLACE, UNDO }

    /** How far away a building can be placed (the client aims up to 128 blocks). */
    private static final double MAX_DISTANCE = 160;

    public static void encode(PrefabToolMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.id == null ? "" : msg.id, 64);
        buf.writeBlockPos(msg.target == null ? BlockPos.ZERO : msg.target);
        buf.writeEnum(msg.look == null ? Direction.NORTH : msg.look);
        buf.writeBoolean(msg.offhand);
    }

    public static PrefabToolMessage decode(FriendlyByteBuf buf) {
        return new PrefabToolMessage(buf.readEnum(Action.class), buf.readUtf(64), buf.readBlockPos(), buf.readEnum(Direction.class),
                buf.readBoolean());
    }

    public static void handle(PrefabToolMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof PrefabToolItem)) {
            return;
        }
        if (!PrefabToolItem.allowed(player)) {
            player.displayClientMessage(Component.literal("The Prefab Tool works in creative mode only"), true);
            return;
        }
        switch (msg.action) {
            case SELECT -> {
                if (PrefabCatalog.get(msg.id) != null) {
                    stack.getOrCreateTag().putString(PrefabPlacement.TAG_ID, msg.id);
                }
            }
            case ROTATE -> {
                int turns = (PrefabPlacement.turns(stack) + 1) % 4;
                stack.getOrCreateTag().putInt(PrefabPlacement.TAG_ROT, turns);
                player.displayClientMessage(Component.literal("Turned " + turns * 90 + " degrees"), true);
            }
            case MIRROR -> stack.getOrCreateTag().putBoolean(PrefabPlacement.TAG_MIRROR, !PrefabPlacement.mirrored(stack));
            case UNDO -> PrefabPlacer.undo(player);
            case PLACE -> {
                PrefabCatalog.Entry entry = PrefabPlacement.selected(stack);
                if (entry == null || msg.target == null || msg.look.getAxis().isVertical()
                        || player.distanceToSqr(msg.target.getCenter()) > MAX_DISTANCE * MAX_DISTANCE) {
                    return;
                }
                PrefabPlacer.place(player, PrefabPlacement.of(entry, msg.target, msg.look, PrefabPlacement.turns(stack),
                        PrefabPlacement.mirrored(stack)));
            }
        }
    }
}
