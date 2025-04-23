import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduling.Util;
import simulation.StaffMember;

import java.time.DayOfWeek;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UtilTest {
    private Util util;
    private List<StaffMember> staffList;

    @BeforeEach
    void setUp() {
        util = new Util();
        staffList = new ArrayList<>();

        // Create sample staff members
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        // Create schedules
        HashMap<DayOfWeek, StaffMember.Shift> schedule1 = new HashMap<>();
        schedule1.put(DayOfWeek.MONDAY, StaffMember.Shift.DAY);
        schedule1.put(DayOfWeek.TUESDAY, StaffMember.Shift.EVENING);

        HashMap<DayOfWeek, StaffMember.Shift> schedule2 = new HashMap<>();
        schedule2.put(DayOfWeek.MONDAY, StaffMember.Shift.DAY);
        schedule2.put(DayOfWeek.WEDNESDAY, StaffMember.Shift.DAY);

        HashMap<DayOfWeek, StaffMember.Shift> schedule3 = new HashMap<>();
        schedule3.put(DayOfWeek.MONDAY, StaffMember.Shift.EVENING);

        // Create staff members with different roles
        StaffMember doctor = new StaffMember(id1, "Dr. Smith", schedule1, StaffMember.Role.DOCTOR);
        StaffMember nurse = new StaffMember(id2, "Nurse Johnson", schedule2, StaffMember.Role.NURSE);
        StaffMember admin = new StaffMember(id3, "Admin Brown", schedule3, StaffMember.Role.ADMINISTRATIVE);

        staffList.add(doctor);
        staffList.add(nurse);
        staffList.add(admin);
    }

    @Test
    void testGetStaffByRole() {
        // Transform list to MultiValuedMap
        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = util.getStaffByRole(staffList);

        // Check that all roles are present
        assertTrue(staffByRole.containsKey(StaffMember.Role.DOCTOR));
        assertTrue(staffByRole.containsKey(StaffMember.Role.NURSE));
        assertTrue(staffByRole.containsKey(StaffMember.Role.ADMINISTRATIVE));

        // Check that counts are correct
        assertEquals(1, staffByRole.get(StaffMember.Role.DOCTOR).size());
        assertEquals(1, staffByRole.get(StaffMember.Role.NURSE).size());
        assertEquals(1, staffByRole.get(StaffMember.Role.ADMINISTRATIVE).size());

        // Check that total staff count is preserved
        int totalStaffCount = staffByRole.values().size();
        assertEquals(staffList.size(), totalStaffCount);

        // Check that we can retrieve the correct staff member for a role
        Collection<StaffMember> doctors = staffByRole.get(StaffMember.Role.DOCTOR);
        StaffMember doctor = doctors.iterator().next();
        assertEquals("Dr. Smith", doctor.getName());
        assertEquals(StaffMember.Role.DOCTOR, doctor.getRole());
    }

    @Test
    void testGetStaffByRoleWithEmptyList() {
        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = util.getStaffByRole(new ArrayList<>());
        assertTrue(staffByRole.isEmpty());
    }

    @Test
    void testGetStaffLevels() {
        // Transform list to MultiValuedMap
        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = util.getStaffByRole(staffList);

        // Test staff levels for MONDAY DAY shift with DOCTOR role
        MultiKeyMap<Object, Integer> mondayDayDoctors = util.getStaffLevels(
            staffByRole, DayOfWeek.MONDAY, StaffMember.Shift.DAY, StaffMember.Role.DOCTOR
        );

        // Check that the count is correct
        Integer doctorCount = mondayDayDoctors.get(
            StaffMember.Role.DOCTOR, DayOfWeek.MONDAY, StaffMember.Shift.DAY
        );
        assertEquals(1, doctorCount);

        // Test staff levels for MONDAY EVENING shift with ADMIN role
        MultiKeyMap<Object, Integer> mondayNightAdmin = util.getStaffLevels(
            staffByRole, DayOfWeek.MONDAY, StaffMember.Shift.EVENING, StaffMember.Role.ADMINISTRATIVE
        );

        // Check that the count is correct
        Integer adminCount = mondayNightAdmin.get(
            StaffMember.Role.ADMINISTRATIVE, DayOfWeek.MONDAY, StaffMember.Shift.EVENING
        );
        assertEquals(1, adminCount);

        // Test staff levels for a shift with no assigned staff
        MultiKeyMap<Object, Integer> tuesdayDayNurse = util.getStaffLevels(
            staffByRole, DayOfWeek.TUESDAY, StaffMember.Shift.DAY, StaffMember.Role.NURSE
        );

        // Check that the count is zero
        Integer nurseCount = tuesdayDayNurse.get(
            StaffMember.Role.NURSE, DayOfWeek.TUESDAY, StaffMember.Shift.DAY
        );
        assertEquals(0, nurseCount);
    }

    @Test
    void testGetStaffLevelsWithNoStaffOfRole() {
        // Create a staffByRole map with no nurses
        List<StaffMember> doctorsOnly = staffList.stream()
            .filter(staff -> staff.getRole() == StaffMember.Role.DOCTOR)
            .toList();

        MultiValuedMap<StaffMember.Role, StaffMember> staffByRole = util.getStaffByRole(doctorsOnly);

        // Test staff levels for a non-existent role
        MultiKeyMap<Object, Integer> mondayDayNurse = util.getStaffLevels(
            staffByRole, DayOfWeek.MONDAY, StaffMember.Shift.DAY, StaffMember.Role.NURSE
        );

        // Check that the count is zero
        Integer nurseCount = mondayDayNurse.get(
            StaffMember.Role.NURSE, DayOfWeek.MONDAY, StaffMember.Shift.DAY
        );
        assertEquals(0, nurseCount);
    }
}
