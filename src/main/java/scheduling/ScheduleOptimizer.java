package scheduling;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import simulation.StaffMember;

import java.time.DayOfWeek;

/**
 * Optimizer that maintains mappings for staff availability by role and day.
 * Facilitates fast lookups when assigning or querying schedules.
 */
public class ScheduleOptimizer {

    /**
     * Maps each staff role to a collection of {@link StaffMember} objects.
     */
    private final MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = new ArrayListValuedHashMap<>();

    /**
     * Maps each day of week to a collection of {@link StaffMember} objects available that day.
     */
    private final MultiValuedMap<DayOfWeek, StaffMember> availableStaffByDay = new ArrayListValuedHashMap<>();

    /**
     * Retrieves the internal map of staff grouped by their role.
     *
     * @return A {@link MultiValuedMap} of role → staff members.
     */
    public MultiValuedMap<StaffMember.Role, StaffMember> getStaffByRoleMap() {
        return staffByRole;
    }

    /**
     * Retrieves the internal map of staff grouped by day of week.
     *
     * @return A {@link MultiValuedMap} of day → staff members available.
     */
    public MultiValuedMap<DayOfWeek, StaffMember> getAvailableStaffByDayMap() {
        return availableStaffByDay;
    }
}
