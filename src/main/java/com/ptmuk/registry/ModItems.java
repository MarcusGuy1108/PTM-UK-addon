package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PtmUk.MOD_ID);

    public static final RegistryObject<Item> UK_SIGNAL_MODERN =
            ITEMS.register("uk_signal_modern", () -> new BlockItem(ModBlocks.UK_SIGNAL_MODERN.get(), new Item.Properties()));
    public static final RegistryObject<Item> UK_SIGNAL_CLASSIC =
            ITEMS.register("uk_signal_classic", () -> new BlockItem(ModBlocks.UK_SIGNAL_CLASSIC.get(), new Item.Properties()));

    private ModItems() {
    }
}
