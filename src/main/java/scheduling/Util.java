package scheduling;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import simulation.StaffMember;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

/**
 * Utility class providing methods to transform staff lists into efficient data structures
 * for scheduling and optimization tasks.
 */
public class Util {

    /**
     * Groups staff members by their role into a {@link MultiValuedMap}.
     * This allows fast lookup of all staff sharing the same role.
     *
     * @param staffList List of {@link StaffMember} to group.
     * @return A {@link MultiValuedMap} mapping {@link StaffMember.Role} to collections of staff.
     */
    public MultiValuedMap<StaffMember.Role, StaffMember> getStaffByRole(List<StaffMember> staffList) {
        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = new ArrayListValuedHashMap<>();
        for (StaffMember member : staffList) {
            staffByRole.put(member.getRole(), member);
        }
        return staffByRole;
    }

    /**
     * Evaluates how many staff members of a given role are scheduled for a particular day and shift.
     *
     * @param staffByRole {@link MultiValuedMap} grouping staff by their role.
     * @param day         Day of week for which to count.
     * @param shift       {@link StaffMember.Shift} for which to count.
     * @param role        The {@link StaffMember.Role} to filter by.
     * @return A {@link MultiKeyMap} where the key is (role, day, shift) and the value is the count of staff.
     */
    public MultiKeyMap<Object, Integer> getStaffLevels(
        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole,
        DayOfWeek day,
        StaffMember.Shift shift,
        StaffMember.Role role
    ) {
        Collection<StaffMember> staffPerRole = staffByRole.get(role);
        int staffCount = (int) staffPerRole.stream()
            .filter(staffMember -> staffMember.getRole().equals(role))
            .filter(staffMember -> {
                StaffMember.Shift assignedShift = staffMember.getShiftOnDay(day);
                return assignedShift != null && assignedShift.equals(shift);
            })
            .count();

        MultiKeyMap<Object, Integer> staffLevel = new MultiKeyMap<>();
        staffLevel.put(role, day, shift, staffCount);
        return staffLevel;
    }
}
