package staff;

import lombok.Getter;

@Getter
public enum Shift {
    // 8-Hour Shifts
    DAY_8H("Day 8 Hours", 8.0, false),
    EVENING_8H("Evening 8 Hours", 8.0, false),
    NIGHT_8H("Night 8 Hours", 8.0, false), // Note: Actual night shift durations can vary (e.g., 7-8h); adjust if needed.

    // 10-Hour Shifts (Common in some hospital scheduling models)
    DAY_10H("Day 10 Hours", 10.0, false),
    EVENING_10H("Evening 10 Hours", 10.0, false), // Less common but possible
    NIGHT_10H("Night 10 Hours", 10.0, false),

    // 12-Hour Shifts (Oregon law generally caps mandatory work at 12h in 24h)
    DAY_12H("Day 12 Hours", 12.0, false),    // Corresponds to LP's 'long day' (Dl)
    NIGHT_12H("Night 12 Hours", 12.0, false),  // Corresponds to LP's 'long night' (Nl)

    // Special Shifts as per LP
    ON_CALL("On Call", 0.0, false), // For LP purposes, scheduled on-call hours usually count as 0 'worked' hours unless called in.
    // Payment for on-call status is separate from hours worked for max hour constraints.
    FREE("Free/Off", 0.0, true);     // Represents a day off.

    private final String description;
    private final double lengthInHours;
    private final boolean isOffShift;

    Shift(String description, double lengthInHours, boolean isOffShift) {
        this.description = description;
        this.lengthInHours = lengthInHours;
        this.isOffShift = isOffShift;
    }
}
