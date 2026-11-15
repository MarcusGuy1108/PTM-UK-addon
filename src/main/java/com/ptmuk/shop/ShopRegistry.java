package com.ptmuk.shop;

import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.registry.ModBlockEntities;
import com.ptmuk.registry.ModItems;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/** The shops' tills' block entity, the shop keepers, and everything the shops sell. */
public final class ShopRegistry {
    public static final RegistryObject<BlockEntityType<ShopBlockEntity>> SHOP_BE = ModBlockEntities.BLOCK_ENTITIES.register("shop_till",
            () -> BlockEntityType.Builder.of(ShopBlockEntity::new, HomeBlocks.BLOCKS.get(HomeFurnitureType.TILL).get(),
                    HomeBlocks.BLOCKS.get(HomeFurnitureType.CHECKOUT).get()).build(null));
    public static final RegistryObject<BlockEntityType<SelfCheckoutBlockEntity>> SELF_CHECKOUT_BE = ModBlockEntities.BLOCK_ENTITIES.register(
            "self_checkout", () -> BlockEntityType.Builder.of(SelfCheckoutBlockEntity::new,
                    HomeBlocks.BLOCKS.get(HomeFurnitureType.SELF_CHECKOUT).get()).build(null));
    public static final RegistryObject<EntityType<ShopKeeperEntity>> SHOP_KEEPER = HomeBlocks.ENTITIES.register("shop_keeper",
            () -> EntityType.Builder.<ShopKeeperEntity>of(ShopKeeperEntity::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).build("shop_keeper"));
    public static final Map<Product, RegistryObject<Item>> ITEMS = new EnumMap<>(Product.class);

    static {
        for (Product p : Product.values()) {
            if (!p.service()) {
                RegistryObject<Item> item = ModItems.ITEMS.register(p.id(), () -> new ProductItem(p));
                ITEMS.put(p, item);
                ModItems.MONEY_ITEMS.add(item);
            }
        }
    }

    public static Item item(Product p) {
        return ITEMS.get(p).get();
    }

    /** Loads this class, so everything above is registered with the mod's registers. */
    public static void init() {
    }

    private ShopRegistry() {
    }
}
