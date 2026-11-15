package com.ptmuk.shop;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;

/**
 * The shops' goings-on outside the tills: the tills already built in a world (before they could
 * serve) are opened up as their chunks load, so their staff come in; and walking out of a shop
 * with something you haven't paid for sets its alarm off.
 */
public final class ShopEvents {
    private record Waiting(ServerLevel level, ChunkPos chunk) {
    }

    private static final Deque<Waiting> CHUNKS = new ArrayDeque<>();

    private ShopEvents() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            for (LevelChunkSection s : chunk.getSections()) {
                if (s != null && !s.hasOnlyAir() && s.maybeHas(st -> st.getBlock() instanceof TillBlock || st.getBlock() instanceof CheckoutBlock)) {
                    CHUNKS.add(new Waiting(level, chunk.getPos()));
                    return;
                }
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (int n = 0; n < 4 && !CHUNKS.isEmpty(); n++) {
            Waiting w = CHUNKS.poll();
            if (!w.level().hasChunk(w.chunk().x, w.chunk().z)) {
                continue;
            }
            LevelChunk chunk = w.level().getChunk(w.chunk().x, w.chunk().z);
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection s = sections[i];
                if (s == null || s.hasOnlyAir()) {
                    continue;
                }
                int y0 = chunk.getMinBuildHeight() + i * 16;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            var b = s.getBlockState(x, y, z).getBlock();
                            if (b instanceof TillBlock || b instanceof CheckoutBlock) {
                                w.level().getBlockEntity(new BlockPos(w.chunk().getMinBlockX() + x, y0 + y, w.chunk().getMinBlockZ() + z));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Out in the open (under the sky), away from the shop, with something unpaid: the alarm. */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        if (event.phase != TickEvent.Phase.END || p.level().isClientSide || p.tickCount % 40 != 0) {
            return;
        }
        int unpaid = 0;
        for (ItemStack s : p.getInventory().items) {
            BlockPos shop = ProductItem.shop(s);
            if (ProductItem.unpaid(s) && shop != null && shop.distSqr(p.blockPosition()) > 16 * 16) {
                unpaid += s.getCount();
            }
        }
        long now = p.level().getGameTime();
        if (unpaid == 0 || !p.level().canSeeSky(p.blockPosition().above()) || now - p.getPersistentData().getLong("ptmuk_alarm") < 600) {
            return;
        }
        p.getPersistentData().putLong("ptmuk_alarm", now);
        for (int i = 0; i < 3; i++) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 2.0F - i * 0.1F);
        }
        p.sendSystemMessage(Component.literal("BEEP BEEP! The shop's alarm went off: you haven't paid for " + unpaid
                + (unpaid == 1 ? " thing." : " things.") + " Take them back to the till.").withStyle(ChatFormatting.RED));
    }
}
