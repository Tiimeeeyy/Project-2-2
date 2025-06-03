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

/**
 * Manages and solves the scheduling optimization problem specifically for Nursing staff
 * using a Linear Programming model.
 */
public class NurseScheduler {

    private static final Logger logger = Logger.getLogger(NurseScheduler.class.getName());
    private static final double INFINITY = Double.POSITIVE_INFINITY;

    static {
        try {
            // Attempt to load native OR-Tools libraries.
            // This should ideally be called once at application startup.
            Loader.loadNativeLibraries();
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Native OR-Tools libraries failed to load. Ensure the OR-Tools native library path is correctly configured (e.g., via -Djava.library.path or by having them in a system-wide accessible location).", e);
            // Depending on the application, you might rethrow this as a runtime exception
            // to halt initialization if the scheduler cannot function.
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An unexpected error occurred while loading OR-Tools libraries.", e);
        }
    }

    public OptimizedScheduleOutput optimizeNurseSchedule(OptimizationInput fullInput) {
        if (fullInput == null) {
            throw new IllegalArgumentException("OptimizationInput cannot be null.");
        }

        // 1. Filter for Nursing Staff and Relevant Demands
        List<StaffMemberInterface> nursingStaff = fullInput.getStaffMembers().stream()
                .filter(staff -> isNurseRole(staff.getRole())) // Assuming isNurseRole helper
                .collect(Collectors.toList());

        if (nursingStaff.isEmpty()) {
            logger.info("No nursing staff found in the input. Returning an empty feasible schedule.");
            return buildEmptyFeasibleOutput();
        }

        List<Demand> nurseDemands = fullInput.getDemands().stream()
                .filter(demand -> isNurseRole(demand.getRequiredRole()))
                .toList();

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
                    // Using staff ID in variable name for better traceability if inspecting LP file
                    x[n][sIdx][d] = solver.makeBoolVar("x_" + nursingStaff.get(n).getId().toString().substring(0,8) + "_" + lpShiftIds.get(sIdx) + "_" + d);
                }
            }
        }

        // TotalRegHours_nw, TotalOTHours_nw, TotalActualHours_nw for each nurse n and week w
        MPVariable[][] totalRegHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalOtHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalActualHours = new MPVariable[numStaff][numWeeks];

        for (int n = 0; n < numStaff; n++) {
            String staffIdPrefix = nursingStaff.get(n).getId().toString().substring(0,8);
            for (int w = 0; w < numWeeks; w++) {
                totalRegHours[n][w] = solver.makeNumVar(0.0, fullInput.getMaxRegularHoursPerWeek(), "regH_" + staffIdPrefix + "_" + w);
                totalOtHours[n][w] = solver.makeNumVar(0.0, fullInput.getMaxTotalHoursPerWeek(), "otH_" + staffIdPrefix + "_" + w); // Max OT could be MaxTotal - MinReg (0)
                totalActualHours[n][w] = solver.makeNumVar(0.0, fullInput.getMaxTotalHoursPerWeek(), "actualH_" + staffIdPrefix + "_" + w);
            }
        }

        // --- 4. Define Constraints (using the LP formulation for nurses) ---

        // Constraint: Each nurse is assigned exactly one LP shift per day
        // Assumes 'F' (Free/Off) is a defined LP shift.
        for (int n = 0; n < numStaff; n++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(1.0, 1.0, "oneShift_n" + n + "_d" + d);
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    c.setCoefficient(x[n][sIdx][d], 1.0);
                }
            }
        }

        // Constraint: Link X_nsd to TotalActualHours_nw (Weekly actual hours from shifts)
        // Sum_{d in w} Sum_s (X_nsd * ShiftLength_s) = TotalActualHours_nw
        for (int n = 0; n < numStaff; n++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "actualHoursCalc_n" + n + "_w" + w);
                c.setCoefficient(totalActualHours[n][w], -1.0); // TotalActualHours_nw is on RHS, so -1 when moved to LHS
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

        // Note: TotalRegHours_nw <= maxRegularHoursPerWeek (Constraint 4) is handled by variable bounds.
        // Note: TotalOTHours_nw >= 0 (Constraint 5) is handled by variable bounds.
        // Note: TotalActualHours_nw <= maxTotalHoursPerWeek (Constraint 6) is handled by variable bounds.

        // Constraint: Max daily hours per nurse
        // Sum_s (X_nsd * ShiftLength_s) <= maxHoursPerDay
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
            Role requiredRole = demand.getRequiredRole(); // This will be a nursing role
            int d = demand.getDayIndex();
            String lpShiftId = demand.getLpShiftId();
            int requiredCount = demand.getRequiredCount();

            int sIdx = lpShiftIds.indexOf(lpShiftId);
            if (sIdx == -1) {
                logger.warning("LP Shift ID '" + lpShiftId + "' in nurse demand not found. Skipping this demand constraint.");
                continue;
            }
            if (requiredCount <= 0) continue; // No demand to enforce

            MPConstraint c = solver.makeConstraint(requiredCount, INFINITY, "demand_" + requiredRole + "_" + lpShiftId + "_d" + d);
            for (int n = 0; n < numStaff; n++) {
                // Only sum up nurses of the specifically required role for this demand
                if (nursingStaff.get(n).getRole() == requiredRole) {
                    c.setCoefficient(x[n][sIdx][d], 1.0);
                }
            }
        }

        // --- 5. Define Objective Function ---
        // Minimize Z = Sum_n Sum_w (RWage_n * TotalRegHours_nw + OTWage_n * TotalOTHours_nw)
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
        // solver.setTimeLimit(120000); // Optional: e.g., 2 minutes time limit

        final MPSolver.ResultStatus resultStatus = solver.solve();

        // --- 7. Process and Return Results ---
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            logger.info("NurseScheduler: Solution found! Status: " + resultStatus + ", Objective value = " + objective.value());
            return extractSolution(solver, fullInput, nursingStaff, lpShiftIds, x, totalRegHours, totalOtHours, totalActualHours, objective.value(), true);
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
        // Add all nursing roles from your Role enum
        return role == Role.REGISTERED_NURSE ||
                role == Role.LICENSED_PRACTICAL_NURSE ||
                role == Role.CERTIFIED_NURSING_ASSISTANT || // If CNAs are scheduled by NurseScheduler
                role == Role.NURSE_PRACTITIONER ||
                role == Role.CLINICAL_NURSE_SPECIALIST ||
                role == Role.CERTIFIED_REGISTERED_NURSE_ANESTHETIST;
        // Add other specific nurse roles defined in your Role enum
    }

    private OptimizedScheduleOutput extractSolution(MPSolver solver, OptimizationInput fullInput,
                                                    List<StaffMemberInterface> scheduledStaff, List<String> lpShiftIds,
                                                    MPVariable[][][] x, MPVariable[][] totalRegHoursVar,
                                                    MPVariable[][] totalOtHoursVar, MPVariable[][] totalActualHoursVar,
                                                    double totalCost, boolean feasible) {

        int numStaff = scheduledStaff.size();
        int numLpShifts = lpShiftIds.size();
        int numDays = fullInput.getNumDaysInPeriod();
        int numWeeks = fullInput.getNumWeeksInPeriod();

        OptimizedScheduleOutput.OptimizedScheduleOutputBuilder outputBuilder = OptimizedScheduleOutput.builder();

        // Populate assignments
        Map<UUID, Map<Integer, String>> allAssignments = new HashMap<>();
        for (int n = 0; n < numStaff; n++) {
            UUID staffId = scheduledStaff.get(n).getId();
            Map<Integer, String> staffDailyAssignments = new HashMap<>();
            for (int d = 0; d < numDays; d++) {
                for (int sIdx = 0; sIdx < numLpShifts; sIdx++) {
                    // Check if the variable exists and its solution value is close to 1
                    if (x[n][sIdx][d] != null && x[n][sIdx][d].solutionValue() > 0.9) {
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

        for (int n = 0; n < numStaff; n++) {
            UUID staffId = scheduledStaff.get(n).getId();
            Map<Integer, Double> regHoursMap = new HashMap<>();
            Map<Integer, Double> otHoursMap = new HashMap<>();
            Map<Integer, Double> actualHoursMap = new HashMap<>();

            for (int w = 0; w < numWeeks; w++) {
                regHoursMap.put(w, totalRegHoursVar[n][w] != null ? totalRegHoursVar[n][w].solutionValue() : 0.0);
                otHoursMap.put(w, totalOtHoursVar[n][w] != null ? totalOtHoursVar[n][w].solutionValue() : 0.0);
                actualHoursMap.put(w, totalActualHoursVar[n][w] != null ? totalActualHoursVar[n][w].solutionValue() : 0.0);
            }

            allWeeklyRegularHours.put(staffId, regHoursMap);
            allWeeklyOvertimeHours.put(staffId, otHoursMap); // This was missing population
            allWeeklyTotalActualHours.put(staffId, actualHoursMap); // This was missing population
        }

        outputBuilder.weeklyRegularHours(allWeeklyRegularHours);
        outputBuilder.weeklyOvertimeHours(allWeeklyOvertimeHours);
        outputBuilder.weeklyTotalActualHours(allWeeklyTotalActualHours);

        // Set total cost and feasibility
        outputBuilder.totalCost(totalCost);
        outputBuilder.feasible(feasible);

        return outputBuilder.build();
    }

    private OptimizedScheduleOutput buildInfeasibleOutput(String statusMessage) {
        logger.warning("Building infeasible/error output for NurseScheduler: " + statusMessage);
        // Return an empty but valid OptimizedScheduleOutput object
        return OptimizedScheduleOutput.builder()
                .assignments(new HashMap<>())
                .weeklyRegularHours(new HashMap<>())
                .weeklyOvertimeHours(new HashMap<>())
                .weeklyTotalActualHours(new HashMap<>())
                .totalCost(0)
                .feasible(false)
                .build();
    }

    private OptimizedScheduleOutput buildEmptyFeasibleOutput() {
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
