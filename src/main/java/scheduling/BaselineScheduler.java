package scheduling;

import staff.Demand;
import staff.Role;
import staff.Shift;
import staff.ShiftDefinition;
import staff.StaffMemberInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates a baseline schedule using a randomized assignment strategy that respects staff regulations.
 * This scheduler serves as a benchmark to compare against the optimized schedulers.
 * It prioritizes meeting demand while adhering to hard constraints like weekly hour limits and rest periods.
 */
public class BaselineScheduler {

    private static final double LONG_SHIFT_THRESHOLD_HOURS = 12.0;
    private static final double MINIMUM_REST_HOURS_AFTER_LONG_SHIFT = 10.0;

    public OptimizedScheduleOutput generateBaselineSchedule(OptimizationInput input) {
        System.out.println("Using baseline schedule:");
        List<StaffMemberInterface> staffPool = new ArrayList<>(input.getStaffMembers());
        Map<String, ShiftDefinition> lpShifts = input.getLpShifts();
        int numDays = input.getNumDaysInPeriod();
        int numWeeks = input.getNumWeeksInPeriod();

        // Initialize data structures for the new schedule
        Map<UUID, Map<Integer, String>> assignments = new HashMap<>();
        Map<UUID, Map<Integer, Double>> weeklyHours = new HashMap<>();
        for (StaffMemberInterface staff : staffPool) {
            assignments.put(staff.getId(), new HashMap<>());
            weeklyHours.put(staff.getId(), new HashMap<>());
            for (int w = 0; w < numWeeks; w++) {
                weeklyHours.get(staff.getId()).put(w, 0.0);
            }
        }

        // --- Step 1: Satisfy demands day by day ---
        // Group demands by day for easier processing
        Map<Integer, List<Demand>> demandsByDay = input.getDemands().stream()
                .collect(Collectors.groupingBy(Demand::getDayIndex));

        for (int d = 0; d < numDays; d++) {
            List<Demand> dayDemands = demandsByDay.getOrDefault(d, Collections.emptyList());
            // Shuffle staff pool to randomize who gets picked first
            Collections.shuffle(staffPool);

            for (Demand demand : dayDemands) {
                int requiredCount = demand.getRequiredCount();
                int assignedCount = 0;
                ShiftDefinition shiftToAssign = lpShifts.get(demand.getLpShiftId());
                if (shiftToAssign == null) continue;

                // Find available staff of the required role
                for (StaffMemberInterface staff : staffPool) {
                    if (assignedCount >= requiredCount) break;

                    if (staff.getRole() == demand.getRequiredRole() && isAssignmentValid(staff, shiftToAssign, d, assignments, weeklyHours, input)) {
                        assignments.get(staff.getId()).put(d, shiftToAssign.getLpShiftId());
                        // Update weekly hours
                        int week = d / 7;
                        weeklyHours.get(staff.getId()).compute(week, (k, currentHours) -> currentHours + shiftToAssign.getLengthInHours());
                        assignedCount++;
                    }
                }
            }
        }

        // --- Step 2: Assign "Off" shifts to all remaining empty slots ---
        ShiftDefinition offShift = lpShifts.values().stream()
                .filter(s -> s.getConcreteShift() == Shift.FREE).findFirst()
                .orElse(null);

        if (offShift != null) {
            for (StaffMemberInterface staff : staffPool) {
                for (int d = 0; d < numDays; d++) {
                    assignments.get(staff.getId()).putIfAbsent(d, offShift.getLpShiftId());
                }
            }
        }

        // --- Step 3: Calculate final hours and costs ---
        Map<UUID, Map<Integer, Double>> weeklyRegularHours = new HashMap<>();
        Map<UUID, Map<Integer, Double>> weeklyOvertimeHours = new HashMap<>();
        double totalCost = 0;

        for (StaffMemberInterface staff : staffPool) {
            weeklyRegularHours.put(staff.getId(), new HashMap<>());
            weeklyOvertimeHours.put(staff.getId(), new HashMap<>());

            for (int w = 0; w < numWeeks; w++) {
                double totalHours = weeklyHours.get(staff.getId()).getOrDefault(w, 0.0);
                double regularHours = Math.min(totalHours, input.getMaxRegularHoursPerWeek());
                double overtimeHours = Math.max(0, totalHours - regularHours);

                weeklyRegularHours.get(staff.getId()).put(w, regularHours);
                weeklyOvertimeHours.get(staff.getId()).put(w, overtimeHours);

                totalCost += (regularHours * staff.getRegularHourlyWage()) + (overtimeHours * staff.getRegularHourlyWage() * staff.getOvertimeMultiplier());
            }
        }


        // --- Step 4: Build and return the output object ---
        return OptimizedScheduleOutput.builder()
                .assignments(assignments)
                .weeklyRegularHours(weeklyRegularHours)
                .weeklyOvertimeHours(weeklyOvertimeHours)
                .weeklyTotalActualHours(weeklyHours)
                .totalCost(totalCost)
                .feasible(true) // A baseline schedule is always considered "feasible" in this context
                .build();
    }

    /**
     * Checks if assigning a shift to a staff member on a given day is valid against all rules.
     */
    private boolean isAssignmentValid(StaffMemberInterface staff, ShiftDefinition shift, int day,
                                      Map<UUID, Map<Integer, String>> assignments,
                                      Map<UUID, Map<Integer, Double>> weeklyHours,
                                      OptimizationInput input) {

        // 1. Check if staff already has a shift on this day
        if (assignments.get(staff.getId()).containsKey(day)) {
            return false;
        }

        // 2. Check max weekly hours
        int week = day / 7;
        double currentHours = weeklyHours.get(staff.getId()).getOrDefault(week, 0.0);
        if (currentHours + shift.getLengthInHours() > input.getMaxTotalHoursPerWeek()) {
            return false;
        }

        // 3. Check for minimum rest period if this is a long shift
        if(day > 0) {
            ShiftDefinition previousDayShift = input.getLpShifts().get(assignments.get(staff.getId()).get(day-1));
            if (previousDayShift != null && !previousDayShift.isOffShift() && previousDayShift.getLengthInHours() >= LONG_SHIFT_THRESHOLD_HOURS) {
                double prevShiftEndTime = previousDayShift.getStartTimeInHoursFromMidnight() + previousDayShift.getLengthInHours();
                double currentShiftStartTime = shift.getStartTimeInHoursFromMidnight() + 24.0; // Relative to start of previous day

                if (currentShiftStartTime < prevShiftEndTime + MINIMUM_REST_HOURS_AFTER_LONG_SHIFT) {
                    return false; // Violates rest period
                }
            }
        }

        // Add more rule checks here as needed (e.g., resident-specific ACGME rules)
        // For now, these are the most critical ones.

        return true;
    }
}
