package staff;

import lombok.Data;

/**
 * Defines a specific shift type as understood by the Linear Program.
 * It maps an LP-specific shift identifier (e.g., "Ds", "Dl") to a concrete
 * {@link Shift} enum value and confirms its properties like length.
 */
@Data
public class ShiftDefinition {

    private final String lpShiftId; // The identifier used in the LP (e.g., "Ds", "Dl", "F")

    private final Shift concreteShift; // The corresponding concrete Shift enum value


    /**
     * Constructs a new ShiftDefinition.
     *
     * @param lpShiftId     The identifier for this shift as used in the Linear Program (e.g., "Ds").
     * This corresponds to 's' in ShiftLength_s or X_nsd.
     * @param concreteShift The concrete {@link Shift} enum value this LP shift maps to.
     * The length and off-shift status will be derived from this.
     */
    public ShiftDefinition(String lpShiftId, Shift concreteShift) {
        if (lpShiftId == null || lpShiftId.trim().isEmpty()) {
            throw new IllegalArgumentException("LP Shift ID cannot be null or empty.");
        }
        if (concreteShift == null) {
            throw new IllegalArgumentException("Concrete Shift enum value cannot be null.");
        }


        this.lpShiftId = lpShiftId.trim();
        this.concreteShift = concreteShift;
    }

    /**
     * Gets the length of this shift in hours, derived from the concrete {@link Shift} enum.
     * This value corresponds to ShiftLength_s in the LP.
     *
     * @return The length of the shift in hours.
     */
    public double getLengthInHours() {
        return concreteShift.getLengthInHours();
    }

    /**
     * Checks if this shift is an off-shift (e.g., "Free"), derived from the concrete {@link Shift} enum.
     *
     * @return true if this is an off-shift, false otherwise.
     */
    public boolean isOffShift() {
        return concreteShift.isOffShift();
    }
    /**
     * Gets the start time of this shift in hours from midnight, derived from the concrete {@link Shift} enum.
     * @return The start time of the shift in hours from midnight.
     */
    public double getStartTimeInHoursFromMidnight() {
        return concreteShift.getDefaultStartTimeInHoursFromMidnight();
    }

    /**
     * Checks if this shift's time interval fully covers another shift's interval on the same day.
     * For example, a 7am-7pm shift covers a 7am-3pm shift.
     *
     * @param otherShift The shift to check if it's contained within this one.
     * @return true if this shift fully covers the other shift.
     */
    public boolean covers(ShiftDefinition otherShift) {
        if (otherShift == null || otherShift.isOffShift() || this.isOffShift()) {
            return false;
        }

        double thisStart = this.getStartTimeInHoursFromMidnight();
        double thisEnd = thisStart + this.getLengthInHours();

        double otherStart = otherShift.getStartTimeInHoursFromMidnight();
        double otherEnd = otherStart + otherShift.getLengthInHours();

        return thisStart <= otherStart && thisEnd >= otherEnd;
    }

    // It's good practice to override equals and hashCode if ShiftDefinition
    // objects are stored in collections like Sets or used as Map keys,
    // primarily based on the lpShiftId.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShiftDefinition that = (ShiftDefinition) o;
        return lpShiftId.equals(that.lpShiftId);
    }

    @Override
    public int hashCode() {
        return lpShiftId.hashCode();
    }

    @Override
    public String toString() {
        return "ShiftDefinition{" +
                "lpShiftId='" + lpShiftId + '\'' +
                ", concreteShift=" + concreteShift.name() +
                ", length=" + getLengthInHours() +
                ", isOffShift=" + isOffShift() +
                '}';
    }
}