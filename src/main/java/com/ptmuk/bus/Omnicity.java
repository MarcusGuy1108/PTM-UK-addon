package com.ptmuk.bus;

import com.ptmuk.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Scania OmniCity double decker, London dual door spec, built from photos of London examples (tools/bus_omnicity.py). */
public class Omnicity extends DoubleDeckerBus {
    public Omnicity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level, OmnicityLayout.INSTANCE, "omnicity");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.OMNICITY.get());
    }
}
