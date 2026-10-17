package com.ptmuk.network;

import com.ptmuk.road.RoadBuilder;
import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.road.RoadPlans;
import com.ptmuk.road.plan.RoadPlan;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: save the plan on the Road Planner, build it, or undo the last build. */
public record RoadPlannerMessage(Action action, byte[] plan, boolean offhand) {
    public enum Action { SAVE, BUILD, UNDO }

    public static void encode(RoadPlannerMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeByteArray(msg.plan == null ? new byte[0] : msg.plan);
        buf.writeBoolean(msg.offhand);
    }

    public static RoadPlannerMessage decode(FriendlyByteBuf buf) {
        return new RoadPlannerMessage(buf.readEnum(Action.class), buf.readByteArray(RoadPlans.MAX_BYTES + 1024), buf.readBoolean());
    }

    public static void handle(RoadPlannerMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
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
            case SAVE -> RoadPlans.write(stack, RoadPlans.unpack(msg.plan));
            case BUILD -> {
                RoadPlan plan = RoadPlans.unpack(msg.plan);
                RoadPlans.write(stack, plan);
                RoadBuilder.start(player, hand, plan);
            }
            case UNDO -> RoadBuilder.undo(player);
        }
    }
}
