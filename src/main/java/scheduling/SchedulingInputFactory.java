package scheduling;

import simulation.Config;
import staff.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A factory class responsible for creating OptimizationInput objects.
 * This class centralizes the logic for generating staff members and calculating demand
 * based on the simulation configuration, providing a clean interface for the DES and schedulers.
 */
public final class SchedulingInputFactory {

    /**
     * Creates and configures a complete OptimizationInput object for the schedulers.
     *
     * @param config   The simulation Config object containing all parameters.
     * @param duration The total duration of the simulation period.
     * @return A fully populated OptimizationInput object.
     */
    public static OptimizationInput createInput(Config config, Duration duration) {
        int numDays = (int) Math.ceil((double) duration.toHours() / 24);
        int numWeeks = (int) Math.ceil((double) duration.toHours() / 168);

        List<StaffMemberInterface> staffMembers = generateStaff(config);
        List<Demand> demands = generateDemands(config, numDays);
        Map<String, ShiftDefinition> lpShifts = defineLpShifts();

        return OptimizationInput.builder()
                .staffMembers(staffMembers)
                .demands(demands)
                .lpShifts(lpShifts)
                .numDaysInPeriod(numDays)
                .numWeeksInPeriod(numWeeks)
                .maxHoursPerDay(config.getMaxHoursPerDay())
                .maxRegularHoursPerWeek(config.getMaxRegularHoursPerWeek())
                .maxTotalHoursPerWeek(config.getMaxTotalHoursPerWeek())
                .build();
    }

    /**
     * Defines the map of available shifts for the linear programming model,
     * accounting for all shifts in the Shift enum.
     *
     * @return A map of LP shift IDs to their ShiftDefinition.
     */
    private static Map<String, ShiftDefinition> defineLpShifts() {
        return new HashMap<>() {{
            // 8-Hour Shifts
            put("d8", new ShiftDefinition("d8", Shift.DAY_8H));
            put("e8", new ShiftDefinition("e8", Shift.EVENING_8H));
            put("n8", new ShiftDefinition("n8", Shift.NIGHT_8H));
            // 10-Hour Shifts
            put("d10", new ShiftDefinition("d10", Shift.DAY_10H));
            put("e10", new ShiftDefinition("e10", Shift.EVENING_10H));
            put("n10", new ShiftDefinition("n10", Shift.NIGHT_10H));
            // 12-Hour Shifts
            put("d12", new ShiftDefinition("d12", Shift.DAY_12H));
            put("n12", new ShiftDefinition("n12", Shift.NIGHT_12H));
            // Special Shifts
            put("on_call", new ShiftDefinition("on_call", Shift.ON_CALL));
            put("off", new ShiftDefinition("off", Shift.FREE));
        }};
    }

    /**
     * Generates the full list of staff members based on the configuration.
     *
     * @param config The simulation configuration.
     * @return A list of all staff members.
     */
    private static List<StaffMemberInterface> generateStaff(Config config) {
        List<StaffMemberInterface> staff = new ArrayList<>();
        for (Map.Entry<String, Integer> roleEntry : config.getStaffCounts().entrySet()) {
            Role role = Role.valueOf(roleEntry.getKey());
            for (int i = 0; i < roleEntry.getValue(); i++) {
                UUID id = UUID.randomUUID();
                String name = role.name() + "_" + i;
                double wage = config.getHourlyWages().get(roleEntry.getKey());
                double overtimeMultiplier = config.getOvertimeMultiplier();

                if (Role.isNurseRole(role)) {
                    staff.add(new Nurse(id, name, role, wage, overtimeMultiplier));
                } else if (Role.isResidentRole(role)) {
                    staff.add(new ResidentPhysician(id, name, role, wage, overtimeMultiplier, 1));
                } else if (Role.isPhysicianRole(role)) {
                    staff.add(new Physician(id, name, role, wage, overtimeMultiplier));
                } else if (Role.isAdminClerkRole(role)) {
                    staff.add(new AdminClerk(id, name, wage, overtimeMultiplier));
                }
            }
        }
        return staff;
    }

    private static List<Demand> generateDemands(Config config, int numDays) {
        List<Demand> demands = new ArrayList<>();
        // Base demands on the three core 8-hour periods from the config estimates
        Map<Role, Integer> dayRequirements = OregonStaffingRules.getStaffRequirements(
                config.getEstTraumaPatientsDay(),
                config.getEstNonTraumaPatientsDay(),
                config.getCNARatio(),
                config.getLPNRatio());
        Map<Role, Integer> eveningRequirements = OregonStaffingRules.getStaffRequirements(
                config.getEstTraumaPatientsEvening(),
                config.getEstNonTraumaPatientsEvening(),
                config.getCNARatio(),
                config.getLPNRatio());
        Map<Role, Integer> nightRequirements = OregonStaffingRules.getStaffRequirements(
                config.getEstTraumaPatientsNight(),
                config.getEstNonTraumaPatientsNight(),
                config.getCNARatio(),
                config.getLPNRatio());

        for (int dayIndex = 0; dayIndex < numDays; dayIndex++) {
            // Add demand for the 8-hour day shift ("d8")
            for (Map.Entry<Role, Integer> entry : dayRequirements.entrySet()) {
                if (entry.getValue() > 0) demands.add(new Demand(entry.getKey(), dayIndex, "d8", entry.getValue()));
            }
            // Add demand for the 8-hour evening shift ("e8")
            for (Map.Entry<Role, Integer> entry : eveningRequirements.entrySet()) {
                if (entry.getValue() > 0) demands.add(new Demand(entry.getKey(), dayIndex, "e8", entry.getValue()));
            }
            // Add demand for the 8-hour night shift ("n8")
            for (Map.Entry<Role, Integer> entry : nightRequirements.entrySet()) {
                if (entry.getValue() > 0) demands.add(new Demand(entry.getKey(), dayIndex, "n8", entry.getValue()));
            }
        }
        return demands;
    }
}