package scheduling;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import staff.Demand;
import staff.Role;
import staff.ShiftDefinition;
import staff.StaffMemberInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static scheduling.SolutionExtractor.buildInfeasibleOutput;

/**
 * Manages and solves the scheduling optimization problem specifically for Nursing staff
 * using a Linear Programming model.
 */
public class NurseScheduler {

    private static final Logger logger = Logger.getLogger(NurseScheduler.class.getName());
    private static final double INFINITY = Double.POSITIVE_INFINITY;
    // Oregon rule: 10-hour rest period immediately following the 12th hour worked.
    private static final double MINIMUM_REST_HOURS_AFTER_LONG_SHIFT = 10.0;
    private static final double LONG_SHIFT_THRESHOLD_HOURS = 12.0; // Shifts of this length or longer trigger rest period

    static {
        try {
            // Attempt to load native OR-Tools libraries.
            Loader.loadNativeLibraries();
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Native OR-Tools libraries failed to load. Ensure the OR-Tools native library path is correctly configured (e.g., via -Djava.library.path or by having them in a system-wide accessible location).", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An unexpected error occurred while loading OR-Tools libraries.", e);
        }
    }

    public OptimizedScheduleOutput optimizeNurseSchedule(OptimizationInput fullInput) {
        if (fullInput == null) {
            throw new IllegalArgumentException("OptimizationInput cannot be null.");
        }

        // 1. Filter for Nursing Staff and Relevant Demands
        List<StaffMemberInterface> nursingStaff = fullInput.getStaffMembers().stream().filter(staff -> isNurseRole(staff.getRole())) // Assuming isNurseRole helper
                .collect(Collectors.toList());

        if (nursingStaff.isEmpty()) {
            logger.info("No nursing staff found in the input. Returning an empty feasible schedule.");
            return SolutionExtractor.buildEmptyFeasibleOutput();
        }

        List<Demand> nurseDemands = fullInput.getDemands().stream().filter(demand -> isNurseRole(demand.getRequiredRole())).toList();

        logger.info("Optimizing schedule for " + nursingStaff.size() + " nursing staff members against " + nurseDemands.size() + " nurse demands.");

        // 2. Create the solver
        MPSolver solver = MPSolver.createSolver("SCIP"); // SCIP is generally good for MILP
        if (solver == null) {
            logger.severe("Could not create OR-Tools solver. Check OR-Tools installation and library loading.");
            return buildInfeasibleOutput("Solver creation failed: OR-Tools solver not available.");
        }

        Map<String, ShiftDefinition> lpShifts = fullInput.getLpShifts();
        List<String> lpShiftIds = new ArrayList<>(lpShifts.keySet());

        int numStaff = nursingStaff.size(); // Use count of filtered nursing staff
        int numLpShifts = lpShiftIds.size();
        int numDays = fullInput.getNumDaysInPeriod();
        int numWeeks = fullInput.getNumWeeksInPeriod();

        // --- 3. Create LP Variables ---
        // X_nsd: 1 if nurse n is assigned to LP shift s on day d
        MPVariable[][][] x = new MPVariable[numStaff][numLpShifts][numDays];
        for (int n = 0; n < numStaff; n++) {
            for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                for (int d = 0; d < numDays; d++) {
                    x[n][sIdx][d] = solver.makeBoolVar("x_" + nursingStaff.get(n).getId().toString().substring(0, 8) + "_" + lpShiftIds.get(sIdx) + "_" + d);
                }
            }
        }

        // TotalRegHours_nw, TotalOTHours_nw, TotalActualHours_nw for each nurse n and week w
        MPVariable[][] totalRegHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalOtHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalActualHours = new MPVariable[numStaff][numWeeks];

        for (int n = 0; n < numStaff; n++) {
            String staffIdPrefix = nursingStaff.get(n).getId().toString().substring(0, 8);
            for (int w = 0; w < numWeeks; w++) {
                totalRegHours[n][w] = solver.makeNumVar(0.0, fullInput.getMaxRegularHoursPerWeek(), "regH_" + staffIdPrefix + "_" + w);
                totalOtHours[n][w] = solver.makeNumVar(0.0, fullInput.getMaxTotalHoursPerWeek(), "otH_" + staffIdPrefix + "_" + w);
                totalActualHours[n][w] = solver.makeNumVar(0.0, fullInput.getMaxTotalHoursPerWeek(), "actualH_" + staffIdPrefix + "_" + w);
            }
        }

        // --- 4. Define Constraints ---

        // Constraint: Each nurse is assigned exactly one LP shift per day
        for (int n = 0; n < numStaff; n++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(1.0, 1.0, "oneShift_n" + n + "_d" + d);
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    c.setCoefficient(x[n][sIdx][d], 1.0);
                }
            }
        }

        // Constraint: Link X_nsd to TotalActualHours_nw
        for (int n = 0; n < numStaff; n++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "actualHoursCalc_n" + n + "_w" + w);
                c.setCoefficient(totalActualHours[n][w], -1.0);
                for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                    int d = w * 7 + dayOffset;
                    if (d >= numDays) break;
                    for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                        ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(sIdx));
                        if (shiftDef != null) {
                            c.setCoefficient(x[n][sIdx][d], shiftDef.getLengthInHours());
                        }
                    }
                }
            }
        }

        // Constraint: TotalActualHours_nw = TotalRegHours_nw + TotalOTHours_nw
        for (int n = 0; n < numStaff; n++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "hourComposition_n" + n + "_w" + w);
                c.setCoefficient(totalActualHours[n][w], 1.0);
                c.setCoefficient(totalRegHours[n][w], -1.0);
                c.setCoefficient(totalOtHours[n][w], -1.0);
            }
        }

        // Constraint: Max daily hours per nurse
        for (int n = 0; n < numStaff; n++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(0, fullInput.getMaxHoursPerDay(), "maxDailyH_n" + n + "_d" + d);
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(sIdx));
                    if (shiftDef != null) {
                        c.setCoefficient(x[n][sIdx][d], shiftDef.getLengthInHours());
                    }
                }
            }
        }

        // Demand/Coverage Constraint for relevant nurse demands
        for (Demand demand : nurseDemands) {
            Role requiredRole = demand.getRequiredRole();
            int d = demand.getDayIndex();
            String demandedLpShiftId = demand.getLpShiftId();
            int requiredCount = demand.getRequiredCount();

            ShiftDefinition demandedShift = lpShifts.get(demandedLpShiftId);
            if (demandedShift == null || requiredCount <= 0) continue;

            MPConstraint c = solver.makeConstraint(requiredCount, INFINITY, "demand_" + requiredRole + "_" + demandedLpShiftId + "_d" + d);
            for (int n = 0; n < numStaff; n++) {
                if (nursingStaff.get(n).getRole() == requiredRole) {
                    // Check all available shifts to see if they can cover the demanded shift
                    for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                        ShiftDefinition potentialShift = lpShifts.get(lpShiftIds.get(sIdx));
                        if (potentialShift.covers(demandedShift)) {
                            // If this shift (e.g., d12) covers the demand (e.g., d8), add it to the constraint
                            c.setCoefficient(x[n][sIdx][d], 1.0);
                        }
                    }
                }
            }
        }

        // Minimum rest period after a long shift (e.g., 10 hours after a 12-hour shift)
        for (int n = 0; n < numStaff; n++) { // For each nurse
            for (int d = 0; d < numDays; d++) { // For each day in the planning period
                for (int sLongIdx = 0; sLongIdx < numLpShifts; sLongIdx++) { // Iterate over all possible shifts for nurse n on day d
                    ShiftDefinition longShiftDef = lpShifts.get(lpShiftIds.get(sLongIdx));

                    // Check if this is a "long shift" that triggers the rest rule
                    if (longShiftDef != null && !longShiftDef.isOffShift() && longShiftDef.getLengthInHours() >= LONG_SHIFT_THRESHOLD_HOURS) {

                        // Calculate the end time of this long shift.
                        // Start time is hours from midnight of day 'd'.
                        // End time can extend past 24.0 (into the next day relative to day 'd's start).
                        double longShiftStartHourRelativeToDayD = longShiftDef.getStartTimeInHoursFromMidnight();
                        double longShiftEndHourRelativeToDayD = longShiftStartHourRelativeToDayD + longShiftDef.getLengthInHours();

                        // Iterate through all potentially conflicting shifts on the same day 'd' and the next day 'd+1'
                        for (int conflictDayIndex = d; conflictDayIndex < Math.min(d + 2, numDays); conflictDayIndex++) {
                            for (int sConflictIdx = 0; sConflictIdx < numLpShifts; sConflictIdx++) {
                                // A shift cannot conflict with itself
                                if (conflictDayIndex == d && sConflictIdx == sLongIdx) continue;

                                ShiftDefinition conflictShiftDef = lpShifts.get(lpShiftIds.get(sConflictIdx));
                                // Off shifts do not participate in this conflict
                                if (conflictShiftDef == null || conflictShiftDef.isOffShift()) continue;

                                double conflictShiftStartHourRelativeToItsDay = conflictShiftDef.getStartTimeInHoursFromMidnight();

                                // Convert start/end times to a consistent absolute timeline (hours from start of day 'd')
                                // to correctly handle shifts spanning midnight and comparing across d and d+1.
                                double longShiftEndAbsolute = longShiftEndHourRelativeToDayD; // Already relative to day 'd' start

                                double conflictShiftStartAbsolute;
                                if (conflictDayIndex == d) {
                                    conflictShiftStartAbsolute = conflictShiftStartHourRelativeToItsDay;
                                } else { // conflictDayIndex == d + 1
                                    conflictShiftStartAbsolute = 24.0 + conflictShiftStartHourRelativeToItsDay; // Starts on next day relative to day 'd'
                                }

                                // If the conflicting shift starts before the mandatory rest period is over
                                if (conflictShiftStartAbsolute < (longShiftEndAbsolute + MINIMUM_REST_HOURS_AFTER_LONG_SHIFT)) {
                                    // Add constraint: x[n][sLongIdx][d] + x[n][sConflictIdx][conflictDayIndex] <= 1
                                    // This means if x[n][sLongIdx][d] is 1, then x[n][sConflictIdx][conflictDayIndex] must be 0.
                                    MPConstraint restConstraint = solver.makeConstraint(0, 1.0, "rest_n" + n + "_d" + d + "_s" + lpShiftIds.get(sLongIdx) + "_conflicts_d" + conflictDayIndex + "_s" + lpShiftIds.get(sConflictIdx));
                                    restConstraint.setCoefficient(x[n][sLongIdx][d], 1.0);
                                    restConstraint.setCoefficient(x[n][sConflictIdx][conflictDayIndex], 1.0);
                                    // logger.log(Level.FINER, "Added rest constraint for nurse " + nursingStaff.get(n).getId() +
                                    // ": Day " + d + " Shift " + lpShiftIds.get(sLongIdx) + " conflicts with Day " +
                                    // conflictDayIndex + " Shift " + lpShiftIds.get(sConflictIdx) );
                                }
                            }
                        }
                    }
                }
            }
        }


        // --- 5. Define Objective Function ---
        MPObjective objective = solver.objective();
        for (int n = 0; n < numStaff; n++) {
            StaffMemberInterface nurse = nursingStaff.get(n);
            double regularWage = nurse.getRegularHourlyWage();
            double overtimeWage = regularWage * nurse.getOvertimeMultiplier();
            for (int w = 0; w < numWeeks; w++) {
                objective.setCoefficient(totalRegHours[n][w], regularWage);
                objective.setCoefficient(totalOtHours[n][w], overtimeWage);
            }
        }
        objective.setMinimization();

        // --- 6. Solve ---
        logger.info("NurseScheduler: Starting OR-Tools solver...");
        final MPSolver.ResultStatus resultStatus = solver.solve();

        // --- 7. Process and Return Results ---
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            logger.info("NurseScheduler: Solution found! Status: " + resultStatus + ", Objective value = " + objective.value());
            return SolutionExtractor.extract(fullInput, nursingStaff, lpShiftIds, x, totalRegHours, totalOtHours, totalActualHours, objective.value(), true);
        } else {
            String statusMessage = "NurseScheduler: No optimal or feasible solution found. Status: " + resultStatus;
            logger.warning(statusMessage);
            if (resultStatus == MPSolver.ResultStatus.INFEASIBLE) {
                logger.warning("NurseScheduler: Problem is INFEASIBLE. Review constraints, demands, and available staff.");
            } else if (resultStatus == MPSolver.ResultStatus.UNBOUNDED) {
                logger.warning("NurseScheduler: Problem is UNBOUNDED. Review objective function and constraints.");
            }
            return buildInfeasibleOutput(statusMessage);
        }
    }

    // Helper to check if a role is a nursing role (can be expanded or moved to Role enum)
    private boolean isNurseRole(Role role) {
        return role == Role.REGISTERED_NURSE || role == Role.LICENSED_PRACTICAL_NURSE || role == Role.CERTIFIED_NURSING_ASSISTANT || // If CNAs are scheduled by NurseScheduler
                role == Role.NURSE_PRACTITIONER || role == Role.CLINICAL_NURSE_SPECIALIST || role == Role.CERTIFIED_REGISTERED_NURSE_ANESTHETIST;
    }
}