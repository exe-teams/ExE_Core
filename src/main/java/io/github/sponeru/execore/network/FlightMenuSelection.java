package io.github.sponeru.execore.network;

import io.github.sponeru.execore.flight.FlightSpeed;

public enum FlightMenuSelection
{
    TOGGLE_NO_INERTIA(null),
    PRECISE(FlightSpeed.PRECISE),
    NORMAL(FlightSpeed.NORMAL),
    FAST(FlightSpeed.FAST),
    EXTREME(FlightSpeed.EXTREME);

    private final FlightSpeed speed;

    FlightMenuSelection(FlightSpeed speed)
    {
        this.speed = speed;
    }

    public FlightSpeed speed()
    {
        return speed;
    }

    public static FlightMenuSelection byOrdinal(int ordinal)
    {
        FlightMenuSelection[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL;
    }
}
