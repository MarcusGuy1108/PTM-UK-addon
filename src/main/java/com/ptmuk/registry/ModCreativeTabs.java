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

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ptmuk.main"))
            .icon(() -> new ItemStack(ModBlocks.signal(SignalStyle.LED, SignalType.STANDARD).get()))
            .displayItems((params, output) -> ModItems.BLOCK_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private ModCreativeTabs() {
    }
}
