package com.ptmuk.bus;

import com.ptmuk.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The original stylised UK double decker, kept as its own bus (tools/bus_ukdd.py). */
public class UkDoubleDecker extends DoubleDeckerBus {
    public UkDoubleDecker(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level, UkddLayout.INSTANCE, "ukdd");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.UK_DOUBLE_DECKER.get());
    }
}
