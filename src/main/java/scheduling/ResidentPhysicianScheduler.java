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

import static scheduling.SolutionExtractor.buildEmptyFeasibleOutput;
import static scheduling.SolutionExtractor.buildInfeasibleOutput;

/**
 * Manages and solves the scheduling optimization problem specifically for Resident Physicians
 * incorporating rules like ACGME work hour limits.
 * Assumes OptimizationInput is configured with parameters suitable for residents
 * (e.g., maxDailyHours, maxTotalHoursPerWeek for residents).
 */
public class ResidentPhysicianScheduler {

    private static final Logger logger = Logger.getLogger(ResidentPhysicianScheduler.class.getName());
    private static final double INFINITY = MPSolver.infinity();

    // Common Resident Physician Rules (inspired by ACGME guidelines)
    // These constants define the core resident-specific rules.
    // OptimizationInput should be configured to align with these where it provides parameters (e.g., maxTotalHoursPerWeek = 80).
    private static final double RESIDENT_MAX_HOURS_PER_WEEK_HARD_CAP = 80.0; // Hard cap for any single week
    private static final double RESIDENT_MAX_AVG_HOURS_PER_WEEK_OVER_PERIOD = 80.0; // Averaged over scheduling period
    private static final double MINIMUM_DAYS_OFF_PER_WEEK = 1.0;
    private static final double MINIMUM_REST_HOURS_AFTER_LONG_SHIFT = 10.0; // Or other ACGME/institutional value
    private static final double LONG_SHIFT_THRESHOLD_HOURS = 12.0; // Shifts triggering rest

    static {
        try {
            Loader.loadNativeLibraries();
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Native OR-Tools libraries failed to load in ResidentPhysicianScheduler.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading OR-Tools libraries in ResidentPhysicianScheduler.", e);
        }
    }

    public OptimizedScheduleOutput optimizeResidentSchedule(OptimizationInput fullInput) {
        if (fullInput == null) {
            throw new IllegalArgumentException("OptimizationInput cannot be null.");
        }

        List<StaffMemberInterface> residentStaff = fullInput.getStaffMembers().stream()
                .filter(staff -> staff.getRole() == Role.RESIDENT_PHYSICIAN || staff.getRole() == Role.CARDIOLOGIST)
                .collect(Collectors.toList());

        if (residentStaff.isEmpty()) {
            logger.info("No resident physician staff found. Returning empty feasible schedule.");
            return buildEmptyFeasibleOutput();
        }

        // Assuming demands are also filtered or specific to residents if this scheduler is called
        List<Demand> residentDemands = fullInput.getDemands().stream()
                .filter(demand -> demand.getRequiredRole() == Role.RESIDENT_PHYSICIAN)
                .toList();

        logger.info("Optimizing schedule for " + residentStaff.size() + " resident physicians against "
                + residentDemands.size() + " resident demands.");

        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            logger.severe("Could not create OR-Tools solver for ResidentPhysicianScheduler.");
            return buildInfeasibleOutput("Solver creation failed.");
        }

        Map<String, ShiftDefinition> lpShifts = fullInput.getLpShifts();
        List<String> lpShiftIds = new ArrayList<>(lpShifts.keySet());
        int sFreeIdx = -1;
        for (int i = 0; i < lpShiftIds.size(); i++) {
            ShiftDefinition sd = lpShifts.get(lpShiftIds.get(i));
            if (sd != null && sd.isOffShift()) { // Assumes Shift.FREE correctly sets isOffShift
                sFreeIdx = i;
                logger.fine("Identified FREE shift for residents: " + lpShiftIds.get(i) + " at index " + sFreeIdx);
                break;
            }
        }
        if (sFreeIdx == -1) {
            logger.warning("No 'FREE' or 'OFF' shift type found in lpShifts. Day off constraints for residents will not be effective.");
        }

        int numStaff = residentStaff.size();
        int numLpShifts = lpShiftIds.size();
        int numDays = fullInput.getNumDaysInPeriod();
        int numWeeks = fullInput.getNumWeeksInPeriod();

        MPVariable[][][] x = new MPVariable[numStaff][numLpShifts][numDays];
        for (int r = 0; r < numStaff; r++) {
            for (int s = 0; s < numLpShifts; s++) {
                for (int d = 0; d < numDays; d++) {
                    x[r][s][d] = solver.makeBoolVar("x_res_" + residentStaff.get(r).getId().toString().substring(0, 8) + "_" + lpShiftIds.get(s) + "_" + d);
                }
            }
        }

        MPVariable[][] totalRegHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalOtHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalActualHours = new MPVariable[numStaff][numWeeks];

        // Max regular hours for residents (e.g., 40h). If all 80h are "regular", set this high in input.
        double residentConfiguredMaxRegHours = fullInput.getMaxRegularHoursPerWeek();
        // Max total hours for residents for a single week (e.g., 80h). This should come from input specific to residents.
        double residentConfiguredMaxTotalHoursWeek = fullInput.getMaxTotalHoursPerWeek();


        for (int r = 0; r < numStaff; r++) {
            String staffIdPrefix = residentStaff.get(r).getId().toString().substring(0, 8);
            for (int w = 0; w < numWeeks; w++) {
                // Regular hours capped by specific input, but not more than the hard cap for total hours.
                totalRegHours[r][w] = solver.makeNumVar(0.0, Math.min(residentConfiguredMaxRegHours, RESIDENT_MAX_HOURS_PER_WEEK_HARD_CAP), "res_regH_" + staffIdPrefix + "_" + w);
                // OT hours can make up the difference to the hard cap.
                totalOtHours[r][w] = solver.makeNumVar(0.0, RESIDENT_MAX_HOURS_PER_WEEK_HARD_CAP, "res_otH_" + staffIdPrefix + "_" + w);
                // Actual total hours for the week, capped by the configured input (expected to be ~80 for residents) AND the hard rule.
                totalActualHours[r][w] = solver.makeNumVar(0.0, Math.min(residentConfiguredMaxTotalHoursWeek, RESIDENT_MAX_HOURS_PER_WEEK_HARD_CAP) , "res_actualH_" + staffIdPrefix + "_" + w);
            }
        }

        // --- Constraints ---
        // 1. One shift per resident per day
        for (int r = 0; r < numStaff; r++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(1.0, 1.0, "res_oneShift_r" + r + "_d" + d);
                for (int s = 0; s < numLpShifts; s++) {
                    c.setCoefficient(x[r][s][d], 1.0);
                }
            }
        }

        // 2. Link X_rsd to TotalActualHours_rw
        for (int r = 0; r < numStaff; r++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "res_actualHoursCalc_r" + r + "_w" + w);
                c.setCoefficient(totalActualHours[r][w], -1.0); // totalActualHours[r][w] = Sum (x*length)
                for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                    int d = w * 7 + dayOffset;
                    if (d >= numDays) break;
                    for (int s = 0; s < numLpShifts; s++) {
                        ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(s));
                        if (shiftDef != null && !shiftDef.isOffShift()) { // Only count work shifts
                            c.setCoefficient(x[r][s][d], shiftDef.getLengthInHours());
                        }
                    }
                }
            }
        }

        // 3. TotalActualHours_rw = TotalRegHours_rw + TotalOtHours_rw
        for (int r = 0; r < numStaff; r++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "res_hourComposition_r" + r + "_w" + w);
                c.setCoefficient(totalActualHours[r][w], 1.0);
                c.setCoefficient(totalRegHours[r][w], -1.0);
                c.setCoefficient(totalOtHours[r][w], -1.0);
            }
        }

        // 4. Max daily hours for a resident (e.g., 24h + 4h transition = 28h)
        // This limit is taken from fullInput.getMaxHoursPerDay(), assumed to be set for residents.
        for (int r = 0; r < numStaff; r++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(0, fullInput.getMaxHoursPerDay(), "res_maxDailyH_r" + r + "_d" + d);
                for (int s = 0; s < numLpShifts; s++) {
                    ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(s));
                    if (shiftDef != null && !shiftDef.isOffShift()) {
                        c.setCoefficient(x[r][s][d], shiftDef.getLengthInHours());
                    }
                }
            }
        }

        // 5. Demand Coverage (if residents directly cover specific demands)
        for (Demand demand : residentDemands) {
            Role requiredRole = demand.getRequiredRole();
            if (requiredRole != Role.RESIDENT_PHYSICIAN) continue; // Should be filtered already but double check

            int d = demand.getDayIndex();
            String lpShiftId = demand.getLpShiftId();
            int requiredCount = demand.getRequiredCount();
            int s = lpShiftIds.indexOf(lpShiftId);

            if (s == -1 || requiredCount <= 0) continue;

            MPConstraint c = solver.makeConstraint(requiredCount, INFINITY, "res_demand_" + lpShiftId + "_d" + d);
            for (int r_idx = 0; r_idx < numStaff; r_idx++) {
                c.setCoefficient(x[r_idx][s][d], 1.0);
            }
        }

        // 6. Minimum Rest Period (e.g., 10 hours after a 12+ hour shift)
        for (int r = 0; r < numStaff; r++) {
            for (int d = 0; d < numDays; d++) {
                for (int sLongIdx = 0; sLongIdx < numLpShifts; sLongIdx++) {
                    ShiftDefinition longShiftDef = lpShifts.get(lpShiftIds.get(sLongIdx));
                    if (longShiftDef != null && !longShiftDef.isOffShift() && longShiftDef.getLengthInHours() >= LONG_SHIFT_THRESHOLD_HOURS) {
                        double longShiftStartHourRelToDayD = longShiftDef.getStartTimeInHoursFromMidnight();
                        double longShiftEndHourRelToDayD = longShiftStartHourRelToDayD + longShiftDef.getLengthInHours();
                        for (int conflictDayIdx = d; conflictDayIdx < Math.min(d + 2, numDays); conflictDayIdx++) {
                            for (int sConflictIdx = 0; sConflictIdx < numLpShifts; sConflictIdx++) {
                                if (conflictDayIdx == d && sConflictIdx == sLongIdx) continue;
                                ShiftDefinition conflictShiftDef = lpShifts.get(lpShiftIds.get(sConflictIdx));
                                if (conflictShiftDef == null || conflictShiftDef.isOffShift()) continue;

                                double conflictShiftStartHourRelToItsDay = conflictShiftDef.getStartTimeInHoursFromMidnight();
                                double conflictShiftStartAbs = (conflictDayIdx == d) ? conflictShiftStartHourRelToItsDay : 24.0 + conflictShiftStartHourRelToItsDay;

                                if (conflictShiftStartAbs < (longShiftEndHourRelToDayD + MINIMUM_REST_HOURS_AFTER_LONG_SHIFT)) {
                                    MPConstraint restC = solver.makeConstraint(0, 1.0, "res_rest_r" + r + "_d" + d + "_s" + lpShiftIds.get(sLongIdx) + "_vs_d" + conflictDayIdx + "_s" + lpShiftIds.get(sConflictIdx));
                                    restC.setCoefficient(x[r][sLongIdx][d], 1.0);
                                    restC.setCoefficient(x[r][sConflictIdx][conflictDayIdx], 1.0);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Max hours per week (averaged over period, and hard cap on individual weeks via variable bounds)
        if (numWeeks > 0) {
            for (int r = 0; r < numStaff; r++) {
                // The individual week cap is RESIDENT_MAX_HOURS_PER_WEEK_HARD_CAP (e.g. 80) via totalActualHours[r][w] variable bounds.
                // Constraint for average hours over the entire scheduling period.
                MPConstraint avgHoursC = solver.makeConstraint(0, RESIDENT_MAX_AVG_HOURS_PER_WEEK_OVER_PERIOD * numWeeks, "res_avgWeeklyHours_r" + r);
                for (int w = 0; w < numWeeks; w++) {
                    avgHoursC.setCoefficient(totalActualHours[r][w], 1.0);
                }
            }
        }

        // 8. At least 1 day off per week
        if (sFreeIdx != -1 && numWeeks > 0) {
            for (int r = 0; r < numStaff; r++) {
                for (int w = 0; w < numWeeks; w++) {
                    MPConstraint dayOffC = solver.makeConstraint(MINIMUM_DAYS_OFF_PER_WEEK, 7.0, "res_weeklyDayOff_r" + r + "_w" + w);
                    for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                        int d = w * 7 + dayOffset;
                        if (d >= numDays) break;
                        dayOffC.setCoefficient(x[r][sFreeIdx][d], 1.0);
                    }
                }
            }
        }

        // --- Objective Function ---
        // Cost based on resident's defined wage and overtime multiplier.
        // If residents are salaried with no traditional "overtime pay" for hours within the 80h limit,
        // their overtimeMultiplier in StaffMemberInterface should be 1.0.
        MPObjective objective = solver.objective();
        for (int r = 0; r < numStaff; r++) {
            StaffMemberInterface resident = residentStaff.get(r);
            double regularWage = resident.getRegularHourlyWage();
            double overtimeRate = regularWage * resident.getOvertimeMultiplier();

            for (int w = 0; w < numWeeks; w++) {
                objective.setCoefficient(totalRegHours[r][w], regularWage);
                objective.setCoefficient(totalOtHours[r][w], overtimeRate);
            }
        }
        objective.setMinimization();

        // --- Solve ---
        logger.info("ResidentPhysicianScheduler: Starting OR-Tools solver...");
        final MPSolver.ResultStatus resultStatus = solver.solve();

        // --- Process Results ---
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            logger.info("ResidentPhysicianScheduler: Solution found! Status: " + resultStatus + ", Objective: " + objective.value());
            return SolutionExtractor.extract(fullInput, residentStaff, lpShiftIds, x, totalRegHours, totalOtHours, totalActualHours, objective.value(), true);
        } else {
            String statusMessage = "ResidentPhysicianScheduler: No solution. Status: " + resultStatus;
            logger.warning(statusMessage);
            if (resultStatus == MPSolver.ResultStatus.INFEASIBLE) {
                logger.warning("ResidentPhysicianScheduler: Problem is INFEASIBLE. Review constraints, demands, and available staff, especially resident-specific hour limits and days off rules.");
            }
            return buildInfeasibleOutput(statusMessage);
        }
    }
}
