package com.ptmuk.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: new contents for an editable block (direction sign, matrix sign, ...). */
public record EditBlockDataMessage(BlockPos pos, CompoundTag data) {
    public interface Editable {
        void applyEdit(CompoundTag tag);
    }

    public static void encode(EditBlockDataMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeNbt(msg.data);
    }

    public static EditBlockDataMessage decode(FriendlyByteBuf buf) {
        return new EditBlockDataMessage(buf.readBlockPos(), buf.readNbt());
    }

    public static void handle(EditBlockDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || msg.data == null || !player.level().isLoaded(msg.pos)
                || player.distanceToSqr(msg.pos.getCenter()) > 64 * 64 || !player.mayBuild()) {
            return;
        }
        BlockEntity be = player.level().getBlockEntity(msg.pos);
        if (be instanceof Editable editable) {
            editable.applyEdit(msg.data);
        }
    }
}
