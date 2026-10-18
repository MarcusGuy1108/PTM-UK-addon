package com.ptmuk.network;

import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.town.TownBuilder;
import com.ptmuk.town.TownBuilderItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: choose what the Town Builder puts down, put it down at a clicked block, or undo. */
public record TownBuilderMessage(Action action, String kind, int size, boolean scatter, int spacing, BlockPos pos, Direction face,
                                 boolean offhand) {
    public enum Action { CHOOSE, USE, UNDO }

    /** The client aims up to 128 blocks. */
    private static final double MAX_DISTANCE = 160;

    public static void encode(TownBuilderMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.kind, 64);
        buf.writeInt(msg.size);
        buf.writeBoolean(msg.scatter);
        buf.writeInt(msg.spacing);
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.face);
        buf.writeBoolean(msg.offhand);
    }

    public static TownBuilderMessage decode(FriendlyByteBuf buf) {
        return new TownBuilderMessage(buf.readEnum(Action.class), buf.readUtf(64), buf.readInt(), buf.readBoolean(), buf.readInt(),
                buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean());
    }

    public static void handle(TownBuilderMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !RoadPlannerItem.allowed(player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof TownBuilderItem)) {
            return;
        }
        switch (msg.action) {
            case CHOOSE -> TownBuilder.choose(stack, msg.kind, msg.size, msg.scatter, msg.spacing);
            case UNDO -> TownBuilder.undo(player);
            case USE -> {
                if (player.distanceToSqr(msg.pos.getCenter()) <= MAX_DISTANCE * MAX_DISTANCE && player.serverLevel().isLoaded(msg.pos)) {
                    TownBuilder.use(player, stack, msg.pos, msg.face);
                }
            }
        }
    }
}
