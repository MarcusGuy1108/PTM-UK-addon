package com.ptmuk.darts;

import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.registry.ModBlockEntities;
import com.ptmuk.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/** Darts: the board's block entity, the darts, and a dart in flight. */
public final class DartsRegistry {
    public static final RegistryObject<BlockEntityType<DartboardBlockEntity>> BOARD_BE = ModBlockEntities.BLOCK_ENTITIES.register("dartboard",
            () -> BlockEntityType.Builder.of(DartboardBlockEntity::new, HomeBlocks.BLOCKS.get(HomeFurnitureType.DARTBOARD).get()).build(null));
    public static final RegistryObject<EntityType<DartEntity>> DART = HomeBlocks.ENTITIES.register("dart",
            () -> EntityType.Builder.<DartEntity>of(DartEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(4).updateInterval(20).build("dart"));
    public static final RegistryObject<Item> DARTS = ModItems.ITEMS.register("darts", DartsItem::new);

    static {
        HomeBlocks.SHOP_ITEMS.add(DARTS);
    }

    /** Loads this class, so everything above is registered with the mod's registers. */
    public static void init() {
    }

    private DartsRegistry() {
    }
}
