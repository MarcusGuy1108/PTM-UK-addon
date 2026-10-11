package com.ptmuk.client.building;

import com.mojang.blaze3d.platform.NativeImage;
import com.ptmuk.PtmUk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Pictures for signs: the mod's built-in made-up logos ("builtin:name") and PNG files the player
 * puts in .minecraft/ptmuk/images ("file:name.png"), loaded as textures on demand and reloaded
 * when the file changes. Each player's game loads its own copy of the folder.
 */
public final class CustomImages {
    public static final List<String> BUILT_IN = List.of("drop", "flame", "leaf", "star", "bolt", "crown", "wave", "cube");
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9 _.()\\-]{1,64}\\.png", Pattern.CASE_INSENSITIVE);
    private static final long MAX_BYTES = 8L << 20;
    private static final int MAX_SIDE = 2048;
    private static final long RECHECK_MS = 2000;

    public record Image(ResourceLocation texture, int width, int height) {
    }

    private record Loaded(Image image, long modified, long checkedAt) {
    }

    private static final Map<String, Loaded> CACHE = new HashMap<>();

    private CustomImages() {
    }

    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("ptmuk").resolve("images");
    }

    /** The PNG files in the images folder (creating it if it isn't there yet). */
    public static List<String> files() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (Stream<Path> s = Files.list(folder())) {
                s.map(p -> p.getFileName().toString()).filter(n -> SAFE.matcher(n).matches()).sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(out::add);
            }
        } catch (IOException ignored) {
            // no folder, no pictures
        }
        return out;
    }

    /** A short name for the editor's logo button. */
    public static String title(String logo) {
        if (logo == null || logo.isEmpty()) {
            return "None";
        }
        if (logo.startsWith("builtin:")) {
            String n = logo.substring(8);
            return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
        return logo.startsWith("file:") ? logo.substring(5) : logo;
    }

    /** The picture for a logo setting, or null if there is none (or the file can't be read). */
    public static Image get(String logo) {
        if (logo == null || logo.isEmpty()) {
            return null;
        }
        if (logo.startsWith("builtin:")) {
            String n = logo.substring(8);
            return BUILT_IN.contains(n) ? new Image(PtmUk.id("textures/logo/" + n + ".png"), 128, 128) : null;
        }
        if (!logo.startsWith("file:")) {
            return null;
        }
        String name = logo.substring(5);
        if (!SAFE.matcher(name).matches()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Loaded cached = CACHE.get(name);
        if (cached != null && now - cached.checkedAt() < RECHECK_MS) {
            return cached.image();
        }
        Path file = folder().resolve(name);
        long modified;
        try {
            modified = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
        } catch (IOException e) {
            modified = -1;
        }
        if (cached != null && cached.modified() == modified) {
            CACHE.put(name, new Loaded(cached.image(), modified, now));
            return cached.image();
        }
        if (cached != null && cached.image() != null) {
            Minecraft.getInstance().getTextureManager().release(cached.image().texture());
        }
        Image image = modified < 0 ? null : load(file, name, modified);
        CACHE.put(name, new Loaded(image, modified, now));
        return image;
    }

    private static Image load(Path file, String name, long modified) {
        try {
            if (Files.size(file) > MAX_BYTES) {
                PtmUk.LOGGER.warn("Sign image {} is over 8 MB, skipped", name);
                return null;
            }
            NativeImage img;
            try (InputStream in = Files.newInputStream(file)) {
                img = NativeImage.read(in);
            }
            if (img.getWidth() > MAX_SIDE || img.getHeight() > MAX_SIDE) {
                PtmUk.LOGGER.warn("Sign image {} is bigger than {} pixels across, skipped", name, MAX_SIDE);
                img.close();
                return null;
            }
            ResourceLocation id = PtmUk.id("custom/" + Integer.toHexString(name.toLowerCase().hashCode()) + "_" + Long.toHexString(modified));
            int w = img.getWidth(), h = img.getHeight();
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
            return new Image(id, w, h);
        } catch (IOException | RuntimeException e) {
            PtmUk.LOGGER.warn("Couldn't load sign image {}: {}", name, e.toString());
            return null;
        }
    }
}
