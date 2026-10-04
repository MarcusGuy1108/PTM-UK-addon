package com.ptmuk.bus;

import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.core.properties.DoorStatus;
import com.rinventor.ptm2.dimension.physical.PerformaceManager;
import com.rinventor.ptm2.engine.animation.core.PlayState;
import com.rinventor.ptm2.engine.animation.core.animation.AnimatableManager;
import com.rinventor.ptm2.engine.animation.core.animation.AnimationController;
import com.rinventor.ptm2.engine.animation.core.animation.AnimationState;
import com.rinventor.ptm2.engine.animation.core.animation.RawAnimation;
import com.rinventor.ptm2.engine.vehicle.DoorLocation;
import com.rinventor.ptm2.engine.vehicle.FloorObject;
import com.rinventor.ptm2.engine.vehicle.ValidatorLocation;
import com.rinventor.ptm2.objects.entities.vehicle.Vehicle;
import com.rinventor.ptm2.objects.entities.vehicle.bus.Bus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Alexander ALX400 double decker (dual door, London style). Seats, doors and floors come from
 * {@link ALX400Layout}, generated together with the model by tools/bus_alx400.py.
 *
 * <p>Modelled the way PTM2 models its buses (doors on the right); with PTM2's left-hand traffic
 * setting on, PTM2 mirrors it into a proper UK bus with the doors on the left.
 *
 * <p>PTM2 floors are flat areas, which can't describe two decks above each other, so each client
 * picks the floor list for the deck its own player is on. The staircase ramp is in both lists, so
 * walking up the stairs carries you from one to the other.
 */
public class ALX400 extends Bus {
    public static final int SEATS = ALX400Layout.SEAT_COUNT;

    private final AnimationController<?> door1Controller = new AnimationController<ALX400>(this, "door1_controller", 1, this::doors);
    private final AnimationController<?> door2Controller = new AnimationController<ALX400>(this, "door2_controller", 1, this::doors);
    private final AnimationController<?> stoppingController = new AnimationController<ALX400>(this, "stopping_controller", 1, this::doors);
    private boolean lastD1;
    private boolean lastD2;
    private final List<FloorObject> lowerFloors = ALX400Layout.floors(false);
    private final List<FloorObject> upperFloors = ALX400Layout.floors(true);

    public ALX400(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.floorHeight = ALX400Layout.LOWER_FLOOR;
        this.passengerSeatYOffset = -0.6;
        this.totalSections = 1;
        this.sectionID = 0;
        this.hasDoorButtons = false;
        this.floors = new ArrayList<>(lowerFloors);
        this.seats = ALX400Layout.seats();
        this.doors = new ArrayList<>(List.of(
                new DoorLocation(this.width / 2.0f, ALX400Layout.LOWER_FLOOR, ALX400Layout.DOOR1_FORWARD, 90.0f, true, false, 30, 30),
                new DoorLocation(this.width / 2.0f, ALX400Layout.LOWER_FLOOR, ALX400Layout.DOOR2_FORWARD, 90.0f, true, false, 30, 30)));
        this.wheels = ALX400Layout.wheels();
        this.validators = new ArrayList<>(List.of(new ValidatorLocation(ALX400Layout.TICKET_MACHINE[0], ALX400Layout.TICKET_MACHINE[1],
                ALX400Layout.TICKET_MACHINE[2], -90.0f, -1)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::pickDeckForLocalPlayer);
        }
    }

    private void pickDeckForLocalPlayer() {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        boolean upper = player != null && player.distanceToSqr(this) < 200
                && player.getY() - getY() > ALX400Layout.DECK_SWITCH_HEIGHT;
        List<FloorObject> wanted = upper ? upperFloors : lowerFloors;
        if (!floors.equals(wanted)) {
            floors = new ArrayList<>(wanted);
        }
    }

    @Override
    public String animationID() {
        return "alx400";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(door1Controller);
        controllers.add(door2Controller);
        controllers.add(stoppingController);
    }

    private PlayState doors(AnimationState<?> state) {
        if (!PerformaceManager.playAnimationsForEntity(this)) {
            return PlayState.STOP;
        }
        Vehicle main = getMainVehicle(this);
        if (main == null) {
            main = this;
        }
        boolean d1 = doors.get(0).animationOpen;
        boolean d2 = doors.get(1).animationOpen;
        if (d1 != lastD1) {
            door1Controller.setAnimation(RawAnimation.begin().thenPlayAndHold("alx400.door1_" + (d1 ? "open" : "close")));
            lastD1 = d1;
        }
        if (d2 != lastD2) {
            door2Controller.setAnimation(RawAnimation.begin().thenPlayAndHold("alx400.door2_" + (d2 ? "open" : "close")));
            lastD2 = d2;
        }
        boolean stopping = main.ignition && (doors.get(0).getStatus() == DoorStatus.REQUESTED || doors.get(1).getStatus() == DoorStatus.REQUESTED);
        stoppingController.setAnimation(RawAnimation.begin().thenLoop("alx400." + (stopping ? "bus_stopping" : "null")));
        front_lights_controller.setAnimation(RawAnimation.begin().thenLoop("alx400." + (main.frontLights && main.ignition ? "front_lights" : "null")));
        reverse_lights_controller.setAnimation(RawAnimation.begin().thenLoop("alx400." + (main.ignition && main.gear == -1 ? "reverse_lights" : "null")));
        break_lights_controller.setAnimation(RawAnimation.begin().thenLoop("alx400." + (main.ignition && main.braking ? "stop_lights" : "null")));
        wipers_controller.setAnimation(RawAnimation.begin().thenLoop("alx400." + (main.wipers ? "wipers" : "null")));
        int ind = main.getVisualIndicators();
        left_indicator_controller.setAnimation(RawAnimation.begin().thenLoop("alx400." + (ind == -1 || ind == -2 ? "left_signal" : "null")));
        right_indicator_controller.setAnimation(RawAnimation.begin().thenLoop("alx400." + (ind == 1 || ind == -2 ? "right_signal" : "null")));
        return PlayState.CONTINUE;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ALX400.get());
    }
}
