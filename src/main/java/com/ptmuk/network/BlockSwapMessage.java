package com.ptmuk.network;

import com.ptmuk.road.BlockSwap;
import com.ptmuk.road.RoadPlannerItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Client -> server: list what's in a box of the world, swap some of its blocks, or undo the last swap. */
public record BlockSwapMessage(Action action, int x0, int z0, int x1, int z1, boolean groundOnly, List<String> from, List<String> to) {
    public enum Action { SCAN, APPLY, UNDO }

    public static void encode(BlockSwapMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeInt(msg.x0);
        buf.writeInt(msg.z0);
        buf.writeInt(msg.x1);
        buf.writeInt(msg.z1);
        buf.writeBoolean(msg.groundOnly);
        buf.writeCollection(msg.from, FriendlyByteBuf::writeUtf);
        buf.writeCollection(msg.to, FriendlyByteBuf::writeUtf);
    }

    public static BlockSwapMessage decode(FriendlyByteBuf buf) {
        return new BlockSwapMessage(buf.readEnum(Action.class), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(),
                buf.readCollection(n -> new ArrayList<>(Math.min(n, BlockSwap.MAX_KINDS)), b -> b.readUtf(256)),
                buf.readCollection(n -> new ArrayList<>(Math.min(n, BlockSwap.MAX_KINDS)), b -> b.readUtf(256)));
    }

    public static void handle(BlockSwapMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !(player.getMainHandItem().getItem() instanceof RoadPlannerItem
                || player.getOffhandItem().getItem() instanceof RoadPlannerItem)) {
            return;
        }
        if (!RoadPlannerItem.allowed(player)) {
            player.displayClientMessage(Component.literal("The Road Planner works in creative mode only"), true);
            return;
        }
        int x0 = Math.min(msg.x0, msg.x1), x1 = Math.max(msg.x0, msg.x1), z0 = Math.min(msg.z0, msg.z1), z1 = Math.max(msg.z0, msg.z1);
        if (x1 - x0 >= BlockSwap.MAX_SIZE || z1 - z0 >= BlockSwap.MAX_SIZE || msg.from.size() != msg.to.size()) {
            return;
        }
        switch (msg.action) {
            case SCAN -> BlockSwap.scan(player, x0, z0, x1, z1, msg.groundOnly, "");
            case UNDO -> BlockSwap.undo(player, x0, z0, x1, z1, msg.groundOnly);
            case APPLY -> {
                Map<Block, Block> swaps = new HashMap<>();
                for (int i = 0; i < msg.from.size(); i++) {
                    Block a = block(msg.from.get(i)), b = block(msg.to.get(i));
                    if (a != null && b != null) {
                        swaps.put(a, b);
                    }
                }
                BlockSwap.apply(player, x0, z0, x1, z1, msg.groundOnly, swaps);
            }
        }
    }

    /** A block by name, or null if there's no such block (air is fine: it takes blocks away). */
    private static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null || !ForgeRegistries.BLOCKS.containsKey(rl) ? null : ForgeRegistries.BLOCKS.getValue(rl);
    }
}
