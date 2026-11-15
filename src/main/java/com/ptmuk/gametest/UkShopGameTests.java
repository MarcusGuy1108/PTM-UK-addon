package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.home.HomeBlocks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.HomeStorageBlockEntity;
import com.ptmuk.money.Bank;
import com.ptmuk.money.CashItem;
import com.ptmuk.registry.ModItems;
import com.ptmuk.shop.Product;
import com.ptmuk.shop.ProductItem;
import com.ptmuk.shop.ShopBlockEntity;
import com.ptmuk.shop.ShopKeeperEntity;
import com.ptmuk.shop.ShopKind;
import com.ptmuk.shop.ShopRegistry;
import com.rinventor.ptm2.objects.items.BankCard;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** The shops: what kind a till works out it is, buying off the shelves at a counter, and ordering at a chippy. */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkShopGameTests {
    private UkShopGameTests() {
    }

    @GameTest(template = "empty5x4x5")
    public static void tillsKnowWhatShopTheyAre(GameTestHelper helper) {
        helper.assertTrue(ShopKind.detect(Set.of(HomeFurnitureType.FRYER), "", false) == ShopKind.CHIPPY, "a fryer: a chippy");
        helper.assertTrue(ShopKind.detect(Set.of(), "THE CROWN & ANCHOR", false) == ShopKind.PUB, "the Crown: a pub");
        helper.assertTrue(ShopKind.detect(Set.of(), "LINDY'S NAILS", false) == ShopKind.NAIL_BAR, "nails: a nail bar");
        helper.assertTrue(ShopKind.detect(Set.of(HomeFurnitureType.BARBER_CHAIR), "", false) == ShopKind.HAIRDRESSER, "a barber's chair");
        helper.assertTrue(ShopKind.detect(EnumSet.of(HomeFurnitureType.SHELVES_GROCERIES), "", false) == ShopKind.CORNER_SHOP, "shelves: a corner shop");
        helper.assertTrue(ShopKind.detect(EnumSet.of(HomeFurnitureType.SHELVES_GROCERIES), "", true) == ShopKind.SUPERMARKET, "a checkout: a supermarket");
        helper.assertTrue(ShopKind.detect(Set.of(), "HEARTS HOSPICE SHOP", false) == ShopKind.CHARITY_SHOP, "a hospice shop: charity");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "shop-test"));
    }

    private static ShopBlockEntity till(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockState till = HomeBlocks.BLOCKS.get(HomeFurnitureType.TILL).get().defaultBlockState();
        helper.setBlock(new BlockPos(2, 1, 2), till);
        return (ShopBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 200)
    public static void buyingAtACornerShop(GameTestHelper helper) {
        ShopBlockEntity shop = till(helper);
        helper.setBlock(new BlockPos(0, 1, 0), HomeBlocks.BLOCKS.get(HomeFurnitureType.SHELVES_GROCERIES).get().defaultBlockState());
        shop.configure(ShopKind.CORNER_SHOP, "Test Stores", false);
        shop.open(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ShopKeeperEntity.class, new AABB(at).inflate(3)).isEmpty(), "someone should be at the till");
        HomeStorageBlockEntity shelf = (HomeStorageBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        ItemStack first = shelf.getItem(0);
        helper.assertTrue(first.getItem() instanceof ProductItem && ProductItem.unpaid(first) && ProductItem.price(first) > 0,
                "the shelf should be stocked, priced and unpaid: " + first);
        ServerPlayer p = player(helper);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getInventory().add(new ItemStack(ModItems.CASH.get(2000).get()));     // in hand, to pay with
        ItemStack taken = shelf.removeItem(0, 2);
        long price = 2L * ProductItem.price(taken);
        p.getInventory().add(taken);
        shop.talk(p, null);
        helper.assertTrue(p.getInventory().items.stream().noneMatch(ProductItem::unpaid), "the things should be handed over");
        for (int i = 0; i < 40; i++) {
            ShopBlockEntity.tick(helper.getLevel(), at, shop.getBlockState(), shop);
        }
        shop.talk(p, null);
        long carried = CashItem.carried(p);
        helper.assertTrue(carried == 2000 - price, "change from £20 for " + CashItem.money(price) + ": has " + CashItem.money(carried));
        long bought = p.getInventory().items.stream().filter(s -> s.getItem() instanceof ProductItem && !ProductItem.unpaid(s)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(bought == 2, "both should be handed back, paid for: " + bought);
        helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ModItems.RECEIPT.get())), "a receipt");
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 300)
    public static void orderingAtAChippy(GameTestHelper helper) {
        ShopBlockEntity shop = till(helper);
        shop.configure(ShopKind.CHIPPY, "The Golden Plaice", false);
        shop.open(helper.getLevel());
        ServerPlayer p = player(helper);
        p.getInventory().clearContent();
        Bank.add(p, 5000, "test");
        long before = Bank.balance(p);
        int[] order = new int[shop.offers().size()];
        order[0] = 1;                                       // cod and chips
        shop.order(p, order);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValues().stream()
                .filter(i -> i instanceof BankCard).findFirst().orElseThrow()));
        shop.talk(p, null);
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        long price = shop.price(Product.FISH_AND_CHIPS);
        helper.assertTrue(before == Long.MAX_VALUE || Bank.balance(p) == before - price, "paid by card: " + before + " -> " + Bank.balance(p));
        for (int i = 0; i < 120; i++) {
            ShopBlockEntity.tick(helper.getLevel(), at, shop.getBlockState(), shop);
        }
        helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ShopRegistry.item(Product.FISH_AND_CHIPS))), "cod and chips, fried and handed over");
        helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ModItems.RECEIPT.get())), "a receipt");
        helper.succeed();
    }
}
