package com.ptmuk.building.schematic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * A building read from a schematic file, already brought up to this game version: a palette of
 * block states (Name and Properties, entry 0 is air), the palette index of every cell (x fastest,
 * then z, then y) and the block entities. Built facing south like the mod's own prefabs, so the
 * south side is treated as the front. Sent from the client that read it to the server in a
 * compact gzipped form.
 */
public final class Schematic {
    /** Biggest box that can be imported (cells, air included), and the longest side. */
    public static final int MAX_VOLUME = 8 * 1024 * 1024;
    public static final int MAX_SIDE = 1024;
    public static final int MAX_PALETTE = 65536;

    public final String name;
    public final String format;
    public final int width, height, depth;
    public final List<CompoundTag> palette;
    public final int[] cells;
    /** Each: x, y, z inside the box and the block entity's data under "nbt". */
    public final List<CompoundTag> blockEntities;

    public Schematic(String name, String format, int width, int height, int depth, List<CompoundTag> palette, int[] cells,
                     List<CompoundTag> blockEntities) {
        this.name = name;
        this.format = format;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.palette = palette;
        this.cells = cells;
        this.blockEntities = blockEntities;
    }

    public int index(int x, int y, int z) {
        return (y * depth + z) * width + x;
    }

    public int solidBlocks() {
        int n = 0;
        for (int c : cells) {
            if (c != 0) {
                n++;
            }
        }
        return n;
    }

    public static void checkSize(long w, long h, long d) throws IOException {
        if (w <= 0 || h <= 0 || d <= 0) {
            throw new IOException("it's empty");
        }
        if (w > MAX_SIDE || h > MAX_SIDE || d > MAX_SIDE || w * h * d > MAX_VOLUME) {
            throw new IOException("too big: " + w + " x " + d + ", " + h + " high (the limit is " + MAX_VOLUME / (1024 * 1024)
                    + " million blocks in its box)");
        }
    }

    // ------------------------------------------------------------------ the compact form sent to the server

    public byte[] encode() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("format", format);
        tag.putInt("w", width);
        tag.putInt("h", height);
        tag.putInt("d", depth);
        ListTag pal = new ListTag();
        pal.addAll(palette);
        tag.put("palette", pal);
        ByteArrayOutputStream cellBytes = new ByteArrayOutputStream(cells.length);
        for (int c : cells) {
            while ((c & ~0x7F) != 0) {
                cellBytes.write((c & 0x7F) | 0x80);
                c >>>= 7;
            }
            cellBytes.write(c);
        }
        tag.putByteArray("cells", cellBytes.toByteArray());
        ListTag bes = new ListTag();
        bes.addAll(blockEntities);
        tag.put("blockEntities", bes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    /** Reads what {@link #encode} wrote, refusing anything oversized (it comes from a client). */
    public static Schematic decode(byte[] bytes) throws IOException {
        CompoundTag tag;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            tag = NbtIo.read(in, new NbtAccounter(512L * 1024 * 1024));
        }
        int w = tag.getInt("w"), h = tag.getInt("h"), d = tag.getInt("d");
        checkSize(w, h, d);
        ListTag pal = tag.getList("palette", Tag.TAG_COMPOUND);
        if (pal.isEmpty() || pal.size() > MAX_PALETTE) {
            throw new IOException("bad palette");
        }
        List<CompoundTag> palette = new ArrayList<>();
        for (int i = 0; i < pal.size(); i++) {
            palette.add(pal.getCompound(i));
        }
        int[] cells = Varints.read(tag.getByteArray("cells"), w * h * d, palette.size());
        List<CompoundTag> bes = new ArrayList<>();
        ListTag list = tag.getList("blockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            bes.add(list.getCompound(i));
        }
        return new Schematic(tag.getString("name"), tag.getString("format"), w, h, d, palette, cells, bes);
    }

    /** Unsigned LEB128 numbers, as Sponge schematics store their blocks. */
    public static final class Varints {
        private Varints() {
        }

        public static int[] read(byte[] data, int count, int limit) throws IOException {
            int[] out = new int[count];
            int i = 0, n = 0;
            while (n < count) {
                int value = 0, shift = 0;
                while (true) {
                    if (i >= data.length) {
                        throw new IOException("block data ends early");
                    }
                    byte b = data[i++];
                    value |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                    if (shift > 28) {
                        throw new IOException("bad block data");
                    }
                }
                if (value < 0 || value >= limit) {
                    throw new IOException("block data points outside the palette");
                }
                out[n++] = value;
            }
            return out;
        }
    }
}
