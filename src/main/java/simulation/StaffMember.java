package simulation;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.UUID;

/**
 * Represents a staff member in the emergency room, including schedule and role.
 */
@Getter
@Setter
public class StaffMember {
    private final UUID id;
    private String name;
    private HashMap<DayOfWeek, Shift> schedule;
    private Role role;

    /**
     * Constructs a StaffMember with specified parameters.
     *
     * @param id       Unique identifier of the staff member.
     * @param name     Name of the staff member.
     * @param schedule Schedule map for the staff member.
     * @param role     Role of the staff member.
     */
    public StaffMember(UUID id, String name, HashMap<DayOfWeek, Shift> schedule, Role role) {
        this.id = id;
        this.name = name;
        this.schedule = schedule;
        this.role = role;
    }

    /**
     * Creates a new staff member with a random UUID and name, with an empty schedule.
     *
     * @param role Role of the new staff member.
     * @return Newly created StaffMember.
     */
    public StaffMember createStaffMember(Role role) {
        UUID id = UUID.randomUUID();
        HashMap<DayOfWeek, Shift> schedule = new HashMap<>(7);
        String name = "StaffMember" + Math.abs(id.hashCode() % 10000);
        return new StaffMember(id, name, schedule, role);
    }

    /**
     * Assigns a shift to this staff member on a given day.
     *
     * @param day       Day of week to assign the shift.
     * @param shiftType Type of shift to assign.
     * @throws InvalidShift If a shift is already assigned for that day.
     */
    public void assignShift(DayOfWeek day, Shift shiftType) {
        if (schedule.get(day) == null) {
            schedule.put(day, shiftType);
        } else {
            throw new InvalidShift("Shift cannot be assigned for day " + day + " . Day is already populated!");
        }
    }

    /**
     * Clears the schedule for the staff member (e.g., at the end of a simulation week).
     */
    public void resetSchedule() {
        schedule.clear();
    }

    /**
     * Retrieves the shift assigned to this staff member on a given day.
     *
     * @param day Day of week to look up.
     * @return Assigned shift for that day, or {@code null} if none.
     */
    public Shift getShiftOnDay(DayOfWeek day) {
        return schedule.get(day);
    }

    /**
     * Defines possible roles for staff members, each with an access level.
     */
    public enum Role {
        DOCTOR(1),
        NURSE(2),
        ADMINISTRATIVE(3);

        private final int accessLevel;

        Role(int accessLevel) {
            this.accessLevel = accessLevel;
        }
    }

    /**
     * Defines the types of shifts available for assignment.
     */
    public enum Shift {
        DAY,         // 8 Hours: 07:00 - 15:30 (30-min break included)
        EVENING,     // 8 Hours: 15:30 - 00:00 (30-min break included)
        NIGHT,       // 7 Hours: 00:00 - 07:00 (30-min break included)
        FREE,        // No work
        DOUBLE_EARLY,// 12 Hours: 07:00 - 19:00 (45-min break included)
        DOUBLE_LATE, // 12 Hours: 12:00 - 00:00 (45-min break included)
        ON_CALL      // On-call duty
    }
}
