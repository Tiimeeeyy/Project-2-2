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

}
