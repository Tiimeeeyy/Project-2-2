package staff;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.UUID;

public interface StaffMemberInterface {
    UUID getId();

    String getName();

    void setName(String name); // Setter for name

    Role getRole();

    double getRegularHourlyWage();
    // Optional: void setRegularHourlyWage(double wage); // If wage can change post-construction

    double getOvertimeMultiplier();
    // Optional: void setOvertimeMultiplier(double multiplier); // If multiplier can change

    HashMap<DayOfWeek, Shift> getSchedule(); // Contract: should return a representation of the schedule

    void assignShift(DayOfWeek day, Shift shiftType);

    void resetSchedule();

    Shift getShiftOnDay(DayOfWeek day);
}