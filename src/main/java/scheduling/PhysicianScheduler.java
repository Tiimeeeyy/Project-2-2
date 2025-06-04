package scheduling;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import staff.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static scheduling.SolutionExtractor.*;
// TODO: 10 hour rest period after 12 hour shift implementation + go over this in general
/**
 * Manages and solves the scheduling optimization problem specifically for Attending Physicians
 * using a Linear Programming model.
 */
public class PhysicianScheduler {

    private static final Logger logger = Logger.getLogger(PhysicianScheduler.class.getName());
    private static final double INFINITY = MPSolver.infinity();

    // Static block for OR-Tools native library loading (same as in NurseScheduler)
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

        // 1. Filter for Attending Physician Staff and Relevant Demands
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

        // 2. Create the solver
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

        // --- 3. Create LP Variables (X_psd, totalRegHours_pw, totalOtHours_pw, totalActualHours_pw) ---
        // 'p' for physician index
        MPVariable[][][] x = new MPVariable[numStaff][numLpShifts][numDays];
        for (int p = 0; p < numStaff; p++) {
            for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                for (int d = 0; d < numDays; d++) {
                    x[p][sIdx][d] = solver.makeBoolVar("x_phy_" + physicianStaff.get(p).getId().toString().substring(0,8) + "_" + lpShiftIds.get(sIdx) + "_" + d);
                }
            }
        }

        // Physician specific regular hours (e.g., 40h target)
        MPVariable[][] totalRegHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalOtHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalActualHours = new MPVariable[numStaff][numWeeks];

        // Use parameters from fullInput, which should be configured for physicians
        // For physicians, maxRegularHoursPerWeek might be 40.
        // maxTotalHoursPerWeek might be 48 (if strictly following nurse rule policy).
        int physicianMaxRegularHours = 40; // As per physician rule #1
        // If "same rules as nurses" for max total, use input.getMaxTotalHoursPerWeek() (e.g. 48)
        // Otherwise, this might be higher for physicians. For now, assume the nurse-like cap.
        int physicianMaxTotalHours = fullInput.getMaxTotalHoursPerWeek();
        int physicianMaxDailyHours = fullInput.getMaxHoursPerDay(); // e.g., 12

        for (int p = 0; p < numStaff; p++) {
            String staffIdPrefix = physicianStaff.get(p).getId().toString().substring(0,8);
            for (int w = 0; w < numWeeks; w++) {
                totalRegHours[p][w] = solver.makeNumVar(0.0, physicianMaxRegularHours, "phy_regH_" + staffIdPrefix + "_" + w);
                totalOtHours[p][w] = solver.makeNumVar(0.0, physicianMaxTotalHours, "phy_otH_" + staffIdPrefix + "_" + w);
                totalActualHours[p][w] = solver.makeNumVar(0.0, physicianMaxTotalHours, "phy_actualH_" + staffIdPrefix + "_" + w);
            }
        }

        // --- 4. Define Constraints ---
        // Most constraints will be structurally identical to NurseScheduler:
        // - Each physician assigned one LP shift per day: Sum_s (X_psd) = 1
        // - Link X_psd to TotalActualHours_pw (weekly actual hours from shifts)
        // - TotalActualHours_pw = TotalRegHours_pw + TotalOtHours_pw
        // - Max daily hours (using physicianMaxDailyHours)
        // - Demand/Coverage for physicianDemands

        // (Implementation of these constraints would mirror NurseScheduler, replacing 'n' with 'p'
        //  and using physician-specific parameters and variable arrays)

        // **NEW/MODIFIED CONSTRAINT: 10-hour rest after 12-hour shift**
        // This requires more detailed shift information (start/end times)
        // For now, this is a placeholder for where this logic would go.
        // If a physician p works a 12h shift s_12 on day d, they cannot work specific subsequent shifts.
        // Example conceptual logic (needs refinement based on actual shift data):
        for (int p = 0; p < numStaff; p++) {
            for (int d = 0; d < numDays; d++) {
                for (int s12Idx = 0; s12Idx < numLpShifts; s12Idx++) {
                    ShiftDefinition shift12Def = lpShifts.get(lpShiftIds.get(s12Idx));
                    if (shift12Def != null && shift12Def.getLengthInHours() == 12.0) {
                        // This is a 12-hour shift.
                        // Need to identify conflicting subsequent shifts within the next 10 hours.
                        // This part requires defining shift start/end times.
                        // If x[p][s12Idx][d] = 1, then for conflicting shifts s_conflict on day d or d+1,
                        // x[p][s_conflict_idx][d_conflict] must be 0.
                        // This would be a set of constraints like:
                        // x[p][s12Idx][d] + x[p][s_conflict_idx][d_conflict] <= 1
                        // Or more complex implications.
                        // logger.warning("Rest period constraint for 12h physician shifts needs implementation detail based on shift timings.");
                    }
                }
            }
        }
        // Due to the complexity of the rest period constraint without explicit shift timings,
        // I will proceed with the other standard constraints for now.
        // The user will need to provide shift start/end times or a clear way to define conflicting shifts
        // to fully implement the 10-hour rest rule.

        // Standard constraints (mirrored from NurseScheduler, adapted for physicians):
        // Each physician one shift per day
        for (int p = 0; p < numStaff; p++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(1.0, 1.0, "phy_oneShift_p" + p + "_d" + d);
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    c.setCoefficient(x[p][sIdx][d], 1.0);
                }
            }
        }
        // Link X_psd to TotalActualHours_pw
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
        // TotalActualHours_pw = TotalRegHours_pw + TotalOtHours_pw
        for (int p = 0; p < numStaff; p++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "phy_hourComposition_p" + p + "_w" + w);
                c.setCoefficient(totalActualHours[p][w], 1.0);
                c.setCoefficient(totalRegHours[p][w], -1.0);
                c.setCoefficient(totalOtHours[p][w], -1.0);
            }
        }
        // Max daily hours per physician
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
        // Demand/Coverage for physicianDemands
        for (Demand demand : physicianDemands) {
            Role requiredRole = demand.getRequiredRole();
            int d = demand.getDayIndex();
            String lpShiftId = demand.getLpShiftId();
            int requiredCount = demand.getRequiredCount();
            int sIdx = lpShiftIds.indexOf(lpShiftId);

            if (sIdx == -1 || requiredCount <=0) continue;

            MPConstraint c = solver.makeConstraint(requiredCount, INFINITY, "phy_demand_" + requiredRole + "_" + lpShiftId + "_d" + d);
            for (int p = 0; p < numStaff; p++) {
                if (physicianStaff.get(p).getRole() == requiredRole) {
                    c.setCoefficient(x[p][sIdx][d], 1.0);
                }
            }
        }

        // --- 5. Define Objective Function (Minimize wage costs) ---
        MPObjective objective = solver.objective();
        for (int p = 0; p < numStaff; p++) {
            StaffMemberInterface physician = physicianStaff.get(p);
            double regularWage = physician.getRegularHourlyWage();
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
            // Use a shared or adapted extractSolution method
            return extract(fullInput, physicianStaff, lpShiftIds, x, totalRegHours, totalOtHours, totalActualHours, objective.value(), true);
        } else {
            // Handle infeasible, unbounded, or error statuses
            String statusMessage = "PhysicianScheduler: No optimal or feasible solution found. Status: " + resultStatus;
            logger.warning(statusMessage);
            if (resultStatus == MPSolver.ResultStatus.INFEASIBLE) {
                logger.warning("PhysicianScheduler: Problem is INFEASIBLE. Review constraints, demands, and available staff.");
            }
            return buildInfeasibleOutput(statusMessage);
        }
    }

    // Helper to identify attending physician roles (adapt based on your Role enum)
    private boolean isAttendingPhysicianRole(Role role) {
        return role == Role.ATTENDING_PHYSICIAN || role == Role.SURGEON || role == Role.CARDIOLOGIST; // Add other non-resident physician roles
    }

}
