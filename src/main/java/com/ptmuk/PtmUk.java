package com.ptmuk;

import com.mojang.logging.LogUtils;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.registry.ModBlockEntities;
import com.ptmuk.registry.ModBlocks;
import com.ptmuk.registry.ModCreativeTabs;
import com.ptmuk.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PtmUk.MOD_ID)
public class PtmUk {
    public static final String MOD_ID = "ptmuk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PtmUk() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        com.ptmuk.bus.UkBuses.ENTITIES.register(modBus);
        modBus.addListener(PtmUk::attributes);
        PtmUkNetwork.register();
    }

    private static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        com.ptmuk.bus.UkBuses.TYPES.values().forEach(type ->
                event.put(type.get(), com.rinventor.ptm2.objects.entities.vehicle.Vehicle.attributes()));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
