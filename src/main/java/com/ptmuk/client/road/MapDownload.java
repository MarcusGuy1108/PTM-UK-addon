package com.ptmuk.client.road;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.ptmuk.PtmUk;
import com.ptmuk.road.Lidar;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import net.minecraft.Util;

/**
 * Real map data fetched for you: a place found by name (OpenStreetMap's Nominatim search), and the
 * roads, buildings and bus stops round it (the Overpass API) saved into the osm_maps folder as an
 * ordinary .osm file, ready to import; and in England, real heights for its buildings from the
 * Environment Agency's LIDAR survey. All free public services with fair-use rules: one search at a
 * time, a named user agent, and an area no bigger than a few kilometres.
 */
final class MapDownload {
    static final String AGENT = "PTM-UK-Addon (Minecraft mod; github.com/MarcusGuy1108/PTM-UK-addon)";
    private static final List<String> OVERPASS = List.of("https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter", "https://overpass.private.coffee/api/interpreter");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).proxy(java.net.ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private MapDownload() {
    }

    /** Where a link points: Google Maps (…/@lat,lon,… or ?q=lat,lon) or openstreetmap.org (#map=zoom/lat/lon, mlat= and mlon=). */
    private static final java.util.regex.Pattern[] LINKS = {
            java.util.regex.Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
            java.util.regex.Pattern.compile("[?&](?:q|ll|query|center)=(-?\\d+\\.\\d+)(?:,|%2C)\\s*(-?\\d+\\.\\d+)"),
            java.util.regex.Pattern.compile("map=\\d+(?:\\.\\d+)?/(-?\\d+\\.\\d+)/(-?\\d+\\.\\d+)"),
            java.util.regex.Pattern.compile("mlat=(-?\\d+\\.\\d+)&mlon=(-?\\d+\\.\\d+)")};

    /** The lat, lon a map link points at, or null if it isn't one. */
    static double[] link(String text) {
        if (!text.contains("/") && !text.contains("=")) {
            return null;
        }
        for (java.util.regex.Pattern p : LINKS) {
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                double lat = Double.parseDouble(m.group(1)), lon = Double.parseDouble(m.group(2));
                return Math.abs(lat) <= 90 && Math.abs(lon) <= 180 ? new double[]{lat, lon} : null;
            }
        }
        return null;
    }

    /** Where a place is: a map link or "lat, lon" as pasted, or the best match the search finds (in the UK first); {lat, lon, name}. */
    static CompletableFuture<Object[]> find(String place) {
        double[] at = link(place);
        if (at != null) {
            return CompletableFuture.completedFuture(new Object[]{at[0], at[1], String.format(Locale.ROOT, "%.4f_%.4f", at[0], at[1])});
        }
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

    /**
     * A picture of OpenStreetMap's map (a 256-pixel PNG tile), from the folder it's kept in for a
     * week, else from OpenStreetMap's tile server (whose rules: a named app, and keep what you fetch).
     */
    static byte[] tile(int z, int x, int y) throws IOException, InterruptedException {
        Path file = MapFiles.folder().resolve("tiles").resolve(z + "/" + x + "/" + y + ".png");
        if (Files.isRegularFile(file) && System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < 7L * 24 * 3600 * 1000) {
            return Files.readAllBytes(file);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png"))
                .header("User-Agent", AGENT).timeout(Duration.ofSeconds(30)).build();
        HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            throw new IOException("the map server said " + res.statusCode());
        }
        Files.createDirectories(file.getParent());
        Files.write(file, res.body());
        return res.body();
    }

    /** England's LIDAR survey (Environment Agency, Open Government Licence): the tops of things, and the bare ground. */
    private static final String SURFACE = "https://environment.data.gov.uk/spatialdata/lidar-composite-digital-surface-model-last-return-dsm-1m/wcs"
            + "?coverageId=9ba4d5ac-d596-445a-9056-dae3ddec0178__Lidar_Composite_Elevation_LZ_DSM_1m",
            GROUND = "https://environment.data.gov.uk/spatialdata/lidar-composite-digital-terrain-model-dtm-1m/wcs"
            + "?coverageId=13787b9a-26a4-4775-8523-806d13af58fc__Lidar_Composite_Elevation_DTM_1m";
    /** Degrees of latitude and longitude in one tile asked for (about 450 by 420 m). */
    private static final double TILE_LAT = 0.004, TILE_LON = 0.006;

    /**
     * Real heights for a map, south, west, north, east in degrees: the LIDAR survey fetched a tile
     * at a time (a metre a pixel; two for a big map) and saved beside the map file (see
     * MapFiles.lidar). England only: anywhere else it fails, and the map is used without them.
     */
    static CompletableFuture<Lidar> heights(String file, double s, double w, double n, double e, Consumer<String> progress) {
        return CompletableFuture.supplyAsync(() -> {
            int rows = (int) Math.ceil((n - s) / TILE_LAT - 1e-9), cols = (int) Math.ceil((e - w) / TILE_LON - 1e-9);
            if (rows * cols > 150) {
                throw new IllegalStateException("that map's too big to fetch heights for (3 km across at most)");
            }
            String coarse = (n - s) * 111_000 > 1600 ? "&scaleFactor=0.5" : "";       // a big map: 2 m pixels, a quarter of the download
            ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "PTM UK real heights");
                t.setDaemon(true);
                return t;
            });
            try {
                List<Future<Lidar.Grid[]>> parts = new ArrayList<>();
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        double ts = s + r * TILE_LAT, tw = w + c * TILE_LON;
                        String box = String.format(Locale.ROOT, "&subset=Lat(%.6f,%.6f)&subset=Long(%.6f,%.6f)", ts, Math.min(n, ts + TILE_LAT), tw,
                                Math.min(e, tw + TILE_LON)) + coarse;
                        parts.add(pool.submit(() -> new Lidar.Grid[]{tile(SURFACE + box), tile(GROUND + box)}));
                    }
                }
                Lidar lidar = new Lidar();
                int failed = 0;
                for (int i = 0; i < parts.size(); i++) {
                    progress.accept("Fetching real heights (England's LIDAR survey): " + (i + 1) + " of " + parts.size() + "...");
                    try {
                        Lidar.Grid[] t = parts.get(i).get();
                        if (t[0] != null && t[1] != null) {
                            lidar.add(t[0], t[1]);
                        }
                    } catch (ExecutionException ex) {
                        failed++;                                   // its buildings get the usual guesses
                        PtmUk.LOGGER.warn("A tile of real heights didn't come: {}", ex.getCause().toString());
                    }
                }
                if (failed * 2 > parts.size()) {
                    throw new IllegalStateException("the LIDAR service isn't answering just now (press Real heights: fetch later)");
                }
                lidar.tiles.removeIf(t -> {
                    for (float v : t.v()) {
                        if (!Float.isNaN(v)) {
                            return false;
                        }
                    }
                    return true;                                    // nothing surveyed in it
                });
                if (lidar.isEmpty()) {
                    throw new IllegalStateException("there's no LIDAR survey here (it covers England)");
                }
                Path path = MapFiles.lidar(file), part = path.resolveSibling(path.getFileName() + ".part");
                try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(part))) {
                    lidar.write(out);
                }
                Files.move(part, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return lidar;
            } catch (IOException ex) {
                throw new IllegalStateException("couldn't save them: " + ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("stopped");
            } finally {
                pool.shutdownNow();
            }
        }, Util.backgroundExecutor());
    }

    /** One tile of the survey (tried three times: the service has its moments), or null where it has none (outside England). */
    private static Lidar.Grid tile(String coverage) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(coverage + "&service=WCS&version=2.0.1&request=GetCoverage&format=image/tiff"
                        + "&subsettingCrs=http://www.opengis.net/def/crs/EPSG/0/4326&outputCrs=http://www.opengis.net/def/crs/EPSG/0/4326"))
                .header("User-Agent", AGENT).timeout(Duration.ofSeconds(60)).build();
        for (int tries = 1; ; tries++) {
            try {
                HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() < 500) {
                    return res.statusCode() == 200 ? Lidar.tiff(res.body()) : null;
                }
                if (tries == 3) {
                    throw new IOException("the LIDAR service said " + res.statusCode());
                }
            } catch (java.net.http.HttpTimeoutException ex) {
                if (tries == 3) {
                    throw ex;
                }
            }
            Thread.sleep(2000L * tries);
        }
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
