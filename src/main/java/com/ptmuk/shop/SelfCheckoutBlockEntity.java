package com.ptmuk.shop;

import com.ptmuk.money.Bank;
import com.ptmuk.money.CashItem;
import com.ptmuk.money.ReceiptItem;
import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.objects.items.BankCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A self-checkout's own session, kept in the machine (closing its menu loses nothing): the
 * customer, what they've scanned (held in the bagging area until it's paid for), loose things
 * looked up or keyed in, bags, and whether they want a receipt. Its screen in the world shows it
 * all. Scan by clicking the machine with the thing in your hand (or in its menu), pay by clicking
 * it with your bank card or cash; sneak and click with an empty hand to cancel (your things back).
 */
public class SelfCheckoutBlockEntity extends BlockEntity {
    /** Each carrier bag. */
    public static final int BAG = 10;
    private static final int IDLE = 20 * 60 * 5;

    /** A loose thing looked up or keyed in: what, how many, at what each. */
    public record Extra(Product product, int count, int each) {
    }

    private UUID customer;
    private String customerName = "";
    private final List<ItemStack> scanned = new ArrayList<>();
    private final List<Extra> extras = new ArrayList<>();
    private int bags, assistTicks, thanksTicks, idle;
    private boolean receipt = true, ageChecked, bagged;
    private String assist = "", thanks = "", shopName = "";
    private BlockPos till;

    public SelfCheckoutBlockEntity(BlockPos pos, BlockState state) {
        super(ShopRegistry.SELF_CHECKOUT_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------ read by the screens

    public List<ItemStack> scanned() {
        return scanned;
    }

    public List<Extra> extras() {
        return extras;
    }

    public int bags() {
        return bags;
    }

    public boolean receipt() {
        return receipt;
    }

    public String assist() {
        return assist;
    }

    public String thanks() {
        return thanks;
    }

    public String shopName() {
        return shopName.isBlank() ? "Supermarket" : shopName;
    }

    public String customerName() {
        return customerName;
    }

    public boolean busy() {
        return customer != null;
    }

    public long total() {
        long t = (long) bags * BAG;
        for (ItemStack s : scanned) {
            t += (long) ProductItem.price(s) * s.getCount();
        }
        for (Extra e : extras) {
            t += (long) e.each() * e.count();
        }
        return t;
    }

    public int count() {
        int n = bags;
        for (ItemStack s : scanned) {
            n += s.getCount();
        }
        for (Extra e : extras) {
            n += e.count();
        }
        return n;
    }

    // ------------------------------------------------------------------ the shop it's in

    private ShopBlockEntity till() {
        if (till != null && level.getBlockEntity(till) instanceof ShopBlockEntity s) {
            return s;
        }
        till = null;
        double best = Double.MAX_VALUE;
        ShopBlockEntity found = null;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-16, -2, -16), worldPosition.offset(16, 3, 16))) {
            if (level.getBlockEntity(p) instanceof ShopBlockEntity s && p.distSqr(worldPosition) < best) {
                best = p.distSqr(worldPosition);
                till = p.immutable();
                found = s;
            }
        }
        return found;
    }

    /** Something unpaid from the shops round here (their tills within 48 blocks). */
    public boolean fromHere(ItemStack s) {
        BlockPos from = ProductItem.shop(s);
        return ProductItem.unpaid(s) && from != null && from.distSqr(worldPosition) <= 48 * 48;
    }

    // ------------------------------------------------------------------ what customers do

    /** Whose turn it is: someone else's (with a session going) and they're told to wait. */
    private boolean mine(Player p) {
        if (customer == null || customer.equals(p.getUUID())) {
            if (customer == null) {
                customer = p.getUUID();
                customerName = p.getName().getString();
                ShopBlockEntity t = till();
                shopName = t != null ? t.shopName() : "";
            }
            idle = 0;
            return true;
        }
        p.displayClientMessage(Component.literal("This checkout's in use. Try the next one.").withStyle(ChatFormatting.RED), true);
        return false;
    }

    /** Scans a stack (it goes into the bagging area). False if it can't be scanned now. */
    public boolean scan(Player p, ItemStack stack) {
        if (!fromHere(stack) || !mine(p)) {
            return false;
        }
        if (!assist.isEmpty()) {
            p.displayClientMessage(Component.literal("Please wait: a colleague is on their way").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        scanned.add(stack.copy());
        stack.setCount(0);
        sound(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f);
        boolean age = !ageChecked && scanned.get(scanned.size() - 1).getItem() instanceof ProductItem pi
                && (pi.product == Product.ENERGY_DRINK || pi.product == Product.PARACETAMOL);
        if (age) {
            ageChecked = true;
            hold("Age check: a colleague will approve it");
        } else if (!bagged && scanned.size() >= 2 && level.random.nextInt(5) == 0) {
            bagged = true;
            hold("Unexpected item in the bagging area");
        }
        changed();
        return true;
    }

    /** Everything unpaid from here on the player, scanned. */
    public void scanAll(Player p) {
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (fromHere(s) && !scan(p, s)) {
                    return;
                }
            }
        }
    }

    public void extra(Player p, Product product, int delta) {
        if (!product.onShelf() || !mine(p)) {
            return;
        }
        ShopBlockEntity t = till();
        int each = t != null ? t.price(product) : product.pence;
        for (int i = 0; i < extras.size(); i++) {
            Extra e = extras.get(i);
            if (e.product() == product) {
                int n = Math.max(0, Math.min(20, e.count() + delta));
                if (n == 0) {
                    extras.remove(i);
                } else {
                    extras.set(i, new Extra(product, n, e.each()));
                }
                if (delta < 0) {
                    hold("Removing an item: a colleague is approving it");
                } else {
                    sound(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f);
                }
                changed();
                return;
            }
        }
        if (delta > 0) {
            extras.add(new Extra(product, Math.min(20, delta), each));
            sound(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f);
            changed();
        }
    }

    public void bags(Player p, int delta) {
        if (mine(p)) {
            bags = Math.max(0, Math.min(9, bags + delta));
            changed();
        }
    }

    public void toggleReceipt(Player p) {
        if (mine(p)) {
            receipt = !receipt;
            changed();
        }
    }

    public void help(Player p) {
        if (mine(p)) {
            hold("A colleague is on their way to help");
            changed();
        }
    }

    /** Pays (by card, or cash with change) and hands everything over, paid for. */
    public SelfCheckout.Reply pay(Player p, boolean card) {
        if (customer == null || !customer.equals(p.getUUID()) || count() == 0) {
            return new SelfCheckout.Reply(SelfCheckout.Result.NOTHING, 0, 0);
        }
        if (!assist.isEmpty()) {
            p.displayClientMessage(Component.literal("Please wait: a colleague is on their way").withStyle(ChatFormatting.GOLD), true);
            return new SelfCheckout.Reply(SelfCheckout.Result.WAIT, total(), 0);
        }
        long total = total(), change = 0;
        if (card) {
            boolean has = p.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof BankCard) || p.getOffhandItem().getItem() instanceof BankCard;
            if (!has) {
                return new SelfCheckout.Reply(SelfCheckout.Result.NO_CARD, total, 0);
            }
            if (!Bank.canPay(p, total)) {
                sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
                return new SelfCheckout.Reply(SelfCheckout.Result.DECLINED, total, 0);
            }
            Bank.pay(p, total, shopName());
        } else {
            change = CashItem.pay(p, total);
            if (change < 0) {
                sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
                return new SelfCheckout.Reply(SelfCheckout.Result.SHORT, total, 0);
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(shopName().toUpperCase(Locale.ROOT) + "  SELF-CHECKOUT");
        for (ItemStack s : scanned) {
            lines.add((s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString() + "  "
                    + CashItem.money((long) ProductItem.price(s) * s.getCount()));
            ItemStack mine = s.copy();
            ProductItem.paid(mine);
            give(p, mine);
        }
        for (Extra e : extras) {
            lines.add((e.count() > 1 ? e.count() + " x " : "") + e.product().title + "  " + CashItem.money((long) e.each() * e.count()));
            give(p, new ItemStack(ShopRegistry.item(e.product()), e.count()));
        }
        if (bags > 0) {
            lines.add((bags > 1 ? bags + " x " : "") + "Carrier bag  " + CashItem.money((long) bags * BAG));
        }
        lines.add("TOTAL  " + CashItem.money(total));
        lines.add(card ? "CARD (CONTACTLESS)" : "CASH  CHANGE " + CashItem.money(change));
        lines.add("THANK YOU FOR SHOPPING WITH US");
        if (receipt) {
            give(p, ReceiptItem.make(ModItems.RECEIPT.get(), shopName(), lines));
        }
        sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f);
        thanks = "Thank you for shopping with us";
        thanksTicks = 80;
        clear();
        return new SelfCheckout.Reply(SelfCheckout.Result.OK, total, change);
    }

    /** The session's off: the scanned things go back to the customer (still unpaid). */
    public void cancel(Player p) {
        if (customer != null && customer.equals(p.getUUID())) {
            for (ItemStack s : scanned) {
                give(p, s.copy());
            }
            clear();
            p.displayClientMessage(Component.literal("Transaction cancelled: your things are back with you").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void clear() {
        customer = null;
        customerName = "";
        scanned.clear();
        extras.clear();
        bags = 0;
        receipt = true;
        ageChecked = false;
        bagged = false;
        assist = "";
        changed();
    }

    private void give(Player p, ItemStack s) {
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
    }

    private void hold(String why) {
        assist = why;
        assistTicks = 60;
        sound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f);
    }

    private void sound(SoundEvent s, float pitch) {
        level.playSound(null, worldPosition, s, SoundSource.BLOCKS, 0.6f, pitch);
    }

    public void tick() {
        if (assistTicks > 0 && --assistTicks == 0) {
            assist = "";
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f);
            changed();
        }
        if (thanksTicks > 0 && --thanksTicks == 0) {
            thanks = "";
            changed();
        }
        if (customer != null && ++idle > IDLE) {                 // walked off: their things back to them, or on the counter
            Player p = level.getPlayerByUUID(customer);
            if (p != null && p.distanceToSqr(worldPosition.getCenter()) < 64 * 64) {
                cancel(p);
            } else {
                for (ItemStack s : scanned) {
                    net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, s);
                }
                clear();
            }
        }
    }

    // ------------------------------------------------------------------ saved, sent

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    private void write(CompoundTag tag) {
        if (customer != null) {
            tag.putUUID("customer", customer);
        }
        tag.putString("name", customerName);
        tag.putString("shop", shopName);
        ListTag list = new ListTag();
        scanned.forEach(s -> list.add(s.save(new CompoundTag())));
        tag.put("scanned", list);
        ListTag ex = new ListTag();
        for (Extra e : extras) {
            CompoundTag c = new CompoundTag();
            c.putString("p", e.product().name());
            c.putInt("n", e.count());
            c.putInt("each", e.each());
            ex.add(c);
        }
        tag.put("extras", ex);
        tag.putInt("bags", bags);
        tag.putBoolean("receipt", receipt);
        tag.putString("assist", assist);
        tag.putString("thanks", thanks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        customer = tag.hasUUID("customer") ? tag.getUUID("customer") : null;
        customerName = tag.getString("name");
        shopName = tag.getString("shop");
        scanned.clear();
        for (Tag t : tag.getList("scanned", Tag.TAG_COMPOUND)) {
            scanned.add(ItemStack.of((CompoundTag) t));
        }
        extras.clear();
        for (Tag t : tag.getList("extras", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                extras.add(new Extra(Product.valueOf(c.getString("p")), c.getInt("n"), c.getInt("each")));
            } catch (IllegalArgumentException ignored) {
                // a product that's gone
            }
        }
        bags = tag.getInt("bags");
        receipt = !tag.contains("receipt") || tag.getBoolean("receipt");
        assist = tag.getString("assist");
        thanks = tag.getString("thanks");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 1, 0);
    }
}
