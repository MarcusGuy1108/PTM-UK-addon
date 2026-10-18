package com.ptmuk.client.town;

import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.TownBuilderMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Aims the Town Builder a long way (like the Prefab Tool) and sends the click to the server. */
public final class TownBuilderClient {
    private static final double REACH = 128;
    /** One right-click can come through both use and useOn; only act on the first. */
    private static long last;

    private TownBuilderClient() {
    }

    public static void use(InteractionHand hand, boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (mc.player == null || now == last) {
            return;
        }
        last = now;
        HitResult hit = mc.player.pick(REACH, 1f, false);
        if (sneaking || hit.getType() != HitResult.Type.BLOCK) {
            mc.setScreen(new TownBuilderScreen(hand));
            return;
        }
        BlockHitResult b = (BlockHitResult) hit;
        PtmUkNetwork.CHANNEL.sendToServer(new TownBuilderMessage(TownBuilderMessage.Action.USE, "", 0, false, 0, b.getBlockPos(), b.getDirection(),
                hand == InteractionHand.OFF_HAND));
    }

    static void send(TownBuilderMessage.Action action, String kind, int size, boolean scatter, int spacing, InteractionHand hand) {
        PtmUkNetwork.CHANNEL.sendToServer(new TownBuilderMessage(action, kind, size, scatter, spacing, BlockPos.ZERO, Direction.UP,
                hand == InteractionHand.OFF_HAND));
    }
}
