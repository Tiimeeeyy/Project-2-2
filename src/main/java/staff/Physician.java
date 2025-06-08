package staff;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.UUID;

@Getter
public class Physician implements StaffMemberInterface {

    private final UUID id;
    private final Role role;

    @Setter
    private String name;

    @Setter
    private double regularHourlyWage;

    @Setter
    private double overtimeMultiplier;

    private final HashMap<DayOfWeek, Shift> schedule;

    public Physician(UUID id, String name, Role physicianRole, double regularHourlyWage, double overtimeMultiplier) {
        if (physicianRole == null || !Role.isPhysicianRole(physicianRole)) {
            throw new IllegalArgumentException("The provided role '" + physicianRole + "' is not a valid physician role for a Physician object.");
        }
        this.id = id;
        this.name = name;
        this.role = physicianRole;
        this.regularHourlyWage = regularHourlyWage;
        this.overtimeMultiplier = overtimeMultiplier;
        this.schedule = new HashMap<>(7 * 4);
    }

    public Physician(String name, Role physicianRole, double regularHourlyWage, double overtimeMultiplier) {
        this(UUID.randomUUID(), name, physicianRole, regularHourlyWage, overtimeMultiplier);
    }

    @Override
    public HashMap<DayOfWeek, Shift> getSchedule() {
        return new HashMap<>(this.schedule);
    }

    @Override
    public void assignShift(DayOfWeek day, Shift shiftType) {
        if (day == null) {
            throw new IllegalArgumentException("Day cannot be null when assigning a shift.");
        }
        this.schedule.put(day, shiftType);
    }

    @Override
    public void resetSchedule() {
        this.schedule.clear();
    }

    @Override
    public Shift getShiftOnDay(DayOfWeek day) {
        return this.schedule.get(day);
    }
}
