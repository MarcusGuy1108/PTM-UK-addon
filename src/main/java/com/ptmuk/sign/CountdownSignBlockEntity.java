package com.ptmuk.sign;

import com.ptmuk.registry.ModBlockEntities;
import com.rinventor.ptm2.dimension.virtual.util.Departure;
import com.rinventor.ptm2.dimension.virtual.util.Departures;
import com.rinventor.ptm2.dimension.virtual.util.VDUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Live arrivals for a countdown sign. The server looks up the PTM2 stop (platform) the sign is
 * in, or one right next to it, every two seconds and sends the next buses to the clients.
 */
public class CountdownSignBlockEntity extends BlockEntity {
    public static final int ROWS = 3;
    /** All PTM2 vehicle types, as PTM2's own departure boards default to. */
    private static final String TYPES = "0,1,2,3,4,5";
    private static final int SEARCH = 3;

    /** True once the sign has found a PTM2 stop. */
    public boolean linked;
    public String stopName = "";
    /** Rows of {route, destination, minutes}. */
    public final List<String[]> rows = new ArrayList<>();
    private int timer;
    private int platformID = -1;
    private int platformCheck;

    public CountdownSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COUNTDOWN_SIGN.get(), pos, state);
    }

    void serverTick() {
        if (level == null || ++timer % 40 != 1) {
            return;
        }
        boolean wasLinked = linked;
        String oldStop = stopName;
        String oldRows = serialise();
        try {
            refresh();
        } catch (RuntimeException e) {
            linked = false;
            rows.clear();
        }
        if (linked != wasLinked || !stopName.equals(oldStop) || !serialise().equals(oldRows)) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void refresh() {
        // the platform lookup is the costly part: redo it every 30 s
        if (--platformCheck <= 0) {
            platformCheck = 15;
            platformID = -1;
            BlockPos at = null;
            search:
            for (int r = 0; r <= SEARCH; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        for (int dy = 0; dy >= -3; dy--) {
                            BlockPos p = worldPosition.offset(dx, dy, dz);
                            int id = VDUtils.getPlatformID(level, p);
                            if (id != -1) {
                                platformID = id;
                                at = p;
                                break search;
                            }
                        }
                    }
                }
            }
            stopName = at == null ? "" : nullToEmpty(VDUtils.getStationName(level, at));
        }
        linked = platformID != -1;
        rows.clear();
        if (!linked) {
            return;
        }
        for (Departure d : Departures.get(level, platformID, TYPES)) {
            if (rows.size() >= ROWS) {
                break;
            }
            rows.add(new String[]{nullToEmpty(d.getNumber()), nullToEmpty(d.getDestination()), Integer.toString(d.getETA())});
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String serialise() {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            sb.append(String.join("\t", row)).append('\n');
        }
        return sb.toString();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("linked", linked);
        tag.putString("stop", stopName);
        ListTag list = new ListTag();
        for (String[] row : rows) {
            CompoundTag r = new CompoundTag();
            r.putString("route", row[0]);
            r.putString("destination", row[1]);
            r.putString("minutes", row[2]);
            list.add(r);
        }
        tag.put("rows", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linked = tag.getBoolean("linked");
        stopName = tag.getString("stop");
        rows.clear();
        for (Tag t : tag.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) t;
            rows.add(new String[]{r.getString("route"), r.getString("destination"), r.getString("minutes")});
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
