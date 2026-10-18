package com.ptmuk.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ptmuk.PtmUk;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Your pins (per world and dimension) and the minimap settings (for every world), kept as small JSON files. */
public final class MapPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Pin(String name, int x, int z, int colour) {
    }

    /** The minimap: shown or not, and how many blocks to a pixel. */
    public static final class Settings {
        public boolean minimap = true;
        public float minimapZoom = 1f;
    }

    public static final int[] PIN_COLOURS = {0xFFE5352B, 0xFF2B7BE5, 0xFF2FA84F, 0xFFF2B81B, 0xFF8E44AD, 0xFFE67E22};

    private static Settings settings;
    private static List<Pin> pins;
    private static Path pinsFile;

    private MapPrefs() {
    }

    public static Settings settings() {
        if (settings == null) {
            settings = read(settingsFile(), Settings.class, new Settings());
        }
        return settings;
    }

    public static void saveSettings() {
        write(settingsFile(), settings());
    }

    private static Path settingsFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("ptmuk").resolve("map.json");
    }

    /** The pins for the world the player is in. */
    public static List<Pin> pins() {
        MapStore store = MapStore.get();
        if (store == null) {
            return List.of();
        }
        Path file = store.folder().resolve("pins.json");
        if (!file.equals(pinsFile)) {
            pinsFile = file;
            pins = new ArrayList<>(read(file, new TypeToken<List<Pin>>() { }.getType(), List.<Pin>of()));
        }
        return pins;
    }

    public static void savePins() {
        if (pinsFile != null) {
            write(pinsFile, pins);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Path file, java.lang.reflect.Type type, T fallback) {
        try {
            if (Files.exists(file)) {
                T t = (T) GSON.fromJson(Files.readString(file), type);
                return t == null ? fallback : t;
            }
        } catch (Exception e) {
            PtmUk.LOGGER.warn("Couldn't read {}", file, e);
        }
        return fallback;
    }

    private static void write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(value));
        } catch (Exception e) {
            PtmUk.LOGGER.warn("Couldn't save {}", file, e);
        }
    }
}
