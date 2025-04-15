import lombok.Data;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.UUID;

/**
 * Represents a Staff Member working in the Emergency Room. For efficient usage and computations, we chose a Hashmap as
 * the representation for the Schedule for efficiency.
 */
@Data
public class StaffMember {
    private final UUID id;
    private String name;
    private HashMap<DayOfWeek, Shift> schedule;
    private Role role;

    /**
     * Creates a Staff Member object with given parameters, for normal usage refer to createStaffMember method.
     * @param id The id of the staff member
     * @param name The name of the Staff member
     * @param schedule The (initially empty) schedule of the staff worker
     * @param role The role of the Staff member
     */
    public StaffMember(UUID id, String name, HashMap<DayOfWeek, Shift> schedule, Role role) {
        this.id = id;
        this.name = name;
        this.schedule = schedule;
        this.role = role;
    }

    /**
     * Creates a staff member with random UUID and random name based on UUID. If you wish to use names and UUID as params, refer to class
     * constructor. Additionally, the initial schedule will be assigned empty and with a size of 7 (for each day of the week) for optimal memory use.
     * @param role The role the staff member takes.
     * @return A staff member object.
     */
    public StaffMember createStaffMember(Role role) {
        UUID id = UUID.randomUUID();
        HashMap<DayOfWeek, Shift> schedule = new HashMap<>(7);
        String name = "StaffMember" + Math.abs(id.hashCode() % 10000);
        return new StaffMember(id, name, schedule, role);
    }

    /**
     * Adds / assigns a shift to the given Staff Member. Shifts are characterized by:
     * @param day The day the shift should be added for.
     * @param shiftType The type of shift to be assigned (see @enum Shift)
     */
    public void assignShift(DayOfWeek day, Shift shiftType) {
        if (getShiftOnDay(day) != null) {
            schedule.put(day, shiftType);
        } else {
            throw new InvalidShift("Shift cannot be assigned for day " + day + " . Day is already populated!");
        }

    }

    /**
     * Resets the schedule for simulation purposes. This should be done, once a simulated week passes.
     */
    public void resetSchedule() {
        schedule.clear();
    }

    /**
     * Gets the shift assigned to a staff member on a given day.
     * @param day The day we want to check for.
     * @return The shift that is assigned on that day. Can return null, therefore can also check if a shift is assigned.
     */
    public Shift getShiftOnDay(DayOfWeek day) {
        return schedule.get(day);
    }


    /**
     * Defines the different roles Staff member can take on. Additionally, an accessLevel is assigned.
     */
    public enum Role {
        DOCTOR(1),
        NURSE(2),
        ADMINISTRATIVE(3);

        Role(int accessLevel) {
        }
    }

    /**
     * Defines the types of shifts that exist in the Simulation.
     * A shift is assumed to be 7-8 hours (36-40 hr work week) long, and a double shift
     * is defined as the maximum shift length in NL, 12 hours.
     */
    public enum Shift {
        DAY,
        NIGHT,
        FREE,
        DOUBLE,
    }

    public Role getRole() {
        return role;
    }
}
