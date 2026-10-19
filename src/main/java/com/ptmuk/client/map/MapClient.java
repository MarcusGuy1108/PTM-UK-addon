package com.ptmuk.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import com.ptmuk.PtmUk;
import com.ptmuk.map.MapData;
import com.ptmuk.map.MapPlaces;
import com.ptmuk.network.MapDataRequestMessage;
import com.ptmuk.network.PtmUkNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Fills the map in from the chunks round the player, a few each tick; M opens it. */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, value = Dist.CLIENT)
public final class MapClient {
    public static final KeyMapping OPEN = new KeyMapping("key.ptmuk.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.ptmuk");
    /** Chunks looked at each tick: a full sweep of a 12-chunk render distance every couple of seconds. */
    private static final int PER_TICK = 12;

    private static int cursor;
    private static long lastSave, lastAsk = -1000;      // long ago, so it asks straight away (not MIN_VALUE: time - lastAsk would overflow)
    /** What the server last sent for the map round us (shops, stops, named roads, buses...). */
    static MapData data = new MapData();

    private MapClient() {
    }

    @Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("minimap", MinimapHud::render);
            event.registerAboveAll("directions", Navigation::hud);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null) {
            return;
        }
        while (OPEN.consumeClick()) {
            mc.setScreen(new WorldMapScreen());
            lastAsk = -1000;
        }
        long time = mc.level.getGameTime();
        // ask for the places every few seconds, more often with the map open (the buses move)
        if ((MapPrefs.settings().minimap || mc.screen instanceof WorldMapScreen)
                && (time - lastAsk >= (mc.screen instanceof WorldMapScreen ? 40 : 100) || time < lastAsk)) {
            lastAsk = time;
            PtmUkNetwork.CHANNEL.sendToServer(new MapDataRequestMessage());
        }
        Navigation.tick(mc);
        MapStore store = MapStore.get();
        int r = Math.min(mc.options.getEffectiveRenderDistance(), 16), side = r * 2 + 1;
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        for (int i = 0; i < PER_TICK; i++) {
            int k = Math.floorMod(cursor++, side * side);
            LevelChunk chunk = mc.level.getChunkSource().getChunk(pcx - r + k % side, pcz - r + k / side, false);
            if (chunk != null) {
                store.scan(mc.level, chunk);
            }
        }
        long now = mc.level.getGameTime();
        if (now - lastSave > 1200 || now < lastSave) {
            lastSave = now;
            store.saveAll();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MapStore.close();
        Navigation.stop();
        data = new MapData();
    }

    public static void received(MapData d) {
        data = d;
    }

    /** The named road you're on (within a few blocks of its middle), or null. */
    static MapData.Road roadAt(double x, double z) {
        MapData.Road best = null;
        double bestD = 7;
        for (MapData.Road r : data.roads) {
            double d = MapPlaces.distance(r, x, z);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best;
    }
}
