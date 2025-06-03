package staff;

/**
 * Utility class to provide helper methods related to Oregon's specific
 * hospital staffing laws and common practices, with a focus on the Emergency Department (ED).
 */
public class OregonStaffingRules {

    // --- ED RN Staffing Ratios (Based on Oregon HB 2697) ---

    /**
     * Calculates the minimum number of Registered Nurses (RNs) required for an
     * Emergency Department (ED) based on Oregon's mandated ratios.
     * - 1 RN per 1 trauma patient (level 1 or 2 acuity).
     * - An average of 1 RN per 4 non-trauma/lower acuity patients over a 12-hour shift
     * (with a maximum of 1 RN to 5 such patients at any one time).
     * For planning minimums, we use the 1:4 for non-trauma.
     *
     * @param traumaPatientsCount    Number of high-acuity trauma patients currently requiring 1:1 RN care.
     * @param nonTraumaPatientsCount Number of other non-trauma/lower-acuity ED patients.
     * @return Minimum number of RNs legally required for the ED.
     */
    public static int getMinRNsForED(int traumaPatientsCount, int nonTraumaPatientsCount) {
        if (traumaPatientsCount < 0) traumaPatientsCount = 0;
        if (nonTraumaPatientsCount < 0) nonTraumaPatientsCount = 0;

        int rnForTrauma = traumaPatientsCount; // Directly 1:1 for trauma patients
        int rnForNonTrauma = (int) Math.ceil(nonTraumaPatientsCount / 4.0); // Using the 1:4 average for planning

        return rnForTrauma + rnForNonTrauma;
    }

    // --- ED LPN Staffing (Policy-Driven in ED) ---

    /**
     * Calculates the estimated number of Licensed Practical Nurses (LPNs) for an ED.
     * Note: Specific LPN-to-patient ratios are not legislated for EDs like RN ratios.
     * This would be based on the hospital's staffing plan and LPN scope of practice in the ED.
     *
     * @param edPatientCount         Total patient count in the ED.
     * @param hospitalPolicyLPNRatio An example policy ratio, e.g., 1 LPN per 10-15 patients if LPNs are used for certain tasks.
     *                               Set to 0 or a very high number if LPNs are not typically part of the ED core staffing model.
     * @return Estimated number of LPNs based on policy.
     */
    public static int getPolicyBasedLPNsForED(int edPatientCount, double hospitalPolicyLPNRatio) {
        if (edPatientCount <= 0 || hospitalPolicyLPNRatio <= 0) return 0;
        return (int) Math.ceil(edPatientCount / hospitalPolicyLPNRatio); // Example: 1 LPN per 15 patients
    }

    // --- ED CNA/Tech Staffing (Policy-Driven in ED) ---

    /**
     * Calculates the estimated number of Certified Nursing Assistants (CNAs) or ED Technicians for an ED.
     * Note: Specific CNA/Tech-to-patient ratios are not legislated for EDs like RN ratios.
     * This would be based on the hospital's staffing plan.
     *
     * @param edPatientCount         Total patient count in the ED.
     * @param hospitalPolicyCNARatio Example policy: 1 CNA/Tech per X patients, or based on number of RNs.
     * @return Estimated number of CNAs/Techs based on policy.
     */
    public static int getPolicyBasedCNAsOrTechsForED(int edPatientCount, double hospitalPolicyCNARatio) {
        if (edPatientCount <= 0 || hospitalPolicyCNARatio <= 0) return 0;
        // Example: 1 CNA/Tech per 10 patients, or 1 per 2 RNs etc.
        return (int) Math.ceil(edPatientCount / hospitalPolicyCNARatio);
    }

    // --- ED Physician Staffing (Policy-Driven) ---

    /**
     * Calculates estimated physician needs for an ED.
     * Physician staffing is typically based on ensuring adequate coverage for expected patient volume,
     * acuity, number of bays, and often involves multiple physicians (e.g., attending, residents).
     * This is highly dependent on the specific hospital's ED operational model.
     *
     * @param edPatientCount Current or anticipated patient count.
     * @param physicianRole  The specific role (e.g., Role.ATTENDING_PHYSICIAN, Role.RESIDENT_PHYSICIAN).
     * @return Estimated number of physicians of that role.
     */
    public static int getPolicyBasedPhysiciansForED(int edPatientCount, Role physicianRole) {
        if (edPatientCount <= 0) return 0;
        // This is purely illustrative and would need to come from actual ED staffing models.
        // For example, ensuring a minimum number of attending physicians + residents based on zones/pods.
        if (physicianRole == Role.ATTENDING_PHYSICIAN) {
            // Example: 1 attending for up to 15-20 patients, or 1 per zone, plus dedicated trauma attending if busy.
            // Minimum 1-2 attending physicians for ED coverage usually.
            return Math.max(1, (int) Math.ceil(edPatientCount / 20.0));
        } else if (physicianRole == Role.RESIDENT_PHYSICIAN) {
            // Residents often supplement attending physicians, ratio might be 1 resident per 10-15 patients.
            return (int) Math.ceil(edPatientCount / 15.0);
        }
        return 0; // Default for other physician roles not specified in this example
    }

    // --- ED Administrative Staff (Policy-Driven) ---

    /**
     * Calculates estimated administrative staff (e.g., unit clerks, registration staff) for an ED.
     * This depends on patient flow, number of registration points, administrative tasks, etc.
     *
     * @param edPatientArrivalsOrCensus Anticipated patient arrivals per shift or current census.
     * @param adminRole                 The specific administrative role.
     * @return Estimated number of administrative staff.
     */
    public static int getPolicyBasedAdminStaffForED(int edPatientArrivalsOrCensus, Role adminRole) {
        if (edPatientArrivalsOrCensus <= 0) return 0;
        // Illustrative examples:
        if (adminRole == Role.ADMIN_CLERK) { // e.g., Unit Secretary or HUC
            // Might be 1-2 per main ED area per shift regardless of exact patient number, up to a point.
            return Math.max(1, (int) Math.ceil(edPatientArrivalsOrCensus / 50.0)); // 1 per 50 arrivals/census as placeholder
        }
        // Add other admin roles like registration clerks if needed.
        return 0;
    }
}