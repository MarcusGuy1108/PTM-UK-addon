package com.ptmuk.bus;

import com.ptmuk.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Alexander Dennis Enviro400, London dual door spec, built from photos of London examples (tools/bus_e400.py). */
public class Enviro400 extends DoubleDeckerBus {
    public Enviro400(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level, E400Layout.INSTANCE, "e400");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ENVIRO400.get());
    }

    /** In the red-to-white "Len" livery. */
    public static class Len extends Enviro400 {
        public Len(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        public ItemStack getPickResult() {
            return new ItemStack(ModItems.ENVIRO400_LEN.get());
        }
    }
}
