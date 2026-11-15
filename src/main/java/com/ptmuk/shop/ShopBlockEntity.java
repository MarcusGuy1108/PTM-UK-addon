package com.ptmuk.shop;

import com.ptmuk.building.ShopSignBlockEntity;
import com.ptmuk.home.HomeBlock;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.HomeStorageBlockEntity;
import com.ptmuk.money.CashItem;
import com.ptmuk.money.ReceiptItem;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.ShopMenuMessage;
import com.ptmuk.registry.ModItems;
import com.ptmuk.money.Bank;
import com.rinventor.ptm2.objects.items.BankCard;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

/**
 * A shop's till (a counter till or a supermarket checkout): which shop it is (worked out from the
 * fittings round it and its sign, or set by hand), its name, the person who works it, its prices
 * (its own, near the usual ones) and stock. It fills the shop's shelves each morning with priced,
 * unpaid things; you hand them over and they're scanned, or you order from the menu at the
 * counter; you pay by card (from your PTM2 bank account) or cash (with change), and get your
 * things (made while you wait at a chippy or café) and a receipt.
 */
public class ShopBlockEntity extends BlockEntity {
    private enum State { IDLE, SCANNING, PAYING, MAKING }

    /** One thing in a sale: its name, price, and what you're given (empty for a service). */
    public record Line(String title, int pence, ItemStack give) {
    }

    private static final String[] WOMEN = {"Sandra", "Priya", "Jade", "Linda", "Aisha", "Sophie", "Emma", "Fatima", "Chloe", "Grace", "Margaret",
            "Kerry", "Megan", "Paula", "Zara", "Bev", "Shaz", "Niamh", "Amira", "Gemma"};
    private static final String[] MEN = {"Dave", "Tom", "Gary", "Mohammed", "Kev", "Raj", "Liam", "Steve", "Darren", "Tariq", "Ben", "Olu",
            "Sean", "Hassan", "Callum", "Pete", "Wes", "Arjun", "Rhys", "Tony"};

    private ShopKind kind;
    private boolean chosen;
    private String shopName = "", keeperName = "";
    private int skin = -1;
    private UUID keeper;
    private long seed, stockedDay = -1;
    private State state = State.IDLE;
    private UUID customer;
    private final List<Line> basket = new ArrayList<>();
    private int timer, scanned, away;
    private long total;
    private String paidBy = "";
    private long change;

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(ShopRegistry.SHOP_BE.get(), pos, state);
    }

    private boolean checkout() {
        return getBlockState().getBlock() instanceof CheckoutBlock;
    }

    public ShopKind kind() {
        return kind == null ? ShopKind.SHOP : kind;
    }

    public boolean chosen() {
        return chosen;
    }

    public String shopName() {
        return shopName;
    }

    public String keeperName() {
        return keeperName;
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(Level level, BlockPos pos, BlockState state, ShopBlockEntity shop) {
        if (level instanceof ServerLevel server) {
            shop.serverTick(server);
        }
    }

    private void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            setUp(level);
            if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, 32, false) != null) {
                open(level);
            }
            Player who = customer == null ? null : level.getPlayerByUUID(customer);
            if (state != State.IDLE && state != State.MAKING) {
                away = who == null || who.distanceToSqr(worldPosition.getCenter()) > 144 ? away + 1 : 0;
                if (away > 30) {
                    giveBack(who);
                }
            }
        }
        switch (state) {
            case SCANNING -> {
                if (++timer >= 8) {
                    timer = 0;
                    Player who = level.getPlayerByUUID(customer);
                    Line l = basket.get(scanned++);
                    total += l.pence();
                    sound(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f, 0.5f);
                    if (who != null) {
                        who.displayClientMessage(Component.literal(l.title() + "  " + CashItem.money(l.pence()) + "   (" + scanned + " of " + basket.size() + ")")
                                .withStyle(ChatFormatting.AQUA), true);
                    }
                    if (scanned >= basket.size()) {
                        state = State.PAYING;
                        say(who, pick("That's " + CashItem.money(total) + ", please.", "That'll be " + CashItem.money(total) + ", love.",
                                CashItem.money(total) + " please. Cash or card?"));
                        hint(who);
                    }
                }
            }
            case MAKING -> {
                timer--;
                if (timer % 20 == 0 && timer > 0) {
                    makingSound();
                }
                if (timer <= 0) {
                    Player who = customer == null ? null : level.getPlayerByUUID(customer);
                    handOver(who);
                }
            }
            default -> {
            }
        }
    }

    /** Open for business: the shop's person at the till, and (each morning) its shelves filled. */
    public void open(ServerLevel level) {
        setUp(level);
        staff(level);
        long day = level.getDayTime() / 24000;
        if (stockedDay != day) {
            stockedDay = day;
            restock(level);
            setChanged();
        }
    }

    /** Works out the shop, its name and its person the first time (and finds its name again if its sign's changed). */
    private void setUp(ServerLevel level) {
        if (seed == 0) {
            seed = level.getRandom().nextLong() | 1;
        }
        String sign = signText(level);
        if (!chosen) {
            ShopKind k = ShopKind.detect(fittings(level), sign, checkout());
            if (k != kind) {
                kind = k;
                setChanged();
            }
            String name = sign.isBlank() ? kind().title : sign;
            if (!name.equals(shopName)) {
                shopName = name;
                setChanged();
            }
        }
        if (skin < 0) {
            newFace();
        }
    }

    public void newFace() {
        int person = (int) Math.floorMod(seed >>> 7, ShopKeeperEntity.PEOPLE);
        skin = kind().outfit * ShopKeeperEntity.PEOPLE + person;
        String[] names = ShopKeeperEntity.longHair(skin) ? WOMEN : MEN;
        keeperName = names[(int) Math.floorMod(seed >>> 13, names.length)];
        setChanged();
    }

    private Set<HomeFurnitureType> fittings(Level level) {
        Set<HomeFurnitureType> out = EnumSet.noneOf(HomeFurnitureType.class);
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-10, -2, -10), worldPosition.offset(10, 3, 10))) {
            if (level.getBlockState(p).getBlock() instanceof HomeBlock hb) {
                out.add(hb.type());
            }
        }
        return out;
    }

    private String signText(Level level) {
        String best = "";
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-16, -3, -16), worldPosition.offset(16, 8, 16))) {
            if (level.getBlockEntity(p) instanceof ShopSignBlockEntity sign && !sign.text.isBlank()) {
                double d = p.distSqr(worldPosition);
                if (d < bestD) {
                    bestD = d;
                    best = sign.text.trim();
                }
            }
        }
        return best;
    }

    /** Someone at the till: the one there already, or a new one where they'd stand (behind it, else in front). */
    private void staff(ServerLevel level) {
        if (keeper != null && level.getEntity(keeper) instanceof ShopKeeperEntity k && k.isAlive()) {
            return;
        }
        for (ShopKeeperEntity k : level.getEntitiesOfClass(ShopKeeperEntity.class, new AABB(worldPosition).inflate(4))) {
            if (k.till().equals(worldPosition)) {
                keeper = k.getUUID();
                return;
            }
        }
        BlockState state = getBlockState();
        Direction front = state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        for (Direction d : new Direction[]{front.getOpposite(), front}) {
            BlockPos at = worldPosition.relative(d);
            if (!level.isLoaded(at)) {
                return;
            }
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos feet = at.above(dy);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                    ShopKeeperEntity k = ShopRegistry.SHOP_KEEPER.get().create(level);
                    if (k == null) {
                        return;
                    }
                    Direction facing = d.getOpposite();
                    k.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, facing.toYRot(), 0);
                    k.setYHeadRot(facing.toYRot());
                    k.setup(worldPosition, skin, keeperName);
                    level.addFreshEntity(k);
                    keeper = k.getUUID();
                    setChanged();
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ prices and stock

    /** This shop's price for a product: near the usual one (dearer at a corner shop), ending in 5p, often 9p. */
    public int price(Product p) {
        long h = (seed ^ (p.ordinal() * 0x9E3779B97F4A7C15L)) * 0xBF58476D1CE4E5B9L;
        double f = 0.92 + 0.16 * ((h >>> 11) & 0xFFFF) / 65535.0;
        int pence = (int) Math.max(5, Math.round(p.pence * kind().dearness * f / 5.0) * 5);
        return pence > 100 && ((h >>> 40) & 1) == 1 ? pence - 1 : pence;
    }

    /** Fills the empty slots of the shop's shelves (in reach of the till) with what goes on each, priced. */
    private void restock(ServerLevel level) {
        if (kind().counter) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-12, -2, -12), worldPosition.offset(12, 4, 12))) {
            if (!(level.getBlockState(p).getBlock() instanceof HomeBlock hb) || ShopKind.Shelves.of(hb.type()) == null
                    || !(level.getBlockEntity(p) instanceof HomeStorageBlockEntity store)) {
                continue;
            }
            Product.Shelf shelf = ShopKind.Shelves.of(hb.type());
            List<Product> goods = new ArrayList<>();
            for (Product pr : Product.values()) {
                if (pr.shelf == shelf) {
                    goods.add(pr);
                }
            }
            int fill = Math.min(store.getContainerSize(), 18);
            for (int slot = 0; slot < fill; slot++) {
                if (!store.getItem(slot).isEmpty()) {
                    continue;
                }
                Product pr = goods.get(Math.floorMod(slot + p.hashCode(), goods.size()));
                int count = 2 + Math.floorMod((int) (seed >>> 3) + slot * 7 + p.hashCode(), 5);
                store.setItem(slot, ProductItem.priced(new ItemStack(ShopRegistry.item(pr), count), price(pr), worldPosition));
            }
            store.setChanged();
        }
    }

    /** What's on offer at the counter: the menu, or today's things on the rails. */
    public List<Line> offers() {
        List<Line> out = new ArrayList<>();
        if (kind().rails()) {
            long day = level == null ? 0 : level.getDayTime() / 24000;
            java.util.Random r = new java.util.Random(seed * 31 + day);
            boolean charity = kind() == ShopKind.CHARITY_SHOP;
            Object[][] things = charity
                    ? new Object[][]{{"Paperback book", 150, Items.BOOK}, {"Hardback book", 250, Items.BOOK}, {"Vinyl record", 400, null},
                    {"Knitted jumper", 350, Items.LEATHER_CHESTPLATE}, {"Jeans", 400, Items.LEATHER_LEGGINGS}, {"Wellies", 300, Items.LEATHER_BOOTS},
                    {"Woolly hat", 150, Items.LEATHER_HELMET}, {"Picture", 200, Items.PAINTING}, {"Candle", 150, Items.CANDLE},
                    {"Vase", 250, Items.DECORATED_POT}, {"Plant pot", 100, Items.FLOWER_POT}}
                    : new Object[][]{{"Jumper", 2500, Items.LEATHER_CHESTPLATE}, {"Jeans", 3000, Items.LEATHER_LEGGINGS},
                    {"Boots", 4000, Items.LEATHER_BOOTS}, {"Beanie hat", 1200, Items.LEATHER_HELMET}, {"Hoodie", 2800, Items.LEATHER_CHESTPLATE},
                    {"Joggers", 2200, Items.LEATHER_LEGGINGS}, {"Trainers", 4500, Items.LEATHER_BOOTS}};
            List<Object[]> list = new ArrayList<>(List.of(things));
            java.util.Collections.shuffle(list, r);
            for (Object[] t : list.subList(0, Math.min(charity ? 7 : 6, list.size()))) {
                ItemStack give;
                String title = (String) t[0];
                if (t[2] == null) {
                    List<Item> discs = new ArrayList<>(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                            .filter(i -> i instanceof net.minecraft.world.item.RecordItem).toList());
                    give = new ItemStack(discs.get(r.nextInt(discs.size())));
                } else {
                    give = new ItemStack((Item) t[2]);
                }
                if (give.getItem() instanceof DyeableLeatherItem dye) {
                    DyeColor c = DyeColor.values()[r.nextInt(DyeColor.values().length)];
                    float[] rgb = c.getTextureDiffuseColors();
                    dye.setColor(give, ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255));
                    title = title + " (" + c.getName().replace('_', ' ') + ")";
                }
                give.setHoverName(Component.literal(title).withStyle(s -> s.withItalic(false)));
                int pence = (int) t[1];
                if (!charity) {
                    pence = pence - 1;                      // £24.99
                }
                out.add(new Line(title, pence, give));
            }
            return out;
        }
        for (Product p : kind().menu) {
            out.add(new Line(p.title, price(p), p.service() ? ItemStack.EMPTY : new ItemStack(ShopRegistry.item(p))));
        }
        return out;
    }

    // ------------------------------------------------------------------ serving

    /** The customer talks to the shop's person (or clicks the till): hand over, order, or pay. */
    public void talk(Player player, ShopKeeperEntity who) {
        if (level == null || level.isClientSide) {
            return;
        }
        setUp((ServerLevel) level);
        if (state != State.IDLE && !player.getUUID().equals(customer)) {
            say(player, "Sorry, I'm just serving someone. Won't be a minute!");
            return;
        }
        switch (state) {
            case IDLE -> {
                if (kind().counter) {
                    say(player, pick("What can I get you?", "Hiya, what are you after?", "Morning! What'll it be?"));
                    if (player instanceof ServerPlayer sp) {
                        PtmUkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ShopMenuMessage(worldPosition, shopName, keeperName, offers()));
                    }
                    return;
                }
                List<Line> handed = new ArrayList<>();
                for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
                    for (int i = 0; i < list.size(); i++) {
                        ItemStack s = list.get(i);
                        BlockPos from = ProductItem.shop(s);
                        if (ProductItem.unpaid(s) && from != null && from.distSqr(worldPosition) <= 48 * 48) {
                            for (int n = 0; n < s.getCount(); n++) {
                                ItemStack one = s.copyWithCount(1);
                                handed.add(new Line(one.getHoverName().getString(), ProductItem.price(one), one));
                            }
                            list.set(i, ItemStack.EMPTY);
                        }
                    }
                }
                if (handed.isEmpty()) {
                    say(player, pick("Hiya! Grab what you need off the shelves and bring it here.",
                            "Alright? Anything you need's on the shelves, love.", "Morning! Just bring it to the till when you're ready."));
                    return;
                }
                begin(player);
                basket.addAll(handed);
                state = State.SCANNING;
                say(player, pick("Hiya! Let's get that scanned for you.", "Lovely, let's get those through.", "Alright? Let's see what we've got."));
            }
            case SCANNING -> say(player, "Just scanning these...");
            case PAYING -> pay(player);
            case MAKING -> say(player, "Won't be a minute!");
        }
        setChanged();
    }

    /** An order from the menu (quantities by line); the customer then pays. */
    public void order(ServerPlayer player, int[] quantities) {
        if (state != State.IDLE || !kind().counter) {
            return;
        }
        List<Line> menu = offers();
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(menu.size(), quantities.length); i++) {
            for (int n = 0; n < Math.max(0, Math.min(10, quantities[i])); n++) {
                Line l = menu.get(i);
                lines.add(new Line(l.title(), l.pence(), l.give().copy()));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        begin(player);
        basket.addAll(lines);
        total = lines.stream().mapToLong(Line::pence).sum();
        state = State.PAYING;
        say(player, pick("That's " + CashItem.money(total) + ", please. Cash or card?", CashItem.money(total) + " please, love.",
                "Lovely. That'll be " + CashItem.money(total) + "."));
        hint(player);
        setChanged();
    }

    private void begin(Player player) {
        customer = player.getUUID();
        basket.clear();
        total = 0;
        scanned = 0;
        timer = 0;
        away = 0;
    }

    private void hint(Player who) {
        if (who != null) {
            who.displayClientMessage(Component.literal("Hold your bank card or some cash and click " + keeperName + " to pay (sneak and click to cancel)")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void pay(Player player) {
        if (player.isShiftKeyDown()) {
            say(player, "No worries, I'll put those back.");
            giveBack(player);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof BankCard) {
            if (!Bank.canPay(player, total)) {
                sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.8f);
                say(player, "Sorry love, it's been declined. Have you got cash?");
                return;
            }
            Bank.pay(player, total, shopName);
            sound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.6f, 0.6f);
            paidBy = "CARD (CONTACTLESS)";
            change = 0;
            paid(player);
        } else if (held.getItem() instanceof CashItem) {
            long c = CashItem.pay(player, total);
            if (c < 0) {
                say(player, "You're a bit short there, it's " + CashItem.money(total) + ".");
                return;
            }
            sound(SoundEvents.ITEM_PICKUP, 0.8f, 0.6f);
            paidBy = "CASH";
            change = c;
            if (c > 0) {
                say(player, "And " + CashItem.money(c) + " change.");
            }
            paid(player);
        } else {
            say(player, "That's " + CashItem.money(total) + ", please. Cash or card?");
            hint(player);
        }
    }

    private void paid(Player player) {
        int wait = kind().rails() || !kind().counter ? 0 : switch (kind()) {
            case PUB -> 40;
            case CAFE -> 80;
            case PIZZA_TAKEAWAY -> 140;
            default -> 100;
        };
        if (wait == 0) {
            handOver(player);
            return;
        }
        state = State.MAKING;
        timer = wait;
        boolean service = basket.stream().allMatch(l -> l.give().isEmpty());
        say(player, service ? pick("Take a seat, I'll be right with you.", "Lovely, sit yourself down.") : pick("Won't be a minute!", "Coming right up."));
        setChanged();
    }

    private void makingSound() {
        switch (kind()) {
            case CHIPPY, KEBAB_SHOP -> sound(SoundEvents.FIRE_EXTINGUISH, 1.8f, 0.25f);
            case PIZZA_TAKEAWAY -> sound(SoundEvents.FURNACE_FIRE_CRACKLE, 1f, 0.8f);
            case CAFE -> sound(SoundEvents.BREWING_STAND_BREW, 1.2f, 0.5f);
            case PUB -> sound(SoundEvents.BOTTLE_FILL, 0.8f, 0.7f);
            case HAIRDRESSER -> sound(SoundEvents.SHEEP_SHEAR, 1.6f, 0.6f);
            default -> sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f, 0.4f);
        }
    }

    /** The things (paid for now) and the receipt to the customer, and on to the next. */
    private void handOver(Player player) {
        boolean service = basket.stream().allMatch(l -> l.give().isEmpty());
        if (player != null) {
            for (Line l : basket) {
                if (!l.give().isEmpty()) {
                    ItemStack s = l.give().copy();
                    ProductItem.paid(s);
                    if (!player.getInventory().add(s)) {
                        player.drop(s, false);
                    }
                }
            }
            ItemStack r = ReceiptItem.make(ModItems.RECEIPT.get(), shopName, receipt());
            if (!player.getInventory().add(r)) {
                player.drop(r, false);
            }
            say(player, service ? pick("There you go, all done. Looking great!", "All done! Hope you like it.")
                    : pick("There you go. Cheers, have a good one!", "Here you are. Ta-ra!", "Thanks love, see you soon."));
        }
        state = State.IDLE;
        customer = null;
        basket.clear();
        setChanged();
    }

    /** The sale's off: what was handed over goes back to the customer (or onto the counter). */
    private void giveBack(Player player) {
        for (Line l : basket) {
            if (!l.give().isEmpty() && ProductItem.unpaid(l.give())) {
                ItemStack s = l.give().copy();
                if (player == null || !player.getInventory().add(s)) {
                    net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, s);
                }
            }
        }
        state = State.IDLE;
        customer = null;
        basket.clear();
        setChanged();
    }

    private List<String> receipt() {
        List<String> out = new ArrayList<>();
        out.add(shopName.toUpperCase(Locale.ROOT));
        long t = level.getDayTime();
        out.add(String.format(Locale.ROOT, "DAY %d  %02d:%02d", t / 24000 + 1, (t / 1000 + 6) % 24, (t % 1000) * 60 / 1000));
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (Line l : basket) {
            long[] g = grouped.computeIfAbsent(l.title(), k -> new long[2]);
            g[0]++;
            g[1] += l.pence();
        }
        grouped.forEach((title, g) -> out.add((g[0] > 1 ? g[0] + " x " : "") + title + "  " + CashItem.money(g[1])));
        out.add("TOTAL  " + CashItem.money(total));
        out.add(paidBy + (paidBy.equals("CASH") ? "  CHANGE " + CashItem.money(change) : ""));
        out.add("Served by " + keeperName);
        out.add("THANK YOU");
        return out;
    }

    private void say(Player player, String text) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(keeperName + ": ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(false))));
        }
    }

    private String pick(String... lines) {
        return lines[(int) Math.floorMod(level.getGameTime() / 7 + seed, lines.length)];
    }

    private void sound(SoundEvent s, float pitch, float volume) {
        level.playSound(null, worldPosition, s, SoundSource.BLOCKS, volume, pitch);
    }

    // ------------------------------------------------------------------ set up by hand, saved, sent

    /** Set up by hand (a shop of your own): its kind (null: work it out), name, and maybe a new person. */
    public void configure(ShopKind k, String name, boolean newPerson) {
        chosen = k != null;
        kind = k;
        if (k == null && level instanceof ServerLevel s) {
            setUp(s);
        }
        if (name != null && !name.isBlank()) {
            shopName = name.trim();
            chosen = true;
        }
        if (newPerson || keeperName.isEmpty()) {
            if (newPerson || seed == 0) {
                seed = level.getRandom().nextLong() | 1;
            }
            newFace();
        } else {
            skin = kind().outfit * ShopKeeperEntity.PEOPLE + Math.floorMod(skin, ShopKeeperEntity.PEOPLE);
        }
        if (level instanceof ServerLevel s && keeper != null && s.getEntity(keeper) != null) {
            s.getEntity(keeper).discard();                  // the new one comes in on the next tick
        }
        keeper = null;
        stockedDay = -1;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("kind", kind == null ? "" : kind.name());
        tag.putBoolean("chosen", chosen);
        tag.putString("shop", shopName);
        tag.putString("keeperName", keeperName);
        tag.putInt("skin", skin);
        if (keeper != null) {
            tag.putUUID("keeper", keeper);
        }
        tag.putLong("seed", seed);
        tag.putLong("stocked", stockedDay);
        tag.putString("state", state == State.MAKING || state == State.IDLE ? state.name() : State.PAYING.name());
        if (customer != null) {
            tag.putUUID("customer", customer);
        }
        ListTag lines = new ListTag();
        for (Line l : basket) {
            CompoundTag c = new CompoundTag();
            c.putString("title", l.title());
            c.putInt("pence", l.pence());
            c.put("give", l.give().save(new CompoundTag()));
            lines.add(c);
        }
        tag.put("basket", lines);
        tag.putLong("total", basket.stream().mapToLong(Line::pence).sum());
        tag.putInt("timer", timer);
        tag.putString("paidBy", paidBy);
        tag.putLong("change", change);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            kind = tag.getString("kind").isEmpty() ? null : ShopKind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            kind = null;
        }
        chosen = tag.getBoolean("chosen");
        shopName = tag.getString("shop");
        keeperName = tag.getString("keeperName");
        skin = tag.contains("skin") ? tag.getInt("skin") : -1;
        keeper = tag.hasUUID("keeper") ? tag.getUUID("keeper") : null;
        seed = tag.getLong("seed");
        stockedDay = tag.contains("stocked") ? tag.getLong("stocked") : -1;
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.IDLE;
        }
        customer = tag.hasUUID("customer") ? tag.getUUID("customer") : null;
        basket.clear();
        for (Tag t : tag.getList("basket", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            basket.add(new Line(c.getString("title"), c.getInt("pence"), ItemStack.of(c.getCompound("give"))));
        }
        total = tag.getLong("total");
        timer = tag.getInt("timer");
        paidBy = tag.getString("paidBy");
        change = tag.getLong("change");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind == null ? "" : kind.name());
        tag.putBoolean("chosen", chosen);
        tag.putString("shop", shopName);
        tag.putString("keeperName", keeperName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
}
