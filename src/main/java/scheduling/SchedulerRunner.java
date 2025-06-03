package scheduling; // Or your chosen package

import staff.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.DayOfWeek;


public class SchedulerRunner {

    public static void main(String[] args) {
        System.out.println("Setting up scheduler input for a LIKELY FEASIBLE scenario...");

        // 1. Define Staff Members (Nurses for NurseScheduler)
        List<StaffMemberInterface> staffList = new ArrayList<>();
        Nurse nurse1 = new Nurse("Alice (RN)", Role.REGISTERED_NURSE, 30.0, 1.5);
        Nurse nurse2 = new Nurse("Bob (RN)", Role.REGISTERED_NURSE, 32.0, 1.5);
        staffList.add(nurse1);
        staffList.add(nurse2);

        System.out.println("Created " + staffList.size() + " staff members.");

        // 2. Define LP Shifts (ShiftDefinitions)
        Map<String, ShiftDefinition> lpShifts = new HashMap<>();
        lpShifts.put("ED_D8", new ShiftDefinition("ED_D8", Shift.DAY_8H));     // 8-hour day shift
        // We only define demand for ED_D8, but other shifts (like F) need to be options for the LP.
        // The LP will assign "F" if no work shift is chosen for a nurse on a day.
        // Let's add an evening and night shift as potential assignments even if no demand,
        // plus the crucial "F" (Free) shift.
        lpShifts.put("ED_E8", new ShiftDefinition("ED_E8", Shift.EVENING_8H));
        lpShifts.put("ED_N8", new ShiftDefinition("ED_N8", Shift.NIGHT_8H));
        lpShifts.put("F", new ShiftDefinition("F", Shift.FREE));            // Free/Off

        System.out.println("Defined " + lpShifts.size() + " LP shift types.");

        // 3. Define Demands (Reduced demand for higher feasibility)
        List<Demand> demands = new ArrayList<>();
        int planningDays = 7; // 1 week
        int planningWeeks = 1;

        // REDUCED ED patient load for demand calculation to require only 1 RN
        int typicalTraumaPatientsPerShift = 0;    // Reduced
        int typicalNonTraumaPatientsPerShift = 2; // Reduced: ceil(2/4) = 1. Total RNs = 0 + 1 = 1

        int requiredRNsPerShift = OregonStaffingRules.getMinRNsForED(
                typicalTraumaPatientsPerShift,
                typicalNonTraumaPatientsPerShift
        ); // This should now be 1 RN
        System.out.println("Calculated ED RN demand per shift: " + requiredRNsPerShift);

        // Only create demand for the Day shift ("ED_D8") for the first 5 days
        for (int day = 0; day < 5; day++) { // Monday to Friday demand
            demands.add(new Demand(Role.REGISTERED_NURSE, day, "ED_D8", requiredRNsPerShift));
        }
        // No explicit demand for Saturday (day 5) and Sunday (day 6) for ED_D8
        // No explicit demand for ED_E8 or ED_N8 on any day.
        // The LP should assign nurses to "F" (Free) on days/shifts with no demand or when not working.

        System.out.println("Created " + demands.size() + " demand entries (should be 5 demands for 1 RN each).");

        // 4. Create OptimizationInput
        OptimizationInput input = OptimizationInput.builder()
                .staffMembers(staffList)
                .lpShifts(lpShifts)
                .demands(demands)
                .numDaysInPeriod(planningDays)
                .numWeeksInPeriod(planningWeeks)
                .maxRegularHoursPerWeek(40)
                .maxTotalHoursPerWeek(48)
                .maxHoursPerDay(12)     // An 8-hour shift is well within this
                .build();
        System.out.println("OptimizationInput created.");

        // 5. Instantiate and Run NurseScheduler
        NurseScheduler nurseScheduler = new NurseScheduler();
        System.out.println("Running NurseScheduler...");
        OptimizedScheduleOutput output = nurseScheduler.optimizeNurseSchedule(input);

        // 6. Print Results
        System.out.println("\n--- Optimization Results (Feasible Scenario Attempt) ---");
        System.out.println("Feasible Solution Found: " + output.isFeasible());
        if (output.isFeasible()) {
            System.out.println("Total Schedule Cost: $" + String.format("%.2f", output.getTotalCost()));

            System.out.println("\nAssignments (lpShiftId per day):");
            for (StaffMemberInterface staff : staffList) {
                System.out.println("  Staff: " + staff.getName() + " (ID: " + staff.getId() + ")");
                Map<Integer, String> staffAssignments = output.getAssignments().get(staff.getId());
                if (staffAssignments != null && !staffAssignments.isEmpty()) {
                    for (int day = 0; day < planningDays; day++) {
                        String assignedShift = staffAssignments.getOrDefault(day, "NOT_ASSIGNED_IN_OUTPUT"); // Should be "F" if not working
                        System.out.println("    Day " + day + ": " + assignedShift);
                    }
                } else {
                    // This case should ideally not happen if "F" is assigned.
                    // If a staff member has no assignments at all in the output map, it means they were assigned "F" implicitly by the LP
                    // and the extractSolution method might not add an entry if only non-"F" shifts are explicitly stored.
                    // However, our "one shift per day" constraint X_nsd = 1 means an "F" shift should be explicitly assigned if it's an option.
                    System.out.println("    No explicit work assignments found. Should be on 'F' (Free) shifts.");
                }

                Map<Integer, Double> weeklyRegH = output.getWeeklyRegularHours().get(staff.getId());
                Map<Integer, Double> weeklyOtH = output.getWeeklyOvertimeHours().get(staff.getId());
                Map<Integer, Double> weeklyTotalH = output.getWeeklyTotalActualHours().get(staff.getId());

                if (weeklyRegH != null) {
                    for (int week = 0; week < planningWeeks; week++) {
                        System.out.println("    Week " + week + ": " +
                                "Reg Hours = " + String.format("%.1f", weeklyRegH.getOrDefault(week, 0.0)) +
                                ", OT Hours = " + String.format("%.1f", weeklyOtH.getOrDefault(week, 0.0)) +
                                ", Total Hours = " + String.format("%.1f", weeklyTotalH.getOrDefault(week, 0.0)));
                    }
                }
                System.out.println("    -----");
            }

            // Example: Detailed weekly schedule for Alice
            System.out.println("\nFormatted Weekly Schedule (Week 0) for " + nurse1.getName() + ":");
            if (output.getAssignments().containsKey(nurse1.getId())) {
                HashMap<DayOfWeek, Shift> nurse1Week0Schedule = output.getStaffMemberWeeklySchedule(
                        nurse1.getId(), 0, input.getNumDaysInPeriod(), input.getLpShifts()
                );
                // Iterate based on standard DayOfWeek order for clarity
                for (DayOfWeek day = DayOfWeek.MONDAY; day.getValue() <= DayOfWeek.SUNDAY.getValue(); day = day.plus(1)) {
                    Shift assignedConcreteShift = nurse1Week0Schedule.get(day);
                    System.out.println("    " + day + ": " + (assignedConcreteShift != null ? assignedConcreteShift.getDescription() : "UNASSIGNED (should be Free)"));
                    if (day == DayOfWeek.SUNDAY) break; // Avoid infinite loop with DayOfWeek.plus(1)
                }
            }


        } else {
            System.out.println("Could not find a feasible schedule. Review inputs, demands, staff availability, and constraints.");
            System.out.println("Solver Status (from logs before this): Ensure it wasn't a solver error or timeout.");
        }
        System.out.println("\nScheduler run finished.");
    }
}