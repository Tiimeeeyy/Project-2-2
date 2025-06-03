package scheduling;

import staff.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the results of the staff scheduling optimization process.
 * This includes detailed shift assignments, weekly hour summaries for each staff member,
 * the total cost of the schedule, and the feasibility status of the solution.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder
public class OptimizedScheduleOutput {

    /**
     * Shift assignments for each staff member.
     * Outer Map: Key is Staff Member UUID.
     * Inner Map: Key is Day Index (0 to numDaysInPeriod-1).
     * Value: The lpShiftId (String, e.g., "Ds", "Nl", "F") assigned to that staff member on that day.
     * This corresponds to the X_nsd decision variables from the LP.
     */
    @Singular("putStaffAssignments") // For adding assignments for a whole staff member
    private final Map<UUID, Map<Integer, String>> assignments;

    /**
     * Total regular hours worked by each staff member per week.
     * Outer Map: Key is Staff Member UUID.
     * Inner Map: Key is Week Index (0 to numWeeksInPeriod-1).
     * Value: Total regular hours for that week.
     * This corresponds to the TotalRegHours_nw decision variables from the LP.
     */
    @Singular("putStaffWeeklyRegularHours")
    private final Map<UUID, Map<Integer, Double>> weeklyRegularHours;

    /**
     * Total overtime hours worked by each staff member per week.
     * Outer Map: Key is Staff Member UUID.
     * Inner Map: Key is Week Index (0 to numWeeksInPeriod-1).
     * Value: Total overtime hours for that week.
     * This corresponds to the TotalOTHours_nw decision variables from the LP.
     */
    @Singular("putStaffWeeklyOvertimeHours")
    private final Map<UUID, Map<Integer, Double>> weeklyOvertimeHours;

    /**
     * Total actual hours (regular + overtime) worked by each staff member per week.
     * Outer Map: Key is Staff Member UUID.
     * Inner Map: Key is Week Index (0 to numWeeksInPeriod-1).
     * Value: Total actual hours for that week.
     * Derived from TotalRegHours_nw + TotalOTHours_nw.
     */
    @Singular("putStaffWeeklyTotalActualHours")
    private final Map<UUID, Map<Integer, Double>> weeklyTotalActualHours;

    /**
     * The total cost of the schedule, as calculated by the LP's objective function.
     * This corresponds to Z in the LP.
     */
    private final double totalCost;

    /**
     * Indicates whether the optimizer found a feasible solution.
     */
    private final boolean feasible;

    /**
     * Helper method to reconstruct a StaffMemberInterface-compatible weekly schedule
     * for a specific staff member and week from the LP output.
     *
     * @param staffMemberId The UUID of the staff member.
     * @param weekIndex The week index (0-based) for which to get the schedule.
     * @param numDaysInPeriod The total number of days in the planning period (e.g., 28).
     * @param lpShifts A map of lpShiftId to {@link ShiftDefinition}, needed to map LP shifts back to concrete {@link Shift} enums.
     * @return A HashMap representing the schedule for the week (DayOfWeek -> Shift),
     * or an empty map if no assignments are found for the staff member/week.
     */
    public HashMap<DayOfWeek, Shift> getStaffMemberWeeklySchedule(UUID staffMemberId, int weekIndex, int numDaysInPeriod, Map<String, ShiftDefinition> lpShifts) {
        HashMap<DayOfWeek, Shift> weeklySchedule = new HashMap<>();
        Map<Integer, String> dailyAssignmentsForStaff = assignments.get(staffMemberId);

        if (dailyAssignmentsForStaff == null || lpShifts == null) {
            return weeklySchedule; // No assignments for this staff or no way to map shifts
        }

        int startDayOfWeekInPeriod = weekIndex * 7;
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            int currentDayInPeriod = startDayOfWeekInPeriod + dayOffset;
            if (currentDayInPeriod >= numDaysInPeriod) {
                break; // Avoid going beyond the planning period for the last week
            }

            String lpShiftIdAssigned = dailyAssignmentsForStaff.get(currentDayInPeriod);
            if (lpShiftIdAssigned != null) {
                ShiftDefinition shiftDef = lpShifts.get(lpShiftIdAssigned);
                if (shiftDef != null) {
                    // DayOfWeek enum values are 1 (Monday) to 7 (Sunday)
                    DayOfWeek day = DayOfWeek.of(dayOffset + 1); // Assuming week starts on Monday for DayOfWeek mapping
                    weeklySchedule.put(day, shiftDef.getConcreteShift());
                }
            }
        }
        return weeklySchedule;
    }
}
