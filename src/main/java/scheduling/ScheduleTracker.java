package scheduling;

import simulation.StaffMember;

import java.time.DayOfWeek;
import java.util.HashMap;

/**
 * Tracks historical shift assignments over a number of weeks.
 * Useful for analyzing patterns or enforcing scheduling constraints.
 */
public class ScheduleTracker {

    /**
     * Number of weeks for which tracking data is retained.
     */
    private final int weeksTracked;

    /**
     * Maps from week index to a map of day-of-week → {@link StaffMember.Shift}.
     */
    private final HashMap<Integer, HashMap<DayOfWeek, StaffMember.Shift>> dayTracker;

    /**
     * Constructs a ScheduleTracker that will track a fixed number of weeks.
     * Initializes an empty dayTracker map.
     *
     * @param shiftMap Unused in current implementation (reserved for future extension).
     */
    public ScheduleTracker(HashMap<DayOfWeek, StaffMember.Shift> shiftMap) {
        this.weeksTracked = 16;
        this.dayTracker = new HashMap<>();
    }

    /**
     * Returns the number of weeks being tracked.
     *
     * @return The number of weeks tracked.
     */
    public int getWeeksTracked() {
        return weeksTracked;
    }

    /**
     * Returns the internal map tracking shift assignments.
     *
     * @return A {@link HashMap} mapping week indices to day-of-week → shift assignments.
     */
    public HashMap<Integer, HashMap<DayOfWeek, StaffMember.Shift>> getDayTracker() {
        return dayTracker;
    }
}
