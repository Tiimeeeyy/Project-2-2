package staff;

import lombok.Getter;


public enum Role {
    // Core nursing roles in Oregon:
    REGISTERED_NURSE("RN"),
    LICENSED_PRACTICAL_NURSE("LPN"),
    CERTIFIED_NURSING_ASSISTANT("CNA"),
    // Advanced nursing roles:
    NURSE_PRACTITIONER("NP"),
    CLINICAL_NURSE_SPECIALIST("CNS"),
    CERTIFIED_REGISTERED_NURSE_ANESTHETIST("CRNA"),
    // Doctor Roles:
    RESIDENT_PHYSICIAN("MD_RESIDENT"),
    ATTENDING_PHYSICIAN("MD_ATTENDING"),
    SURGEON("MD_SURGEON"),
    CARDIOLOGIST("MD_CARDIO"),
    // General Staff role:
    ADMIN_CLERK("AC");

    @Getter
    private final String abbreviation;


    Role(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public boolean isRN() {
        return this == REGISTERED_NURSE || this == NURSE_PRACTITIONER ||
                this == CLINICAL_NURSE_SPECIALIST || this == CERTIFIED_REGISTERED_NURSE_ANESTHETIST;
    }

    public boolean isAPRN() {
        return this == NURSE_PRACTITIONER || this == CLINICAL_NURSE_SPECIALIST ||
                this == CERTIFIED_REGISTERED_NURSE_ANESTHETIST;
    }

    public static boolean isNurseRole(Role roleToCheck) {
        // This helper should ideally be part of the Role enum or a utility class
        return roleToCheck == Role.REGISTERED_NURSE || roleToCheck == Role.LICENSED_PRACTICAL_NURSE || roleToCheck == Role.CERTIFIED_NURSING_ASSISTANT || roleToCheck == Role.NURSE_PRACTITIONER || roleToCheck == Role.CLINICAL_NURSE_SPECIALIST || roleToCheck == Role.CERTIFIED_REGISTERED_NURSE_ANESTHETIST;
    }

    public static boolean isPhysicianRole(Role roleToCheck) {
        return roleToCheck == RESIDENT_PHYSICIAN;
    }

    public static boolean isResidentRole(Role roleToCheck) {
        return roleToCheck == RESIDENT_PHYSICIAN || roleToCheck == CARDIOLOGIST || roleToCheck == SURGEON;
    }

    public static boolean isAdminClerkRole(Role roleToCheck) {
        return roleToCheck == ADMIN_CLERK;
    }

}
