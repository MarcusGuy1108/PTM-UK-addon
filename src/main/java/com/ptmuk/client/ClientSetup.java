package com.ptmuk.client;

import com.ptmuk.PtmUk;
import com.ptmuk.client.motorway.VmsRenderer;
import com.ptmuk.client.sign.DirectionSignRenderer;
import com.ptmuk.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CLASSIC_LAMP.get(), ClassicLampRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DIRECTION_SIGN.get(), DirectionSignRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VMS.get(), VmsRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BUS_STOP.get(), com.ptmuk.client.sign.BusStopRenderer::new);
        event.registerEntityRenderer(com.ptmuk.bus.UkBuses.ALX400_TYPE.get(), com.ptmuk.client.bus.ALX400Renderer::new);
    }
}
