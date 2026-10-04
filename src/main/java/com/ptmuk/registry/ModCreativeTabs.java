package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.SignalStyle;
import com.ptmuk.block.SignalType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PtmUk.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = tab("main", null,
            () -> new ItemStack(ModBlocks.signal(SignalStyle.LED, SignalType.STANDARD).get()), ModItems.SIGNAL_ITEMS);
    public static final RegistryObject<CreativeModeTab> POLES = tab("poles", MAIN,
            () -> new ItemStack(ModBlocks.POLES.get("signal_pole_black").get()), ModItems.POLE_ITEMS);
    public static final RegistryObject<CreativeModeTab> SIGNS = tab("signs", POLES,
            () -> new ItemStack(ModBlocks.ACCESSORIES.get("speed_30_sign").get()), ModItems.SIGN_ITEMS);
    public static final RegistryObject<CreativeModeTab> MOTORWAY = tab("motorway", SIGNS,
            () -> new ItemStack(ModBlocks.MOTORWAY.get("lane_signal").get()), ModItems.MOTORWAY_ITEMS);
    public static final RegistryObject<CreativeModeTab> POWER = tab("power", MOTORWAY,
            () -> new ItemStack(ModItems.PYLON_BUILDERS.get("pylon_400kv").get()), ModItems.POWER_ITEMS);
    public static final RegistryObject<CreativeModeTab> STREET = tab("street", POWER,
            () -> new ItemStack(ModBlocks.FURNITURE.get("phone_box").get()), ModItems.STREET_ITEMS);
    public static final RegistryObject<CreativeModeTab> BUILDING = tab("building", STREET,
            () -> new ItemStack(ModBlocks.BUILDING.get("red_brick").get()), ModItems.BUILDING_ITEMS);

    private static RegistryObject<CreativeModeTab> tab(String name, RegistryObject<CreativeModeTab> after,
                                                       java.util.function.Supplier<ItemStack> icon,
                                                       java.util.List<RegistryObject<net.minecraft.world.item.Item>> items) {
        return TABS.register(name, () -> {
            CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ptmuk." + name))
                    .icon(icon)
                    .displayItems((params, output) -> items.forEach(item -> output.accept(item.get())));
            if (after != null) {
                builder.withTabsBefore(after.getKey());
            }
            return builder.build();
        });
    }

    private ModCreativeTabs() {
    }
}
