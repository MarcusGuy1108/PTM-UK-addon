package com.ptmuk.bus;

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
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A dual-door UK double decker for PTM2. Seats, doors and floors come from the model's
 * {@link BusLayout}, generated together with the model (tools/bus_*.py).
 *
 * <p>Modelled the way PTM2 models its buses (doors on the right); with PTM2's left-hand traffic
 * setting on, PTM2 mirrors it into a proper UK bus with the doors on the left.
 *
 * <p>PTM2 floors are flat areas, which can't describe two decks above each other, so each client
 * picks the floor list for the deck its own player is on. The staircase ramp is in both lists, so
 * walking up the stairs carries you from one to the other.
 */
public abstract class DoubleDeckerBus extends Bus {
    private final AnimationController<?> door1Controller = new AnimationController<DoubleDeckerBus>(this, "door1_controller", 1, this::doors);
    private final AnimationController<?> door2Controller = new AnimationController<DoubleDeckerBus>(this, "door2_controller", 1, this::doors);
    private final AnimationController<?> stoppingController = new AnimationController<DoubleDeckerBus>(this, "stopping_controller", 1, this::doors);
    private boolean lastD1;
    private boolean lastD2;
    private final BusLayout layout;
    private final String prefix;
    private final List<FloorObject> lowerFloors;
    private final List<FloorObject> upperFloors;
    private final List<FloorObject> lowerFloorsMirrored;
    private final List<FloorObject> upperFloorsMirrored;

    protected DoubleDeckerBus(EntityType<? extends PathfinderMob> type, Level level, BusLayout layout, String animationPrefix) {
        super(type, level);
        this.layout = layout;
        this.prefix = animationPrefix;
        this.lowerFloors = layout.floorList(false);
        this.upperFloors = layout.floorList(true);
        this.lowerFloorsMirrored = mirror(lowerFloors);
        this.upperFloorsMirrored = mirror(upperFloors);
        this.floorHeight = layout.lowerFloor();
        this.passengerSeatYOffset = -0.6;
        this.totalSections = 1;
        this.sectionID = 0;
        this.hasDoorButtons = false;
        this.floors = new ArrayList<>(lowerFloors);
        this.seats = layout.seatList();
        this.doors = new ArrayList<>(List.of(
                new DoorLocation(this.width / 2.0f, layout.lowerFloor(), layout.door1Forward(), 90.0f, true, false, 30, 30),
                new DoorLocation(this.width / 2.0f, layout.lowerFloor(), layout.door2Forward(), 90.0f, true, false, 30, 30)));
        this.wheels = layout.wheelList();
        double[] tm = layout.ticketMachine();
        this.validators = new ArrayList<>(List.of(new ValidatorLocation(tm[0], tm[1], tm[2], -90.0f, -1)));
    }

    private static List<FloorObject> mirror(List<FloorObject> floors) {
        List<FloorObject> out = new ArrayList<>();
        for (FloorObject f : floors) {
            out.add(new FloorObject(f.forwardStart, f.forwardEnd, -f.leftMax, -f.leftMin, f.floorHeight));
        }
        return out;
    }

    private boolean leftTraffic() {
        try {
            return com.rinventor.ptm2.PTM.DATA.get(level()).LEFT_TRAFFIC;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public BusLayout layout() {
        return layout;
    }

    @Override
    public void tick() {
        // choose the deck before PTM2 places the player on a floor this tick, or a player who
        // arrives upstairs is snapped down to the lower floor first
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::pickDeckForLocalPlayer);
        }
        super.tick();
    }

    private void pickDeckForLocalPlayer() {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        boolean upper = player != null && player.distanceToSqr(this) < 200
                && player.getY() - getY() > layout.deckSwitchHeight();
        List<FloorObject> wanted = upper ? upperFloors : lowerFloors;
        // PTM2 mirrors seats and doors in left-hand traffic worlds but not floors, so mirror the
        // stair ramp ourselves or it sits on the opposite side to the stairs you can see
        if (leftTraffic()) {
            wanted = upper ? upperFloorsMirrored : lowerFloorsMirrored;
        }
        if (!floors.equals(wanted)) {
            floors = new ArrayList<>(wanted);
        }
    }

    @Override
    public String animationID() {
        return prefix;
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
            door1Controller.setAnimation(RawAnimation.begin().thenPlayAndHold(prefix + ".door1_" + (d1 ? "open" : "close")));
            lastD1 = d1;
        }
        if (d2 != lastD2) {
            door2Controller.setAnimation(RawAnimation.begin().thenPlayAndHold(prefix + ".door2_" + (d2 ? "open" : "close")));
            lastD2 = d2;
        }
        boolean stopping = main.ignition && (doors.get(0).getStatus() == DoorStatus.REQUESTED || doors.get(1).getStatus() == DoorStatus.REQUESTED);
        stoppingController.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (stopping ? "bus_stopping" : "null")));
        front_lights_controller.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (main.frontLights && main.ignition ? "front_lights" : "null")));
        reverse_lights_controller.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (main.ignition && main.gear == -1 ? "reverse_lights" : "null")));
        break_lights_controller.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (main.ignition && main.braking ? "stop_lights" : "null")));
        wipers_controller.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (main.wipers ? "wipers" : "null")));
        int ind = main.getVisualIndicators();
        left_indicator_controller.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (ind == -1 || ind == -2 ? "left_signal" : "null")));
        right_indicator_controller.setAnimation(RawAnimation.begin().thenLoop(prefix + "." + (ind == 1 || ind == -2 ? "right_signal" : "null")));
        return PlayState.CONTINUE;
    }
}
