package com.ptmuk.bus;

import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.engine.vehicle.DoorLocation;
import com.rinventor.ptm2.engine.vehicle.SeatLocation;
import com.rinventor.ptm2.engine.vehicle.WheelLocation;
import com.rinventor.ptm2.objects.entities.vehicle.car.Car;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 2026 Kia K4 hatchback, GT-Line S, in red, for PTM2 (model and textures from tools/car_k4.py).
 * Seat 0 is the driver. Built left-hand drive like PTM2's cars; PTM2 mirrors it in left-hand
 * traffic worlds, so UK worlds get a right-hand drive car.
 */
public class KiaK4 extends Car {
    public KiaK4(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.seats = new ArrayList<>(List.of(
                new SeatLocation(-0.47, 0.0, 0.1, 0.0f, false, -1.5, 0.0, 0.3),
                new SeatLocation(0.47, 0.0, 0.1, 0.0f, false, 1.5, 0.0, 0.3),
                new SeatLocation(-0.55, 0.0, -1.05, 0.0f, false, -1.5, 0.0, -0.8),
                new SeatLocation(0.55, 0.0, -1.05, 0.0f, false, 1.5, 0.0, -0.8),
                new SeatLocation(0.0, 0.0, -1.1, 0.0f, false, 1.5, 0.0, -0.4)));
        this.doors = new ArrayList<>(List.of(
                new DoorLocation(-1.09, 0.2, 0.4, -90.0f, true, false, 40, 25),
                new DoorLocation(1.09, 0.2, 0.4, 90.0f, true, false, 40, 25),
                new DoorLocation(-1.09, 0.2, -0.7, -90.0f, true, false, 40, 25),
                new DoorLocation(1.09, 0.2, -0.7, 90.0f, true, false, 40, 25),
                new DoorLocation(0.0, 1.0, -2.6, 180.0f, false, false, 60, 40)));
        this.wheels = new ArrayList<>(List.of(new WheelLocation(1.56), new WheelLocation(-1.65)));
        this.variants = new ArrayList<>(List.of("red"));
    }

    @Override
    public String animationID() {
        return "k4";
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.KIA_K4.get());
    }
}
