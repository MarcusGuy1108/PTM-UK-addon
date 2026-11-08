package com.ptmuk.client.road;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.ptmuk.road.OsmMap;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Real map data fetched for you: a place found by name (OpenStreetMap's Nominatim search), and the
 * roads, buildings and bus stops round it (the Overpass API) saved into the osm_maps folder as an
 * ordinary .osm file, ready to import. Both are free public services with fair-use rules: one
 * search at a time, a named user agent, and an area no bigger than a few kilometres.
 */
final class MapDownload {
    private static final String AGENT = "PTM-UK-Addon (Minecraft mod; github.com/MarcusGuy1108/PTM-UK-addon)";
    private static final List<String> OVERPASS = List.of("https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter", "https://overpass.private.coffee/api/interpreter");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).proxy(java.net.ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private MapDownload() {
    }

    /** Where a place is: "lat, lon" as typed, or the best match the search finds (in the UK first); {lat, lon, name}. */
    static CompletableFuture<Object[]> find(String place) {
        String[] parts = place.split(",");
        if (parts.length == 2) {
            try {
                double lat = Double.parseDouble(parts[0].trim()), lon = Double.parseDouble(parts[1].trim());
                if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
                    return CompletableFuture.completedFuture(new Object[]{lat, lon, String.format(Locale.ROOT, "%.4f_%.4f", lat, lon)});
                }
            } catch (NumberFormatException ignored) {
                // a place name with a comma in it
            }
        }
        return search(place, "&countrycodes=gb").thenCompose(r -> r != null ? CompletableFuture.completedFuture(r) : search(place, ""))
                .thenApply(r -> {
                    if (r == null) {
                        throw new IllegalStateException("Couldn't find \"" + place + "\"");
                    }
                    return r;
                });
    }

    private static CompletableFuture<Object[]> search(String place, String where) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://nominatim.openstreetmap.org/search?format=json&limit=1" + where
                + "&q=" + URLEncoder.encode(place, StandardCharsets.UTF_8))).header("User-Agent", AGENT).timeout(Duration.ofSeconds(30)).build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() != 200) {
                throw new IllegalStateException("The place search said " + res.statusCode());
            }
            JsonArray a = JsonParser.parseString(res.body()).getAsJsonArray();
            if (a.isEmpty()) {
                return null;
            }
            var o = a.get(0).getAsJsonObject();
            return new Object[]{o.get("lat").getAsDouble(), o.get("lon").getAsDouble(), o.has("name") ? o.get("name").getAsString() : place};
        });
    }

    /**
     * The map `half` metres each way round lat, lon, from the first Overpass server that answers,
     * saved as name.osm in the maps folder; the file's name.
     */
    static CompletableFuture<String> download(String name, double lat, double lon, double half) {
        double[] b = OsmMap.box(lat, lon, half);
        String body = "data=" + URLEncoder.encode(OsmMap.overpassQuery(b[0], b[1], b[2], b[3]), StandardCharsets.UTF_8);
        String file = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "") + "_" + (int) (half * 2) + "m.osm";
        Path path = MapFiles.folder().resolve(file);
        return tryServer(0, body, path).thenApply(p -> file);
    }

    private static CompletableFuture<Path> tryServer(int k, String body, Path path) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(OVERPASS.get(k))).header("User-Agent", AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofMinutes(4))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        Path part = path.resolveSibling(path.getFileName() + ".part");
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofFile(part)).thenApply(res -> {
            try {
                String head;
                try (var in = Files.newInputStream(part)) {
                    head = new String(in.readNBytes(400), StandardCharsets.UTF_8);    // (an error comes back as a short page, not a map)
                }
                if (res.statusCode() != 200 || !head.contains("<osm")) {
                    Files.deleteIfExists(part);
                    throw new IllegalStateException("Overpass said " + res.statusCode());
                }
                Files.move(part, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return path;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }).exceptionallyCompose(err -> k + 1 < OVERPASS.size() ? tryServer(k + 1, body, path) : CompletableFuture.failedFuture(err));
    }
}
