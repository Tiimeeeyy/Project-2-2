package staff;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents the staffing demand for a specific role, on a specific day, for a specific LP shift.
 * This corresponds to the R_qsd (Required staff with role q, on day d, at shift s)
 * parameter in the linear program. The requiredCount is typically determined by factors
 * like patient census, acuity, and applicable staffing regulations (e.g., from OregonStaffingRules).
 */
@Getter
@ToString
@EqualsAndHashCode
public class Demand {

    /**
     * The specific {@link Role} required (e.g., Role.REGISTERED_NURSE for an ED RN).
     * This corresponds to 'q' (qualification/role) in the LP.
     */
    private final Role requiredRole;

    /**
     * The day index within the planning period (e.g., 0 for Day 1, ..., up to 27 for a 28-day period).
     * This corresponds to 'd' in the LP.
     */
    private final int dayIndex;

    /**
     * The identifier for the LP shift (e.g., "ED_DAY_Ds", "ED_NIGHT_Nl").
     * This identifier corresponds to 's' in the LP and will map to a {@link ShiftDefinition}
     * which, in turn, maps to a concrete {@link Shift} enum (e.g., Shift.DAY_8H).
     */
    private final String lpShiftId;

    /**
     * The number of staff members with the specified role required for this shift on this day.
     * This count is determined based on staffing rules (e.g., from OregonStaffingRules.java).
     */
    private final int requiredCount;

    /**
     * Constructs a new Demand instance.
     *
     * @param requiredRole The specific {@link Role} required.
     * @param dayIndex The day index within the planning period (0-indexed).
     * @param lpShiftId The LP shift identifier (e.g., "ED_DAY_Ds").
     * @param requiredCount The number of staff members required, typically derived from staffing rules.
     */
    public Demand(Role requiredRole, int dayIndex, String lpShiftId, int requiredCount) {
        if (requiredRole == null) {
            throw new IllegalArgumentException("Required role cannot be null.");
        }
        if (dayIndex < 0) {
            throw new IllegalArgumentException("Day index cannot be negative.");
        }
        if (lpShiftId == null || lpShiftId.trim().isEmpty()) {
            throw new IllegalArgumentException("LP shift ID cannot be null or empty.");
        }
        if (requiredCount < 0) {
            throw new IllegalArgumentException("Required count cannot be negative (0 is acceptable).");
        }

        this.requiredRole = requiredRole;
        this.dayIndex = dayIndex;
        this.lpShiftId = lpShiftId.trim();
        this.requiredCount = requiredCount;
    }
}
