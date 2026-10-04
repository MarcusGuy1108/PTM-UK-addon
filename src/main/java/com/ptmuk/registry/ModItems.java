package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.AccessoryType;
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

    /** Items per creative tab. */
    public static final List<RegistryObject<Item>> SIGNAL_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Item>> POLE_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Item>> SIGN_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Item>> STREET_ITEMS = new ArrayList<>();

    static {
        register(ModBlocks.POLES, POLE_ITEMS);
        register(ModBlocks.SIGNALS, SIGNAL_ITEMS);
        for (AccessoryType type : AccessoryType.values()) {
            (type.mount == AccessoryType.Mount.SIGN ? SIGN_ITEMS : SIGNAL_ITEMS)
                    .add(item(type.id(), ModBlocks.ACCESSORIES.get(type.id())));
        }
        register(ModBlocks.FURNITURE, STREET_ITEMS);
        register(ModBlocks.FENCES, STREET_ITEMS);
    }

    private static void register(Map<String, RegistryObject<Block>> blocks, List<RegistryObject<Item>> tab) {
        blocks.forEach((name, block) -> tab.add(item(name, block)));
    }

    private static RegistryObject<Item> item(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private ModItems() {
    }
}
