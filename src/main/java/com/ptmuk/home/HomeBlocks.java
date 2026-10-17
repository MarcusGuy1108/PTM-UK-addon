package com.ptmuk.home;

import com.ptmuk.PtmUk;
import com.ptmuk.registry.ModBlockEntities;
import com.ptmuk.registry.ModBlocks;
import com.ptmuk.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the home furniture blocks, their items (UK Home Furniture tab, and the shop and cinema
 * fittings in a tab of their own), the storage and oven block entities and the seat entity. */
public final class HomeBlocks {
    public static final Map<HomeFurnitureType, RegistryObject<Block>> BLOCKS = new EnumMap<>(HomeFurnitureType.class);
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Shop and cinema fittings, in a tab of their own. */
    public static final List<RegistryObject<Item>> SHOP_ITEMS = new ArrayList<>();
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PtmUk.MOD_ID);

    static {
        for (HomeFurnitureType type : HomeFurnitureType.values()) {
            RegistryObject<Block> block = ModBlocks.BLOCKS.register(type.id, () -> create(type));
            BLOCKS.put(type, block);
            boolean shop = type.group == HomeFurnitureType.Group.SHOP || type.group == HomeFurnitureType.Group.CINEMA;
            (shop ? SHOP_ITEMS : ITEMS).add(ModItems.ITEMS.register(type.id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<BlockEntityType<HomeStorageBlockEntity>> STORAGE_BE = ModBlockEntities.BLOCK_ENTITIES.register(
            "home_storage", () -> BlockEntityType.Builder.of(HomeStorageBlockEntity::new, blocks(t -> t.rows > 0 && !t.oven)).build(null));
    public static final RegistryObject<BlockEntityType<OvenBlockEntity>> OVEN_BE = ModBlockEntities.BLOCK_ENTITIES.register(
            "home_oven", () -> BlockEntityType.Builder.of(OvenBlockEntity::new, blocks(t -> t.oven)).build(null));
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.01F, 0.01F)
                    .clientTrackingRange(10).noSummon().build("seat"));

    private static Block create(HomeFurnitureType type) {
        return switch (type.kind) {
            case TABLETOP -> new TabletopHomeBlock(type);
            case SOFA -> new SofaBlock(type);
            case TABLE -> new HomeTableBlock(type);
            case TALL -> new TallHomeBlock(type);
            case WIDE -> new WideHomeBlock(type);
            default -> new HomeBlock(type);
        };
    }

    private static Block[] blocks(java.util.function.Predicate<HomeFurnitureType> which) {
        return BLOCKS.entrySet().stream().filter(e -> which.test(e.getKey())).map(e -> e.getValue().get()).toArray(Block[]::new);
    }

    /** Loads this class, so everything above is registered with the mod's registers. */
    public static void init() {
    }

    private HomeBlocks() {
    }
}
