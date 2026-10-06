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
    public static final RegistryObject<Item> UK_DOUBLE_DECKER;
    public static final RegistryObject<Item> ENVIRO400;
    public static final RegistryObject<Item> ALX400_LEN;
    public static final RegistryObject<Item> ENVIRO400_LEN;
    public static final RegistryObject<Item> OMNICITY;
    public static final RegistryObject<Item> KIA_K4;
    public static final RegistryObject<Item> CUBE_CARD;
    public static final RegistryObject<Item> BUS_PASS;

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
        UK_DOUBLE_DECKER = ITEMS.register("uk_double_decker", () -> com.ptmuk.bus.UkBuses.transportItem(com.ptmuk.bus.UkBuses.UKDD_CODE));
        BUS_ITEMS.add(UK_DOUBLE_DECKER);
        ENVIRO400 = ITEMS.register("enviro400", () -> com.ptmuk.bus.UkBuses.transportItem(com.ptmuk.bus.UkBuses.E400_CODE));
        BUS_ITEMS.add(ENVIRO400);
        ALX400_LEN = ITEMS.register("alx400_len", () -> com.ptmuk.bus.UkBuses.transportItem(com.ptmuk.bus.UkBuses.ALX400_LEN_CODE));
        BUS_ITEMS.add(ALX400_LEN);
        ENVIRO400_LEN = ITEMS.register("enviro400_len", () -> com.ptmuk.bus.UkBuses.transportItem(com.ptmuk.bus.UkBuses.E400_LEN_CODE));
        BUS_ITEMS.add(ENVIRO400_LEN);
        OMNICITY = ITEMS.register("omnicity", () -> com.ptmuk.bus.UkBuses.transportItem(com.ptmuk.bus.UkBuses.OMNICITY_CODE));
        BUS_ITEMS.add(OMNICITY);
        KIA_K4 = ITEMS.register("kia_k4_gt_line_s", () -> com.ptmuk.bus.UkBuses.carItem(com.ptmuk.bus.UkBuses.K4_CODE, "red"));
        BUS_ITEMS.add(KIA_K4);
        CUBE_CARD = ITEMS.register("cube_card", com.ptmuk.fare.CubeCardItem::new);
        BUS_ITEMS.add(CUBE_CARD);
        BUS_PASS = ITEMS.register("bus_pass", com.ptmuk.fare.BusPassItem::new);
        BUS_ITEMS.add(BUS_PASS);
        BUS_ITEMS.add(item("london_bus_stop", ModBlocks.BUS_STOP));
        BUS_ITEMS.add(item("bus_countdown_sign", ModBlocks.COUNTDOWN_SIGN));
        register(ModBlocks.BUS_FURNITURE, BUS_ITEMS);
        register(ModBlocks.FURNITURE, STREET_ITEMS);
        // the stop furniture shows in the buses tab as well
        for (RegistryObject<Item> item : List.copyOf(STREET_ITEMS)) {
            if (java.util.Set.of("bus_shelter", "bus_shelter_london", "bus_stop_flag").contains(item.getId().getPath())) {
                BUS_ITEMS.add(item);
            }
        }
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
