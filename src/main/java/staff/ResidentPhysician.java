package staff;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.UUID;

@Getter
public class ResidentPhysician implements StaffMemberInterface {

    private final UUID id;
    private final Role role;

    @Setter
    private String name;

    @Setter
    private double regularHourlyWage;

    @Setter
    private double overtimeMultiplier;

    @Setter
    private int residencyYear;

    private final HashMap<DayOfWeek, Shift> schedule;

    public ResidentPhysician(UUID id, String name, Role residentRole, double regularHourlyWage,
                             double overtimeMultiplier, int residencyYear) {
        if (residentRole == null || !Role.isResidentRole(residentRole)) {
            throw new IllegalArgumentException("The provided role '" + residentRole +
                                              "' is not a valid resident role for a ResidentPhysician object.");
        }
        this.id = id;
        this.name = name;
        this.role = residentRole;
        this.regularHourlyWage = regularHourlyWage;
        this.overtimeMultiplier = overtimeMultiplier;
        this.residencyYear = residencyYear;
        this.schedule = new HashMap<>(7 * 4);
    }
    public ResidentPhysician(UUID id, String name, Role residentRole, double regularHourlyWage,
                             double overtimeMultiplier) {
        if (residentRole == null || !Role.isResidentRole(residentRole)) {
            throw new IllegalArgumentException("The provided role '" + residentRole +
                    "' is not a valid resident role for a ResidentPhysician object.");
        }
        this.id = id;
        this.name = name;
        this.role = residentRole;
        this.regularHourlyWage = regularHourlyWage;
        this.overtimeMultiplier = overtimeMultiplier;
        this.schedule = new HashMap<>(7 * 4);
    }


    public ResidentPhysician(String name, Role residentRole, double regularHourlyWage,
                             double overtimeMultiplier, int residencyYear) {
        this(UUID.randomUUID(), name, residentRole, regularHourlyWage, overtimeMultiplier, residencyYear);
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
