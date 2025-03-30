import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

/**
 * Utility class to transform the basic instance of a staff list into multiple different Data structures,
 * to ensure computational efficiency when running optimization algorithms.
 */
public class Util {

    /**
     * Transforms a List object if staff members into a Multivalued HashMap. This transformation ensures fast lookups of
     * groups of Staff members which have the same role, which can be done by utilizing the role.
     * @param staffList The staff list object to be transformed.
     * @return A {@link MultiValuedMap} containing all staff members of the original list, grouped by role.
     */
    public MultiValuedMap<StaffMember.Role, StaffMember> getStaffByRole(List<StaffMember> staffList) {
        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = new ArrayListValuedHashMap<>();
        for (StaffMember member : staffList) {
            staffByRole.put(member.getRole(), member);
        }
        return staffByRole;
    }

    /**
     * Evaluates the staff level at a given shift, time and for a given role.
     * @param staffByRole The grouped Map containing staff members grouped by role (see {@link #getStaffByRole(List)}
     * @param day The day for which we want to evaluate.
     * @param shift The shift we want to evaluate.
     * @param role The role of Staff we want to evaluate.
     * @return A {@link MultiKeyMap} with the input parameters as key and the staff count as the value.
     */
    public MultiKeyMap<Object, Integer> getStaffLevels(MultiValuedMap<StaffMember.Role, StaffMember> staffByRole, DayOfWeek day, StaffMember.Shift shift, StaffMember.Role role) {
        Collection<StaffMember> staffPerRole = staffByRole.get(role);
        int staffCount = (int) staffPerRole.stream()
                .filter(staffMember -> staffMember.getRole().equals(role))
                .filter(staffMember -> staffMember.getShiftOnDay(day).equals(shift))
                .count();
        MultiKeyMap<Object, Integer> staffLevel = new MultiKeyMap<>();
        staffLevel.put(role, day, shift, staffCount);
        return staffLevel;
    }
}
