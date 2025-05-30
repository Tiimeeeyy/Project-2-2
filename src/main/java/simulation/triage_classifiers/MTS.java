package simulation.triage_classifiers;

import simulation.Patient.TriageLevel;

public class MTS implements TriageClassifier {

    @Override
    public TriageLevel classify(int diagnosis_code) {
        switch (diagnosis_code) {
            case 1: return TriageLevel.YELLOW; // Syncope
            case 2: return TriageLevel.YELLOW; // Fever
            case 3: return TriageLevel.RED;    // Shock
            case 4: return TriageLevel.YELLOW; // Nausea and vomiting
            case 5: return TriageLevel.YELLOW; // Dysphagia
            case 6: return TriageLevel.YELLOW; // Abdominal pain
            case 7: return TriageLevel.GREEN;  // Malaise and fatigue
            case 8: return TriageLevel.YELLOW; // Mental/substance use
            case 9: return TriageLevel.BLUE;   // Abnormal substance findings
            case 10: return TriageLevel.YELLOW; // Nervous system
            case 11: return TriageLevel.GREEN; // Genitourinary
            case 12: return TriageLevel.YELLOW; // Circulatory
            case 13: return TriageLevel.ORANGE; // Respiratory
            case 14: return TriageLevel.GREEN;  // Skin/Subcutaneous
            case 15: return TriageLevel.GREEN;  // General sensation/perception
            case 16: return TriageLevel.GREEN;  // Other general signs
            case 17: return TriageLevel.BLUE;   // Abnormal findings w/o diagnosis
            default:
                throw new IllegalArgumentException("Unknown diagnosis code: " + diagnosis_code);
        }
    }
}

