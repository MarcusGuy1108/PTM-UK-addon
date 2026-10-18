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

    public static void openBusStopEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.ptmuk.sign.BusStopBlockEntity stop) {
            mc.setScreen(new com.ptmuk.client.sign.BusStopEditScreen(stop));
        }
    }

    public static void openShopSignEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.ptmuk.building.ShopSignBlockEntity sign) {
            mc.setScreen(new com.ptmuk.client.building.ShopSignEditScreen(sign));
        }
    }

    /** The player on this client. Common code calls this rather than Minecraft.getInstance().player, so a
     * dedicated server never has to load the client's player class to check the code. */
    public static net.minecraft.world.entity.player.Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    public static void usePrefabTool(net.minecraft.world.InteractionHand hand, boolean sneaking) {
        com.ptmuk.client.building.PrefabToolClient.use(hand, sneaking);
    }

    public static void openFuelPriceSignEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.ptmuk.building.FuelPriceSignBlockEntity sign) {
            mc.setScreen(new com.ptmuk.client.building.FuelPriceSignEditScreen(sign));
        }
    }

    public static void openNameplateEditor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.ptmuk.map.NameplateBlockEntity plate) {
            mc.setScreen(new com.ptmuk.client.map.NameplateEditScreen(plate));
        }
    }
}
