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

import static scheduling.SolutionExtractor.*;

/**
 * Manages and solves the scheduling optimization problem specifically for Attending Physicians
 * using a Linear Programming model.
 */
public class PhysicianScheduler {

    private static final Logger logger = Logger.getLogger(PhysicianScheduler.class.getName());
    private static final double INFINITY = MPSolver.infinity(); // Using MPSolver.infinity() is cleaner

    // Policy-driven or best-practice rest period for physicians
    private static final double MINIMUM_REST_HOURS_AFTER_LONG_SHIFT = 10.0;
    private static final double LONG_SHIFT_THRESHOLD_HOURS = 12.0;

    static {
        try {
            Loader.loadNativeLibraries();
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Native OR-Tools libraries failed to load in PhysicianScheduler.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading OR-Tools libraries in PhysicianScheduler.", e);
        }
    }

    public OptimizedScheduleOutput optimizePhysicianSchedule(OptimizationInput fullInput) {
        if (fullInput == null) {
            throw new IllegalArgumentException("OptimizationInput cannot be null.");
        }

        List<StaffMemberInterface> physicianStaff = fullInput.getStaffMembers().stream()
                .filter(staff -> isAttendingPhysicianRole(staff.getRole()))
                .collect(Collectors.toList());

        if (physicianStaff.isEmpty()) {
            logger.info("No attending physician staff found in the input. Returning an empty feasible schedule.");
            return buildEmptyFeasibleOutput();
        }

        List<Demand> physicianDemands = fullInput.getDemands().stream()
                .filter(demand -> isAttendingPhysicianRole(demand.getRequiredRole()))
                .toList();

        logger.info("Optimizing schedule for " + physicianStaff.size() + " attending physicians against "
                + physicianDemands.size() + " physician demands.");

        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            logger.severe("Could not create OR-Tools solver for PhysicianScheduler.");
            return buildInfeasibleOutput("Solver creation failed.");
        }

        Map<String, ShiftDefinition> lpShifts = fullInput.getLpShifts();
        List<String> lpShiftIds = new ArrayList<>(lpShifts.keySet());

        int numStaff = physicianStaff.size();
        int numLpShifts = lpShiftIds.size();
        int numDays = fullInput.getNumDaysInPeriod();
        int numWeeks = fullInput.getNumWeeksInPeriod();

        MPVariable[][][] x = new MPVariable[numStaff][numLpShifts][numDays]; // p for physician, s for shift, d for day
        for (int p = 0; p < numStaff; p++) {
            for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                for (int d = 0; d < numDays; d++) {
                    x[p][sIdx][d] = solver.makeBoolVar("x_phy_" + physicianStaff.get(p).getId().toString().substring(0, 8) + "_" + lpShiftIds.get(sIdx) + "_" + d);
                }
            }
        }

        MPVariable[][] totalRegHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalOtHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalActualHours = new MPVariable[numStaff][numWeeks];

        // Use physician-specific parameters or those from fullInput
        int physicianMaxRegularHours = 40; // Default or policy for physicians
        int physicianMaxTotalHours = fullInput.getMaxTotalHoursPerWeek(); // e.g., from config, could be higher for physicians than nurses
        int physicianMaxDailyHours = fullInput.getMaxHoursPerDay();     // e.g., from config

        for (int p = 0; p < numStaff; p++) {
            String staffIdPrefix = physicianStaff.get(p).getId().toString().substring(0, 8);
            for (int w = 0; w < numWeeks; w++) {
                totalRegHours[p][w] = solver.makeNumVar(0.0, physicianMaxRegularHours, "phy_regH_" + staffIdPrefix + "_" + w);
                totalOtHours[p][w] = solver.makeNumVar(0.0, physicianMaxTotalHours, "phy_otH_" + staffIdPrefix + "_" + w); // Max OT can be up to MaxTotal
                totalActualHours[p][w] = solver.makeNumVar(0.0, physicianMaxTotalHours, "phy_actualH_" + staffIdPrefix + "_" + w);
            }
        }

        // Constraint: Each physician is assigned exactly one LP shift per day
        for (int p = 0; p < numStaff; p++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(1.0, 1.0, "phy_oneShift_p" + p + "_d" + d);
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    c.setCoefficient(x[p][sIdx][d], 1.0);
                }
            }
        }

        // Constraint: Link X_psd to TotalActualHours_pw
        for (int p = 0; p < numStaff; p++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "phy_actualHoursCalc_p" + p + "_w" + w);
                c.setCoefficient(totalActualHours[p][w], -1.0);
                for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                    int d = w * 7 + dayOffset;
                    if (d >= numDays) break;
                    for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                        ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(sIdx));
                        if (shiftDef != null) {
                            c.setCoefficient(x[p][sIdx][d], shiftDef.getLengthInHours());
                        }
                    }
                }
            }
        }

        // Constraint: TotalActualHours_pw = TotalRegHours_pw + TotalOtHours_pw
        for (int p = 0; p < numStaff; p++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "phy_hourComposition_p" + p + "_w" + w);
                c.setCoefficient(totalActualHours[p][w], 1.0);
                c.setCoefficient(totalRegHours[p][w], -1.0);
                c.setCoefficient(totalOtHours[p][w], -1.0);
            }
        }

        // Constraint: Max daily hours per physician
        for (int p = 0; p < numStaff; p++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(0, physicianMaxDailyHours, "phy_maxDailyH_p" + p + "_d" + d);
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(sIdx));
                    if (shiftDef != null) {
                        c.setCoefficient(x[p][sIdx][d], shiftDef.getLengthInHours());
                    }
                }
            }
        }

        // Demand/Coverage Constraint for physicianDemands
        for (Demand demand : physicianDemands) {
            Role requiredRole = demand.getRequiredRole();
            int d = demand.getDayIndex();
            String lpShiftId = demand.getLpShiftId();
            int requiredCount = demand.getRequiredCount();
            int sIdx = lpShiftIds.indexOf(lpShiftId);

            if (sIdx == -1) {
                logger.warning("LP Shift ID '" + lpShiftId + "' in physician demand not found. Skipping this demand constraint.");
                continue;
            }
            if (requiredCount <= 0) continue;

            MPConstraint c = solver.makeConstraint(requiredCount, INFINITY, "phy_demand_" + requiredRole + "_" + lpShiftId + "_d" + d);
            for (int p = 0; p < numStaff; p++) {
                if (physicianStaff.get(p).getRole() == requiredRole) {
                    c.setCoefficient(x[p][sIdx][d], 1.0);
                }
            }
        }

        // NEW CONSTRAINT: Minimum rest period after a long shift
        for (int p = 0; p < numStaff; p++) { // For each physician
            for (int d = 0; d < numDays; d++) { // For each day
                for (int sLongIdx = 0; sLongIdx < numLpShifts; sLongIdx++) {
                    ShiftDefinition longShiftDef = lpShifts.get(lpShiftIds.get(sLongIdx));

                    if (longShiftDef != null && !longShiftDef.isOffShift() && longShiftDef.getLengthInHours() >= LONG_SHIFT_THRESHOLD_HOURS) {
                        double longShiftStartHourRelativeToDayD = longShiftDef.getStartTimeInHoursFromMidnight();
                        double longShiftEndHourRelativeToDayD = longShiftStartHourRelativeToDayD + longShiftDef.getLengthInHours();

                        for (int conflictDayIndex = d; conflictDayIndex < Math.min(d + 2, numDays); conflictDayIndex++) {
                            for (int sConflictIdx = 0; sConflictIdx < numLpShifts; sConflictIdx++) {
                                if (conflictDayIndex == d && sConflictIdx == sLongIdx) continue;

                                ShiftDefinition conflictShiftDef = lpShifts.get(lpShiftIds.get(sConflictIdx));
                                if (conflictShiftDef == null || conflictShiftDef.isOffShift()) continue;

                                double conflictShiftStartHourRelativeToItsDay = conflictShiftDef.getStartTimeInHoursFromMidnight();
                                double longShiftEndAbsolute = longShiftEndHourRelativeToDayD;

                                double conflictShiftStartAbsolute;
                                if (conflictDayIndex == d) {
                                    conflictShiftStartAbsolute = conflictShiftStartHourRelativeToItsDay;
                                } else { // conflictDayIndex == d + 1
                                    conflictShiftStartAbsolute = 24.0 + conflictShiftStartHourRelativeToItsDay;
                                }

                                if (conflictShiftStartAbsolute < (longShiftEndAbsolute + MINIMUM_REST_HOURS_AFTER_LONG_SHIFT)) {
                                    MPConstraint restConstraint = solver.makeConstraint(0, 1.0,
                                            "phy_rest_p" + p + "_d" + d + "_s" + lpShiftIds.get(sLongIdx) +
                                                    "_conflicts_d" + conflictDayIndex + "_s" + lpShiftIds.get(sConflictIdx));
                                    restConstraint.setCoefficient(x[p][sLongIdx][d], 1.0);
                                    restConstraint.setCoefficient(x[p][sConflictIdx][conflictDayIndex], 1.0);
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Define Objective Function (Minimize wage costs) ---
        MPObjective objective = solver.objective();
        for (int p = 0; p < numStaff; p++) {
            StaffMemberInterface physician = physicianStaff.get(p);
            double regularWage = physician.getRegularHourlyWage();
            // Assuming physicians can also have overtime, adjust if their contracts differ
            double overtimeWage = regularWage * physician.getOvertimeMultiplier();
            for (int w = 0; w < numWeeks; w++) {
                objective.setCoefficient(totalRegHours[p][w], regularWage);
                objective.setCoefficient(totalOtHours[p][w], overtimeWage);
            }
        }
        objective.setMinimization();

        // --- 6. Solve ---
        logger.info("PhysicianScheduler: Starting OR-Tools solver...");
        final MPSolver.ResultStatus resultStatus = solver.solve();

        // --- 7. Process and Return Results ---
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            logger.info("PhysicianScheduler: Solution found! Status: " + resultStatus + ", Objective value = " + objective.value());
            return extract(fullInput, physicianStaff, lpShiftIds, x, totalRegHours, totalOtHours, totalActualHours, objective.value(), true);
        } else {
            String statusMessage = "PhysicianScheduler: No optimal or feasible solution found. Status: " + resultStatus;
            logger.warning(statusMessage);
            if (resultStatus == MPSolver.ResultStatus.INFEASIBLE) {
                logger.warning("PhysicianScheduler: Problem is INFEASIBLE. Review constraints, demands, and available staff.");
            } else if (resultStatus == MPSolver.ResultStatus.UNBOUNDED) {
                logger.warning("PhysicianScheduler: Problem is UNBOUNDED. Review objective function and constraints.");
            }
            return buildInfeasibleOutput(statusMessage);
        }
    }

    private boolean isAttendingPhysicianRole(Role role) {
        // Add all specific attending physician roles from your Role enum
        return role == Role.ATTENDING_PHYSICIAN ||
                role == Role.SURGEON || // Assuming surgeons are attending level for this scheduler
                role == Role.CARDIOLOGIST; // Assuming cardiologists are attending level
        // Exclude RESIDENT_PHYSICIAN if they have a separate scheduler or different rules
    }
}