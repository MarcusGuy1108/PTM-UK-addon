package com.ptmuk.bus;

import com.ptmuk.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Alexander ALX400, built to match photos of London ALX400s (tools/bus_alx400.py). */
public class ALX400 extends DoubleDeckerBus {
    public ALX400(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level, ALX400Layout.INSTANCE, "alx400");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ALX400.get());
    }

    /** In the red-to-white "Len" livery. */
    public static class Len extends ALX400 {
        public Len(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        public ItemStack getPickResult() {
            return new ItemStack(ModItems.ALX400_LEN.get());
        }
    }
}
