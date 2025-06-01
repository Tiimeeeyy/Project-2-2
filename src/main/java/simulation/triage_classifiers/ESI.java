package simulation.triage_classifiers;

import simulation.Patient.TriageLevel;

/**
 * Emergency Severity Index (ESI) classifier implementation.
 * Maps diagnosis codes to {@link TriageLevel} according to ESI guidelines.
 */
public class ESI implements TriageClassifier {

    /**
     * Classifies a patient into a triage level based on diagnosis code.
     *
     * @param diagnosis_code The diagnosis code to classify.
     * @return Corresponding {@link TriageLevel}.
     * @throws IllegalArgumentException If the diagnosis code is not recognized.
     */
    @Override
    public TriageLevel classify(int diagnosis_code) {
        switch (diagnosis_code) {
            case 1: return TriageLevel.YELLOW;  // Syncope (ESI-2)
            case 2: return TriageLevel.YELLOW;  // Fever (avg ESI-3)
            case 3: return TriageLevel.RED;     // Shock (ESI-1)
            case 4: return TriageLevel.YELLOW;  // Nausea and vomiting (ESI-3)
            case 5: return TriageLevel.YELLOW;  // Dysphagia (ESI-3)
            case 6: return TriageLevel.YELLOW;  // Abdominal pain (ESI-3)
            case 7: return TriageLevel.GREEN;   // Malaise and fatigue (ESI-4)
            case 8: return TriageLevel.YELLOW;  // Mental/substance use (ESI-2)
            case 9: return TriageLevel.BLUE;    // Abnormal substance findings (ESI-5)
            case 10: return TriageLevel.YELLOW; // Nervous system (ESI-3)
            case 11: return TriageLevel.GREEN;  // Genitourinary (ESI-4)
            case 12: return TriageLevel.YELLOW; // Circulatory (ESI-2)
            case 13: return TriageLevel.ORANGE; // Respiratory (ESI-2)
            case 14: return TriageLevel.BLUE;   // Skin/Subcutaneous (ESI-5)
            case 15: return TriageLevel.GREEN;  // General sensation/perception (ESI-4)
            case 16: return TriageLevel.GREEN;  // Other general signs (ESI-4)
            case 17: return TriageLevel.BLUE;   // Abnormal findings w/o diagnosis (ESI-5)
            default:
                throw new IllegalArgumentException("Unknown diagnosis code: " + diagnosis_code);
        }
    }
}
