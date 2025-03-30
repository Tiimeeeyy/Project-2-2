import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.time.DayOfWeek;

public class ScheduleOptimizer {
// Fast lookups by multiple criteria
MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = new ArrayListValuedHashMap<>();
MultiValuedMap<DayOfWeek, StaffMember> availableStaffByDay = new ArrayListValuedHashMap<>();

}

