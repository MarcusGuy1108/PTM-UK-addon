package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.AccessoryType;
import com.ptmuk.power.CableToolItem;
import com.ptmuk.power.PylonBuilderItem;
import java.util.LinkedHashMap;
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
    public static final List<RegistryObject<Item>> MOTORWAY_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Item>> POWER_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Item>> BUILDING_ITEMS = new ArrayList<>();
    /** Tower name -> the item that builds it. */
    public static final Map<String, RegistryObject<Item>> PYLON_BUILDERS = new LinkedHashMap<>();
    public static final RegistryObject<Item> CABLE_TOOL;
    public static final List<RegistryObject<Item>> BUS_ITEMS = new ArrayList<>();
    public static final RegistryObject<Item> ALX400;

    static {
        register(ModBlocks.POLES, POLE_ITEMS);
        register(ModBlocks.SIGNALS, SIGNAL_ITEMS);
        for (AccessoryType type : AccessoryType.values()) {
            (type.mount == AccessoryType.Mount.SIGN ? SIGN_ITEMS : SIGNAL_ITEMS)
                    .add(item(type.id(), ModBlocks.ACCESSORIES.get(type.id())));
        }
        register(ModBlocks.DIRECTION_SIGNS, SIGN_ITEMS);
        register(ModBlocks.MOTORWAY, MOTORWAY_ITEMS);
        ModBlocks.PYLONS.forEach((name, block) -> {
            RegistryObject<Item> item = ITEMS.register(name + "_builder", () -> new PylonBuilderItem(block));
            PYLON_BUILDERS.put(name, item);
            POWER_ITEMS.add(item);
        });
        CABLE_TOOL = ITEMS.register("cable_tool", CableToolItem::new);
        POWER_ITEMS.add(CABLE_TOOL);
        POWER_ITEMS.add(ITEMS.register("pylon_dismantler", com.ptmuk.power.PylonDismantlerItem::new));
        ALX400 = ITEMS.register("alx400", () -> com.ptmuk.bus.UkBuses.transportItem(com.ptmuk.bus.UkBuses.ALX400_CODE));
        BUS_ITEMS.add(ALX400);
        BUS_ITEMS.add(item("london_bus_stop", ModBlocks.BUS_STOP));
        register(ModBlocks.FURNITURE, STREET_ITEMS);
        register(ModBlocks.FENCES, STREET_ITEMS);
        register(ModBlocks.BUILDING, BUILDING_ITEMS);
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
