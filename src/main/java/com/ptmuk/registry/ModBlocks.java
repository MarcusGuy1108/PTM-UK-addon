package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.AccessoryType;
import com.ptmuk.block.BuildingMaterial;
import com.ptmuk.block.FurnitureBlock;
import com.ptmuk.block.FurnitureType;
import com.ptmuk.block.PoleType;
import com.ptmuk.block.SignalAccessory;
import com.ptmuk.block.SignalStyle;
import com.ptmuk.block.SignalType;
import com.ptmuk.block.UkFence;
import com.ptmuk.block.UkPole;
import com.ptmuk.block.UkTrafficSignal;
import com.ptmuk.motorway.GantryBeamBlock;
import com.ptmuk.motorway.GantryLegBlock;
import com.ptmuk.motorway.LaneSignalBlock;
import com.ptmuk.motorway.VmsBlock;
import com.ptmuk.power.PylonBlock;
import com.ptmuk.power.PylonCells;
import com.ptmuk.sign.DirectionSignBlock;
import com.ptmuk.sign.SignScheme;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PtmUk.MOD_ID);

    /** Every UK pole, in creative-tab order. */
    public static final Map<String, RegistryObject<Block>> POLES = new LinkedHashMap<>();
    /** Every signal head, in creative-tab order. */
    public static final Map<String, RegistryObject<Block>> SIGNALS = new LinkedHashMap<>();
    /** Street furniture, in creative-tab order. */
    public static final Map<String, RegistryObject<Block>> FURNITURE = new LinkedHashMap<>();
    /** Bus furniture (card readers, machines, road markings), in the buses tab. */
    public static final Map<String, RegistryObject<Block>> BUS_FURNITURE = new LinkedHashMap<>();
    public static final Set<FurnitureType> BUS_TYPES = EnumSet.of(FurnitureType.CARD_READER, FurnitureType.TOP_UP_MACHINE,
            FurnitureType.BUS_STOP_MARKING, FurnitureType.BUS_LANE_MARKING);
    /** Fences and railings. */
    public static final Map<String, RegistryObject<Block>> FENCES = new LinkedHashMap<>();
    /** Every accessory, in creative-tab order. */
    public static final Map<String, RegistryObject<Block>> ACCESSORIES = new LinkedHashMap<>();
    /** Editable direction signs, one per colour scheme (the scheme can be changed in the editor). */
    public static final Map<String, RegistryObject<Block>> DIRECTION_SIGNS = new LinkedHashMap<>();
    /** Motorway gantry parts and signals. */
    public static final Map<String, RegistryObject<Block>> MOTORWAY = new LinkedHashMap<>();
    public static final RegistryObject<Block> VMS;
    public static final RegistryObject<Block> BUS_STOP = BLOCKS.register("london_bus_stop", com.ptmuk.sign.BusStopBlock::new);
    public static final RegistryObject<Block> COUNTDOWN_SIGN = BLOCKS.register("bus_countdown_sign", com.ptmuk.sign.CountdownSignBlock::new);
    /** UK building materials: full blocks, then their slabs. */
    public static final Map<String, RegistryObject<Block>> BUILDING = new LinkedHashMap<>();
    /** Transmission towers, one block per tower type (each cell of the tower is a state). */
    public static final Map<String, RegistryObject<Block>> PYLONS = new LinkedHashMap<>();

    static {
        for (PoleType type : PoleType.values()) {
            POLES.put(type.id(), BLOCKS.register(type.id(), () -> new UkPole(type)));
        }
        for (SignalType type : SignalType.values()) {
            for (SignalStyle style : stylesFor(type)) {
                String name = style.id() + "_" + type.id();
                SIGNALS.put(name, BLOCKS.register(name, () -> new UkTrafficSignal(style, type)));
            }
        }
        for (FurnitureType type : FurnitureType.values()) {
            RegistryObject<Block> block = BLOCKS.register(type.id(), () -> switch (type) {
                case CARD_READER -> new com.ptmuk.fare.CardReaderBlock(type);
                case TOP_UP_MACHINE -> new com.ptmuk.fare.TopUpMachineBlock(type);
                default -> new FurnitureBlock(type);
            });
            (BUS_TYPES.contains(type) ? BUS_FURNITURE : FURNITURE).put(type.id(), block);
        }
        fence("palisade_fence", SoundType.METAL);
        fence("black_railings", SoundType.METAL);
        fence("pedestrian_guardrail", SoundType.METAL);
        fence("close_board_fence", SoundType.WOOD);
        fence("heras_fence", SoundType.METAL);
        fence("armco_barrier", SoundType.METAL);
        for (AccessoryType type : AccessoryType.values()) {
            ACCESSORIES.put(type.id(), BLOCKS.register(type.id(), () -> new SignalAccessory(type)));
        }
        for (SignScheme scheme : SignScheme.values()) {
            String id = scheme == SignScheme.STREET_NAME ? "street_name_sign" : "direction_sign_" + scheme.name().toLowerCase();
            DIRECTION_SIGNS.put(id, BLOCKS.register(id, () -> new DirectionSignBlock(scheme)));
        }
        MOTORWAY.put("gantry_beam", BLOCKS.register("gantry_beam", GantryBeamBlock::new));
        MOTORWAY.put("gantry_leg", BLOCKS.register("gantry_leg", GantryLegBlock::new));
        MOTORWAY.put("lane_signal", BLOCKS.register("lane_signal", LaneSignalBlock::new));
        VMS = BLOCKS.register("matrix_sign", VmsBlock::new);
        MOTORWAY.put("matrix_sign", VMS);
        for (BuildingMaterial m : BuildingMaterial.values()) {
            BUILDING.put(m.id(), BLOCKS.register(m.id(), () -> new Block(m.properties())));
        }
        for (BuildingMaterial m : BuildingMaterial.values()) {
            BUILDING.put(m.id() + "_slab", BLOCKS.register(m.id() + "_slab", () -> new SlabBlock(m.properties())));
        }
        for (String name : PylonCells.COUNTS.keySet()) {
            PYLONS.put(name, BLOCKS.register(name, () -> new PylonBlock(name)));
        }
    }

    private static void fence(String id, SoundType sound) {
        FENCES.put(id, BLOCKS.register(id, () -> new UkFence(sound)));
    }

    /** The head styles each signal type really comes in on UK roads. */
    public static Set<SignalStyle> stylesFor(SignalType type) {
        return switch (type) {
            case CYCLE, LOW_LEVEL_CYCLE, PUFFIN, TOUCAN -> EnumSet.of(SignalStyle.LED);
            case PELICAN -> EnumSet.of(SignalStyle.LED, SignalStyle.CLASSIC);
            case STANDARD -> EnumSet.allOf(SignalStyle.class);
            default -> EnumSet.of(SignalStyle.LED, SignalStyle.LED_TUNNEL, SignalStyle.CLASSIC);
        };
    }

    public static RegistryObject<Block> signal(SignalStyle style, SignalType type) {
        return SIGNALS.get(style.id() + "_" + type.id());
    }

    private ModBlocks() {
    }
}
