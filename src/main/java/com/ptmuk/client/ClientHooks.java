package com.ptmuk.client;

import com.ptmuk.client.motorway.VmsEditScreen;
import com.ptmuk.client.sign.SignEditScreen;
import com.ptmuk.motorway.VmsBlockEntity;
import com.ptmuk.sign.DirectionSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openSignEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof DirectionSignBlockEntity sign) {
            mc.setScreen(new SignEditScreen(sign));
        }
    }

    public static void openVmsEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof VmsBlockEntity vms) {
            mc.setScreen(new VmsEditScreen(vms));
        }
    }
}
