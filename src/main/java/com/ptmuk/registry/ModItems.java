package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PtmUk.MOD_ID);

    /** Block items in creative-tab order: signals first, then accessories. */
    public static final List<RegistryObject<Item>> BLOCK_ITEMS = new ArrayList<>();

    static {
        register(ModBlocks.SIGNALS);
        register(ModBlocks.ACCESSORIES);
    }

    private static void register(Map<String, RegistryObject<Block>> blocks) {
        blocks.forEach((name, block) ->
                BLOCK_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()))));
    }

    private ModItems() {
    }
}
