package com.ptmuk.building;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** What a fuel price sign shows. All brand names and logos are whatever the player chooses. */
public class FuelPriceSignBlockEntity extends BlockEntity implements EditBlockDataMessage.Editable {
    public static final int ROWS = 4;
    private static final int MAX = 32;

    public String brand = "FILL & GO";
    /** "" for none, "builtin:name" for one of the mod's made-up logos, "file:name.png" for your own image. */
    public String logo = "builtin:drop";
    public int panel = 0xFFF2F0EA;
    public int ink = 0xFFB3202A;
    public int digits = 0xFFFFB21E;
    public boolean lit = true;
    public String info = "Shop | Car Wash | Air & Water";
    public final String[] labels = {"Unleaded", "Diesel", "Super Unleaded", "Super Diesel"};
    public final String[] prices = {"145.9", "152.9", "159.9", "164.9"};
    public final int[] plates = {0xFF1E7A3C, 0xFF121214, 0xFFB3202A, 0xFF4A4F57};

    public FuelPriceSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_PRICE_SIGN.get(), pos, state);
    }

    public void write(CompoundTag tag) {
        tag.putString("brand", brand);
        tag.putString("logo", logo);
        tag.putInt("panel", panel);
        tag.putInt("ink", ink);
        tag.putInt("digits", digits);
        tag.putBoolean("lit", lit);
        tag.putString("info", info);
        for (int i = 0; i < ROWS; i++) {
            tag.putString("label" + i, labels[i]);
            tag.putString("price" + i, prices[i]);
            tag.putInt("plate" + i, plates[i]);
        }
    }

    public void read(CompoundTag tag) {
        if (tag.contains("brand")) {
            brand = clip(tag.getString("brand"));
        }
        if (tag.contains("logo")) {
            logo = clip(tag.getString("logo"), 80);
        }
        if (tag.contains("panel")) {
            panel = tag.getInt("panel") | 0xFF000000;
        }
        if (tag.contains("ink")) {
            ink = tag.getInt("ink") | 0xFF000000;
        }
        if (tag.contains("digits")) {
            digits = tag.getInt("digits") | 0xFF000000;
        }
        if (tag.contains("lit")) {
            lit = tag.getBoolean("lit");
        }
        if (tag.contains("info")) {
            info = clip(tag.getString("info"), 60);
        }
        for (int i = 0; i < ROWS; i++) {
            if (tag.contains("label" + i)) {
                labels[i] = clip(tag.getString("label" + i), 20);
            }
            if (tag.contains("price" + i)) {
                prices[i] = clip(tag.getString("price" + i), 6);
            }
            if (tag.contains("plate" + i)) {
                plates[i] = tag.getInt("plate" + i) | 0xFF000000;
            }
        }
    }

    private static String clip(String s) {
        return clip(s, MAX);
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        read(tag);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        com.ptmuk.map.MapPlaces.note(level, worldPosition, com.ptmuk.map.MapData.Kind.FUEL, brand.isBlank() ? "Petrol station" : brand);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void applyEdit(CompoundTag tag) {
        read(tag);
        com.ptmuk.map.MapPlaces.note(level, worldPosition, com.ptmuk.map.MapData.Kind.FUEL, brand.isBlank() ? "Petrol station" : brand);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1, 0, 1).expandTowards(0, 4, 0);
    }
}
