package com.ptmuk.network;

import com.ptmuk.PtmUk;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PtmUkNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(PtmUk.id("main"), () -> VERSION,
            VERSION::equals, VERSION::equals);

    private PtmUkNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(EditBlockDataMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditBlockDataMessage::encode)
                .decoder(EditBlockDataMessage::decode)
                .consumerMainThread(EditBlockDataMessage::handle)
                .add();
        CHANNEL.messageBuilder(SyncPowerLinesMessage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPowerLinesMessage::encode)
                .decoder(SyncPowerLinesMessage::decode)
                .consumerMainThread(SyncPowerLinesMessage::handle)
                .add();
        CHANNEL.messageBuilder(PrefabToolMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PrefabToolMessage::encode)
                .decoder(PrefabToolMessage::decode)
                .consumerMainThread(PrefabToolMessage::handle)
                .add();
        CHANNEL.messageBuilder(RoadPlannerMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RoadPlannerMessage::encode)
                .decoder(RoadPlannerMessage::decode)
                .consumerMainThread(RoadPlannerMessage::handle)
                .add();
        CHANNEL.messageBuilder(SchematicUploadMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SchematicUploadMessage::encode)
                .decoder(SchematicUploadMessage::decode)
                .consumerMainThread(SchematicUploadMessage::handle)
                .add();
    }
}
