package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.AccessoryType;
import com.ptmuk.block.FurnitureBlock;
import com.ptmuk.block.FurnitureType;
import com.ptmuk.block.PoleType;
import com.ptmuk.block.SignalAccessory;
import com.ptmuk.block.SignalStyle;
import com.ptmuk.block.SignalType;
import com.ptmuk.block.UkFence;
import com.ptmuk.block.UkPole;
import com.ptmuk.block.UkTrafficSignal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Block;
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
    /** Fences and railings. */
    public static final Map<String, RegistryObject<Block>> FENCES = new LinkedHashMap<>();
    /** Every accessory, in creative-tab order. */
    public static final Map<String, RegistryObject<Block>> ACCESSORIES = new LinkedHashMap<>();

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
            FURNITURE.put(type.id(), BLOCKS.register(type.id(), () -> new FurnitureBlock(type)));
        }
        fence("palisade_fence", SoundType.METAL);
        fence("black_railings", SoundType.METAL);
        fence("pedestrian_guardrail", SoundType.METAL);
        fence("close_board_fence", SoundType.WOOD);
        fence("heras_fence", SoundType.METAL);
        for (AccessoryType type : AccessoryType.values()) {
            ACCESSORIES.put(type.id(), BLOCKS.register(type.id(), () -> new SignalAccessory(type)));
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
