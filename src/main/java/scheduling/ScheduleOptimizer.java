package scheduling;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import simulation.StaffMember;

import java.time.DayOfWeek;

public class ScheduleOptimizer {
// Fast lookups by multiple criteria
MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = new ArrayListValuedHashMap<>();
MultiValuedMap<DayOfWeek, StaffMember> availableStaffByDay = new ArrayListValuedHashMap<>();

}

