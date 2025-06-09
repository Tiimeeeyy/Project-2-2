package staff;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.UUID;

@Getter
public class AdminClerk implements StaffMemberInterface {

    private final UUID id;
    private final Role role;

    @Setter
    private String name;

    @Setter
    private double regularHourlyWage;

    @Setter
    private double overtimeMultiplier;

    private final HashMap<DayOfWeek, Shift> schedule;

    public AdminClerk(UUID id, String name, double regularHourlyWage, double overtimeMultiplier) {
        this.id = id;
        this.name = name;
        this.role = Role.ADMIN_CLERK; // Role is fixed for this class
        this.regularHourlyWage = regularHourlyWage;
        this.overtimeMultiplier = overtimeMultiplier;
        this.schedule = new HashMap<>(7 * 4);
    }

    public AdminClerk(String name, double regularHourlyWage, double overtimeMultiplier) {
        this(UUID.randomUUID(), name, regularHourlyWage, overtimeMultiplier);
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
