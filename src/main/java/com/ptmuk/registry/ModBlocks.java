package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.SignalStyle;
import com.ptmuk.block.UkTrafficSignal;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PtmUk.MOD_ID);

    public static final RegistryObject<Block> UK_SIGNAL_MODERN =
            BLOCKS.register("uk_signal_modern", () -> new UkTrafficSignal(SignalStyle.MODERN));
    public static final RegistryObject<Block> UK_SIGNAL_CLASSIC =
            BLOCKS.register("uk_signal_classic", () -> new UkTrafficSignal(SignalStyle.CLASSIC));

    private ModBlocks() {
    }
}
