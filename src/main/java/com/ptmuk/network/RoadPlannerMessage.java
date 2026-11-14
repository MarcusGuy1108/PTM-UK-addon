package com.ptmuk.network;

import com.ptmuk.road.RoadBuilder;
import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.road.RoadPlans;
import com.ptmuk.road.plan.RoadPlan;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server: save the plan on the Road Planner, build it, or undo the last build; or the
 * map's buildings round you, sent just before the plan to build them with it. A big plan comes
 * in several of these (part 0 of parts first), put back together before it's used.
 */
public record RoadPlannerMessage(Action action, int part, int parts, byte[] plan, boolean offhand) {
    public enum Action { SAVE, BUILD, UNDO, BUILDINGS }

    private static final int MAX_PARTS = RoadPlans.MAX_PLAN / RoadPlans.MAX_BYTES + 1;
    /** A big map's buildings can come to more than a plan. */
    private static final int MAX_BUILDING_PARTS = com.ptmuk.road.MapBuildings.MAX_PACKED / RoadPlans.MAX_BYTES + 1;

    /** A plan coming in pieces: what's come so far. */
    private record Incoming(Action action, int parts, ByteArrayOutputStream data) {
    }

    private static final Map<UUID, Incoming> INCOMING = new HashMap<>();

    /** Sends the action and its plan in as many pieces as it takes (client side). */
    public static void send(Action action, byte[] plan, boolean offhand) {
        int parts = Math.max(1, (plan.length + RoadPlans.MAX_BYTES - 1) / RoadPlans.MAX_BYTES);
        for (int i = 0; i < parts; i++) {
            byte[] piece = Arrays.copyOfRange(plan, i * RoadPlans.MAX_BYTES, Math.min(plan.length, (i + 1) * RoadPlans.MAX_BYTES));
            PtmUkNetwork.CHANNEL.sendToServer(new RoadPlannerMessage(action, i, parts, piece, offhand));
        }
    }

    public static void encode(RoadPlannerMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.part);
        buf.writeVarInt(msg.parts);
        buf.writeByteArray(msg.plan == null ? new byte[0] : msg.plan);
        buf.writeBoolean(msg.offhand);
    }

    public static RoadPlannerMessage decode(FriendlyByteBuf buf) {
        return new RoadPlannerMessage(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(RoadPlans.MAX_BYTES + 1024), buf.readBoolean());
    }

    public static void handle(RoadPlannerMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || msg.parts < 1 || msg.parts > (msg.action == Action.BUILDINGS ? MAX_BUILDING_PARTS : MAX_PARTS)
                || msg.part < 0 || msg.part >= msg.parts) {
            return;
        }
        // pieces arrive in order on one connection: start afresh on the first, drop strays
        Incoming in = msg.part == 0 ? new Incoming(msg.action, msg.parts, new ByteArrayOutputStream()) : INCOMING.get(player.getUUID());
        if (in == null || in.action != msg.action || in.parts != msg.parts || in.data.size() != msg.part * RoadPlans.MAX_BYTES) {
            INCOMING.remove(player.getUUID());
            return;
        }
        in.data.writeBytes(msg.plan);
        if (msg.part < msg.parts - 1) {
            INCOMING.put(player.getUUID(), in);
            return;
        }
        INCOMING.remove(player.getUUID());
        byte[] data = in.data.toByteArray();
        InteractionHand hand = msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RoadPlannerItem)) {
            return;
        }
        if (!RoadPlannerItem.allowed(player)) {
            player.displayClientMessage(Component.literal("The Road Planner works in creative mode only"), true);
            return;
        }
        switch (msg.action) {
            case SAVE -> RoadPlans.write(stack, RoadPlans.unpack(data));
            case BUILD -> {
                RoadPlan plan = RoadPlans.unpack(data);
                RoadPlans.write(stack, plan);
                RoadBuilder.start(player, hand, plan);
            }
            case UNDO -> RoadBuilder.undo(player);
            case BUILDINGS -> com.ptmuk.road.MapBuildings.receive(player.getUUID(), data);
        }
    }
}
