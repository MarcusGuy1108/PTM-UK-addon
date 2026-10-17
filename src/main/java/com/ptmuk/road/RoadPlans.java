package com.ptmuk.road;

import com.ptmuk.road.plan.RoadPlan;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** A road plan packed small (deflated JSON) for the item it's saved on and the packets that carry it. */
public final class RoadPlans {
    public static final String TAG = "road_plan";
    /** Packets from the client are capped at 32 KB, so plans are too. */
    public static final int MAX_BYTES = 30000;

    private RoadPlans() {
    }

    public static byte[] pack(RoadPlan plan) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DeflaterOutputStream z = new DeflaterOutputStream(out)) {
                z.write(plan.toJson().getBytes(StandardCharsets.UTF_8));
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static RoadPlan unpack(byte[] data) {
        if (data == null || data.length == 0) {
            return new RoadPlan();
        }
        try (InflaterInputStream z = new InflaterInputStream(new ByteArrayInputStream(data))) {
            return RoadPlan.fromJson(new String(z.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new RoadPlan();
        }
    }

    public static RoadPlan read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new RoadPlan() : unpack(tag.getByteArray(TAG));
    }

    public static void write(ItemStack stack, RoadPlan plan) {
        stack.getOrCreateTag().putByteArray(TAG, pack(plan));
    }
}
