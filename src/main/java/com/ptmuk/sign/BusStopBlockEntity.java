package com.ptmuk.sign;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.registry.ModBlockEntities;
import com.rinventor.ptm2.core.properties.VDMemoryIDs;
import com.rinventor.ptm2.dimension.virtual.storage.VDStorageHandler;
import com.rinventor.ptm2.dimension.virtual.types.VDStation;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A London-style bus stop flag. The stop name defaults to the PTM2 station the flag stands in,
 * like PTM2's own stop sign; everything can be overridden in the editor.
 */
public class BusStopBlockEntity extends BlockEntity implements EditBlockDataMessage.Editable {
    private static final int MAX = 60;

    public String name = "";
    public String towards = "";
    public String routes = "";
    public String letter = "";
    public boolean request = false;
    /** Name of the PTM2 station this flag is in (resolved on the server, synced). */
    public String stationName = "";

    public BusStopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUS_STOP.get(), pos, state);
    }

    public String displayName() {
        return name.isBlank() ? (stationName.isBlank() ? "Bus Stop" : stationName) : name;
    }

    public void write(CompoundTag tag) {
        tag.putString("name", name);
        tag.putString("towards", towards);
        tag.putString("routes", routes);
        tag.putString("letter", letter);
        tag.putBoolean("request", request);
    }

    public void read(CompoundTag tag) {
        name = clip(tag.getString("name"));
        towards = clip(tag.getString("towards"));
        routes = clip(tag.getString("routes"));
        letter = clip(tag.getString("letter"));
        if (letter.length() > 2) {
            letter = letter.substring(0, 2);
        }
        request = tag.getBoolean("request");
        if (tag.contains("station")) {
            stationName = tag.getString("station");
        }
    }

    private static String clip(String s) {
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshStation();
    }

    /** Looks up the PTM2 station area this flag stands in (as PTM2's stop sign does). */
    public void refreshStation() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        String found = "";
        try {
            for (Map.Entry<Integer, String> e : VDStorageHandler.getAll(level, VDMemoryIDs.STATION).entrySet()) {
                String data = e.getValue();
                if (data == null || data.isEmpty()) {
                    continue;
                }
                VDStation station = new VDStation(data);
                if (station.isInside(worldPosition.getX(), worldPosition.getZ())) {
                    found = station.name == null ? "" : station.name;
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // PTM2 storage not ready yet; try again on the next edit or reload
        }
        if (!found.equals(stationName)) {
            stationName = found;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        tag.putString("station", stationName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        read(tag);
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
        tag.remove("station");
        read(tag);
        refreshStation();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 4, 0);
    }
}
