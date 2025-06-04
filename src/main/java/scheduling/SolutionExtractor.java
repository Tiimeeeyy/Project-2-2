package scheduling;

import com.google.ortools.linearsolver.MPVariable; // Assuming MPSolver might be needed for some advanced extraction, though not directly for current logic
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import staff.*;
/**
 * Helper class to extract solution data from a solved OR-Tools optimization model
 * and populate the OptimizedScheduleOutput object.
 */
public class SolutionExtractor {

    /**
     * Extracts the solution from the solved MPVariable arrays and other result parameters.
     *
     * @param fullInput             The original OptimizationInput, used for parameters like numDays/numWeeks.
     * @param scheduledStaff        The list of StaffMemberInterface objects that were included in this specific solve.
     * @param lpShiftIds            The ordered list of LP shift identifiers (String) used for indexing.
     * @param x                     The 3D array of MPVariable representing shift assignments (X_nsd).
     * @param totalRegHoursVar      The 2D array of MPVariable for weekly regular hours.
     * @param totalOtHoursVar       The 2D array of MPVariable for weekly overtime hours.
     * @param totalActualHoursVar   The 2D array of MPVariable for weekly total actual hours.
     * @param totalCost             The total cost from the objective function.
     * @param feasible              Boolean indicating if a feasible solution was found.
     * @return An {@link OptimizedScheduleOutput} object populated with the solution.
     */
    public static OptimizedScheduleOutput extract(
            OptimizationInput fullInput,
            List<StaffMemberInterface> scheduledStaff,
            List<String> lpShiftIds,
            MPVariable[][][] x,
            MPVariable[][] totalRegHoursVar,
            MPVariable[][] totalOtHoursVar,
            MPVariable[][] totalActualHoursVar,
            double totalCost,
            boolean feasible) {

        if (!feasible) {
            // If not feasible, return a default infeasible output
            return OptimizedScheduleOutput.builder()
                    .assignments(new HashMap<>())
                    .weeklyRegularHours(new HashMap<>())
                    .weeklyOvertimeHours(new HashMap<>())
                    .weeklyTotalActualHours(new HashMap<>())
                    .totalCost(0) // Or Double.NaN or some other indicator
                    .feasible(false)
                    .build();
        }

        int numStaff = scheduledStaff.size();
        int numLpShifts = lpShiftIds.size();
        int numDays = fullInput.getNumDaysInPeriod();
        int numWeeks = fullInput.getNumWeeksInPeriod();

        OptimizedScheduleOutput.OptimizedScheduleOutputBuilder outputBuilder = OptimizedScheduleOutput.builder();

        // Populate assignments
        Map<UUID, Map<Integer, String>> allAssignments = new HashMap<>();
        for (int i = 0; i < numStaff; i++) { // Index for staff in scheduledStaff and variable arrays
            UUID staffId = scheduledStaff.get(i).getId();
            Map<Integer, String> staffDailyAssignments = new HashMap<>();
            for (int d = 0; d < numDays; d++) {
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    if (x[i][sIdx][d] != null && x[i][sIdx][d].solutionValue() > 0.9) { // Using 0.9 for robustness
                        staffDailyAssignments.put(d, lpShiftIds.get(sIdx));
                        break; // Each staff member should have only one shift per day
                    }
                }
            }
            if (!staffDailyAssignments.isEmpty()) {
                allAssignments.put(staffId, staffDailyAssignments);
            }
        }
        outputBuilder.assignments(allAssignments);

        // Populate weekly hours
        Map<UUID, Map<Integer, Double>> allWeeklyRegularHours = new HashMap<>();
        Map<UUID, Map<Integer, Double>> allWeeklyOvertimeHours = new HashMap<>();
        Map<UUID, Map<Integer, Double>> allWeeklyTotalActualHours = new HashMap<>();

        for (int i = 0; i < numStaff; i++) {
            UUID staffId = scheduledStaff.get(i).getId();
            Map<Integer, Double> regHoursMap = new HashMap<>();
            Map<Integer, Double> otHoursMap = new HashMap<>();
            Map<Integer, Double> actualHoursMap = new HashMap<>();

            for (int w = 0; w < numWeeks; w++) {
                regHoursMap.put(w, totalRegHoursVar[i][w] != null ? totalRegHoursVar[i][w].solutionValue() : 0.0);
                otHoursMap.put(w, totalOtHoursVar[i][w] != null ? totalOtHoursVar[i][w].solutionValue() : 0.0);
                actualHoursMap.put(w, totalActualHoursVar[i][w] != null ? totalActualHoursVar[i][w].solutionValue() : 0.0);
            }

            allWeeklyRegularHours.put(staffId, regHoursMap);
            allWeeklyOvertimeHours.put(staffId, otHoursMap);
            allWeeklyTotalActualHours.put(staffId, actualHoursMap);
        }

        outputBuilder.weeklyRegularHours(allWeeklyRegularHours);
        outputBuilder.weeklyOvertimeHours(allWeeklyOvertimeHours);
        outputBuilder.weeklyTotalActualHours(allWeeklyTotalActualHours);

        // Set total cost and feasibility
        outputBuilder.totalCost(totalCost);
        outputBuilder.feasible(true); // Already handled infeasible case at the beginning

        return outputBuilder.build();
    }

    /**
     * Helper to build a standardized output for infeasible scenarios.
     * @param statusMessage A message describing why the solution was infeasible or the error.
     * @return An {@link OptimizedScheduleOutput} object indicating no feasible solution.
     */
    public static OptimizedScheduleOutput buildInfeasibleOutput(String statusMessage) {
        // Logger can be added here too if desired, or handled by the caller
        // System.err.println("Building infeasible output: " + statusMessage);
        return OptimizedScheduleOutput.builder()
                .assignments(new HashMap<>())
                .weeklyRegularHours(new HashMap<>())
                .weeklyOvertimeHours(new HashMap<>())
                .weeklyTotalActualHours(new HashMap<>())
                .totalCost(0)
                .feasible(false)
                .build();
    }

    /**
     * Helper to build a standardized output for scenarios with no staff to schedule.
     * @return An {@link OptimizedScheduleOutput} object indicating a feasible (empty) schedule.
     */
    public static OptimizedScheduleOutput buildEmptyFeasibleOutput() {
        return OptimizedScheduleOutput.builder()
                .assignments(new HashMap<>())
                .weeklyRegularHours(new HashMap<>())
                .weeklyOvertimeHours(new HashMap<>())
                .weeklyTotalActualHours(new HashMap<>())
                .totalCost(0)
                .feasible(true)
                .build();
    }
}
