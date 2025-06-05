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
 * Manages and solves the scheduling optimization problem for Administrative Staff.
 * Assumes OptimizationInput is configured with parameters suitable for admin staff.
 */
public class AdminStaffScheduler {

    private static final Logger logger = Logger.getLogger(AdminStaffScheduler.class.getName());
    private static final double INFINITY = MPSolver.infinity();

    // Typical for full-time admin staff, can be adjusted or made more flexible
    private static final double MINIMUM_DAYS_OFF_PER_WEEK_ADMIN = 2.0;

    static {
        try {
            Loader.loadNativeLibraries();
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Native OR-Tools libraries failed to load in AdminStaffScheduler.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading OR-Tools libraries in AdminStaffScheduler.", e);
        }
    }

    public OptimizedScheduleOutput optimizeAdminSchedule(OptimizationInput fullInput) {
        if (fullInput == null) {
            throw new IllegalArgumentException("OptimizationInput cannot be null.");
        }

        List<StaffMemberInterface> adminStaffList = fullInput.getStaffMembers().stream()
                .filter(staff -> isAdminRole(staff.getRole()))
                .collect(Collectors.toList());

        if (adminStaffList.isEmpty()) {
            logger.info("No administrative staff found. Returning empty feasible schedule.");
            return buildEmptyFeasibleOutput();
        }

        List<Demand> adminDemands = fullInput.getDemands().stream()
                .filter(demand -> isAdminRole(demand.getRequiredRole()))
                .toList();

        logger.info("Optimizing schedule for " + adminStaffList.size() + " administrative staff against "
                + adminDemands.size() + " admin demands.");

        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            logger.severe("Could not create OR-Tools solver for AdminStaffScheduler.");
            return buildInfeasibleOutput("Solver creation failed.");
        }

        Map<String, ShiftDefinition> lpShifts = fullInput.getLpShifts();
        List<String> lpShiftIds = new ArrayList<>(lpShifts.keySet());
        int sFreeIdx = -1;
        for (int i = 0; i < lpShiftIds.size(); i++) {
            ShiftDefinition sd = lpShifts.get(lpShiftIds.get(i));
            if (sd != null && sd.isOffShift()) {
                sFreeIdx = i;
                logger.fine("Identified FREE shift for admin staff: " + lpShiftIds.get(i) + " at index " + sFreeIdx);
                break;
            }
        }
        if (sFreeIdx == -1) {
            logger.warning("No 'FREE' or 'OFF' shift type found in lpShifts. Day off constraints for admin staff may not be effective.");
        }

        int numStaff = adminStaffList.size();
        int numLpShifts = lpShiftIds.size();
        int numDays = fullInput.getNumDaysInPeriod();
        int numWeeks = fullInput.getNumWeeksInPeriod();

        // Variables: x[a][s][d] for admin 'a', shift 's', day 'd'
        MPVariable[][][] x = new MPVariable[numStaff][numLpShifts][numDays];
        for (int a = 0; a < numStaff; a++) {
            for (int s = 0; s < numLpShifts; s++) {
                for (int d = 0; d < numDays; d++) {
                    x[a][s][d] = solver.makeBoolVar("x_adm_" + adminStaffList.get(a).getId().toString().substring(0, 8) + "_" + lpShiftIds.get(s) + "_" + d);
                }
            }
        }

        MPVariable[][] totalRegHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalOtHours = new MPVariable[numStaff][numWeeks];
        MPVariable[][] totalActualHours = new MPVariable[numStaff][numWeeks];

        // Use parameters from fullInput, assumed to be configured for admin staff
        // e.g., maxRegularHoursPerWeek = 40, maxTotalHoursPerWeek = 48 (or as per policy)
        double adminMaxRegularHoursWeek = fullInput.getMaxRegularHoursPerWeek();
        double adminMaxTotalHoursWeek = fullInput.getMaxTotalHoursPerWeek();

        for (int a = 0; a < numStaff; a++) {
            String staffIdPrefix = adminStaffList.get(a).getId().toString().substring(0, 8);
            for (int w = 0; w < numWeeks; w++) {
                totalRegHours[a][w] = solver.makeNumVar(0.0, adminMaxRegularHoursWeek, "adm_regH_" + staffIdPrefix + "_" + w);
                totalOtHours[a][w] = solver.makeNumVar(0.0, adminMaxTotalHoursWeek, "adm_otH_" + staffIdPrefix + "_" + w);
                totalActualHours[a][w] = solver.makeNumVar(0.0, adminMaxTotalHoursWeek, "adm_actualH_" + staffIdPrefix + "_" + w);
            }
        }

        // --- Constraints ---
        // 1. One shift per admin staff per day
        for (int a = 0; a < numStaff; a++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(1.0, 1.0, "adm_oneShift_a" + a + "_d" + d);
                for (int s = 0; s < numLpShifts; s++) {
                    c.setCoefficient(x[a][s][d], 1.0);
                }
            }
        }

        // 2. Link X_asd to TotalActualHours_aw
        for (int a = 0; a < numStaff; a++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "adm_actualHoursCalc_a" + a + "_w" + w);
                c.setCoefficient(totalActualHours[a][w], -1.0);
                for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                    int d = w * 7 + dayOffset;
                    if (d >= numDays) break;
                    for (int s = 0; s < numLpShifts; s++) {
                        ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(s));
                        if (shiftDef != null && !shiftDef.isOffShift()) {
                            c.setCoefficient(x[a][s][d], shiftDef.getLengthInHours());
                        }
                    }
                }
            }
        }

        // 3. TotalActualHours_aw = TotalRegHours_aw + TotalOtHours_aw
        for (int a = 0; a < numStaff; a++) {
            for (int w = 0; w < numWeeks; w++) {
                MPConstraint c = solver.makeConstraint(0.0, 0.0, "adm_hourComposition_a" + a + "_w" + w);
                c.setCoefficient(totalActualHours[a][w], 1.0);
                c.setCoefficient(totalRegHours[a][w], -1.0);
                c.setCoefficient(totalOtHours[a][w], -1.0);
            }
        }

        // 4. Max daily hours (from fullInput.getMaxHoursPerDay())
        for (int a = 0; a < numStaff; a++) {
            for (int d = 0; d < numDays; d++) {
                MPConstraint c = solver.makeConstraint(0, fullInput.getMaxHoursPerDay(), "adm_maxDailyH_a" + a + "_d" + d);
                for (int s = 0; s < numLpShifts; s++) {
                    ShiftDefinition shiftDef = lpShifts.get(lpShiftIds.get(s));
                    if (shiftDef != null && !shiftDef.isOffShift()) {
                        c.setCoefficient(x[a][s][d], shiftDef.getLengthInHours());
                    }
                }
            }
        }

        // 5. Demand Coverage for adminDemands
        for (Demand demand : adminDemands) {
            Role requiredRole = demand.getRequiredRole();
            if (!isAdminRole(requiredRole)) continue;

            int d = demand.getDayIndex();
            String lpShiftId = demand.getLpShiftId();
            int requiredCount = demand.getRequiredCount();
            int s = lpShiftIds.indexOf(lpShiftId);

            if (s == -1 || requiredCount <= 0) continue;

            MPConstraint c = solver.makeConstraint(requiredCount, INFINITY, "adm_demand_" + lpShiftIds.get(s) + "_d" + d);
            for (int a_idx = 0; a_idx < numStaff; a_idx++) {
                // Only count staff of the specific required admin role if you have multiple admin roles.
                // If isAdminRole just checks for any admin role, and demands are specific (e.g. ADMIN_CLERK vs. RECEPTIONIST)
                // you might need: adminStaffList.get(a_idx).getRole() == requiredRole
                if (adminStaffList.get(a_idx).getRole() == requiredRole) { // Ensure matching specific admin role if demand is specific
                    c.setCoefficient(x[a_idx][s][d], 1.0);
                }
            }
        }

        // 6. At least MINIMUM_DAYS_OFF_PER_WEEK_ADMIN days off per week (e.g., 2 days)
        if (sFreeIdx != -1 && numWeeks > 0) {
            for (int a = 0; a < numStaff; a++) {
                for (int w = 0; w < numWeeks; w++) {
                    MPConstraint dayOffC = solver.makeConstraint(MINIMUM_DAYS_OFF_PER_WEEK_ADMIN, 7.0, "adm_weeklyDayOff_a" + a + "_w" + w);
                    for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                        int d = w * 7 + dayOffset;
                        if (d >= numDays) break;
                        dayOffC.setCoefficient(x[a][sFreeIdx][d], 1.0);
                    }
                }
            }
        }

        // --- Objective Function ---
        MPObjective objective = solver.objective();
        for (int a = 0; a < numStaff; a++) {
            StaffMemberInterface admin = adminStaffList.get(a);
            double regularWage = admin.getRegularHourlyWage();
            double overtimeRate = regularWage * admin.getOvertimeMultiplier();

            for (int w = 0; w < numWeeks; w++) {
                objective.setCoefficient(totalRegHours[a][w], regularWage);
                objective.setCoefficient(totalOtHours[a][w], overtimeRate);
            }
        }
        objective.setMinimization();

        // --- Solve ---
        logger.info("AdminStaffScheduler: Starting OR-Tools solver...");
        final MPSolver.ResultStatus resultStatus = solver.solve();

        // --- Process Results ---
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            logger.info("AdminStaffScheduler: Solution found! Status: " + resultStatus + ", Objective: " + objective.value());
            return SolutionExtractor.extract(fullInput, adminStaffList, lpShiftIds, x, totalRegHours, totalOtHours, totalActualHours, objective.value(), true);
        } else {
            String statusMessage = "AdminStaffScheduler: No solution. Status: " + resultStatus;
            logger.warning(statusMessage);
            if (resultStatus == MPSolver.ResultStatus.INFEASIBLE) {
                logger.warning("AdminStaffScheduler: Problem is INFEASIBLE. Review constraints, demands, and available staff.");
            }
            return buildInfeasibleOutput(statusMessage);
        }
    }

    private boolean isAdminRole(Role role) {
        // Define what constitutes an admin role in your system
        // This could be a single role or multiple (e.g., ADMIN_CLERK, RECEPTIONIST, etc.)
        return role == Role.ADMIN_CLERK;
        // If you have more admin roles:
        // return role == Role.ADMIN_CLERK || role == Role.SOME_OTHER_ADMIN_ROLE;
    }
}