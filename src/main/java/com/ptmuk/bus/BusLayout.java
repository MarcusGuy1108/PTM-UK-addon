package com.ptmuk.bus;

import com.rinventor.ptm2.engine.vehicle.FloorObject;
import com.rinventor.ptm2.engine.vehicle.SeatLocation;
import com.rinventor.ptm2.engine.vehicle.WheelLocation;
import java.util.List;

/** A double decker's seats, doors, floors and display positions; generated with each model. */
public interface BusLayout {
    float lowerFloor();

    double deckSwitchHeight();

    double door1Forward();

    double door2Forward();

    double[] ticketMachine();

    /** Display width, height and label yaw. */
    float[] displayFront();

    float[] displaySide();

    float[] displayRear();

    float[] displayInside();

    List<SeatLocation> seatList();

    List<WheelLocation> wheelList();

    List<FloorObject> floorList(boolean upper);

    /** Colour of the fleet number on the front. */
    default int frontIdColour() {
        return 0xFF101010;
    }
}
