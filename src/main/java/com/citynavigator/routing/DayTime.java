package com.citynavigator.routing;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * The part of the week that matters for traffic: weekday, Saturday or Sunday, and the hour.
 *
 * @param dayType {@link #WEEKDAY}, {@link #SATURDAY} or {@link #SUNDAY}
 */
public record DayTime(int dayType, int hour) {

    public static final int WEEKDAY = 0;
    public static final int SATURDAY = 1;
    public static final int SUNDAY = 2;
    public static final int DAY_TYPES = 3;

    public DayTime {
        if (dayType < WEEKDAY || dayType > SUNDAY || hour < 0 || hour > 23) {
            throw new IllegalArgumentException("not a day type and hour: " + dayType + ", " + hour);
        }
    }

    public static DayTime of(LocalDateTime local) {
        DayOfWeek day = local.getDayOfWeek();
        int type = switch (day) {
            case SATURDAY -> SATURDAY;
            case SUNDAY -> SUNDAY;
            default -> WEEKDAY;
        };
        return new DayTime(type, local.getHour());
    }

    /** Position in a table of {@code DAY_TYPES * 24} hourly values. */
    public int slot() {
        return dayType * 24 + hour;
    }
}
