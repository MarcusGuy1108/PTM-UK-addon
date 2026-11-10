package com.ptmuk.client.road;

import com.ptmuk.road.Lidar;
import com.ptmuk.road.OsmMap;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Real maps: OpenStreetMap exports (.osm) in the game folder's osm_maps folder, read in the background once. */
public final class MapFiles {
    private record Loaded(long modified, CompletableFuture<OsmMap> map) {
    }

    private static final Map<String, Loaded> CACHE = new HashMap<>();

    private MapFiles() {
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("osm_maps");
    }

    public static List<String> list() {
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
            // a read-only game folder: just list what's there
        }
        try (Stream<Path> files = Files.list(folder())) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".osm")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The map in a file of the folder (the name comes off an item, so nothing outside the folder). */
    public static CompletableFuture<OsmMap> get(String name) {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return CompletableFuture.failedFuture(new IOException("not a map file: " + name));
        }
        Path path = folder().resolve(name), heights = lidar(name);
        long modified;
        try {
            modified = Files.getLastModifiedTime(path).toMillis() * 31 + (Files.exists(heights) ? Files.getLastModifiedTime(heights).toMillis() : 0);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new IOException(name + " isn't in the osm_maps folder"));
        }
        Loaded l = CACHE.get(name);
        if (l == null || l.modified() != modified) {
            l = new Loaded(modified, CompletableFuture.supplyAsync(() -> {
                OsmMap map;
                try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                    map = OsmMap.read(in);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
                if (Files.exists(heights)) {
                    try (InputStream in = new BufferedInputStream(Files.newInputStream(heights))) {
                        map.lidar = Lidar.read(in);
                    } catch (IOException e) {
                        com.ptmuk.PtmUk.LOGGER.warn("Couldn't read the real heights for {}: {}", name, e.toString());
                    }
                }
                return map;
            }, Util.backgroundExecutor()));
            CACHE.put(name, l);
        }
        return l.map();
    }

    /** Where a map's real heights (see MapDownload.heights) are kept: beside it. */
    static Path lidar(String name) {
        return folder().resolve(name + ".lidar");
    }
}
