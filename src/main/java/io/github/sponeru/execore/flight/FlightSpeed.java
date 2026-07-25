package io.github.sponeru.execore.flight;

import net.minecraft.network.chat.Component;

public enum FlightSpeed
{
    PRECISE(0.025F, "precise"),
    NORMAL(0.05F, "normal"),
    FAST(0.10F, "fast"),
    EXTREME(0.20F, "extreme");

    private final float value;
    private final String translationSuffix;

    FlightSpeed(float value, String translationSuffix)
    {
        this.value = value;
        this.translationSuffix = translationSuffix;
    }

    public float value()
    {
        return value;
    }

    public Component displayName()
    {
        return Component.translatable("flight_speed.execore." + translationSuffix);
    }

    public static FlightSpeed byOrdinal(int ordinal)
    {
        FlightSpeed[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL;
    }
}
