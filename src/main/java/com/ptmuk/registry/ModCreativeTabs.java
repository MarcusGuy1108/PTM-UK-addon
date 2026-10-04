package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PtmUk.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ptmuk.main"))
            .icon(() -> new ItemStack(ModItems.UK_SIGNAL_MODERN.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.UK_SIGNAL_MODERN.get());
                output.accept(ModItems.UK_SIGNAL_CLASSIC.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
