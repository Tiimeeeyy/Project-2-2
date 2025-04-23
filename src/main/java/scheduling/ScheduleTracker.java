package scheduling;

import simulation.StaffMember;

import java.time.DayOfWeek;
import java.util.HashMap;

public class ScheduleTracker {
    int weeksTracked;
    HashMap<Integer, HashMap<DayOfWeek, StaffMember.Shift>> dayTracker;

    public ScheduleTracker(HashMap<DayOfWeek, StaffMember.Shift> shiftMap) {
        this.weeksTracked = 16;

    }
}
