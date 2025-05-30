package simulation.triage_classifiers;

import simulation.Patient.TriageLevel;

public class CTAS implements TriageClassifier {

    @Override
    public TriageLevel classify(int diagnosis_code) {
        switch (diagnosis_code) {
            case 1: return TriageLevel.YELLOW; // Syncope (CTAS 3)
            case 2: return TriageLevel.GREEN;  // Fever (CTAS 4)
            case 3: return TriageLevel.RED;    // Shock (CTAS 1)
            case 4: return TriageLevel.BLUE;   // Nausea and vomiting (CTAS 5)
            case 5: return TriageLevel.ORANGE; // Dysphagia (CTAS 2)
            case 6: return TriageLevel.GREEN;  // Abdominal pain (CTAS 4)
            case 7: return TriageLevel.YELLOW; // Malaise and fatigue (CTAS 3)
            case 8: return TriageLevel.YELLOW; // Mental/substance use (CTAS 3)
            case 9: return TriageLevel.BLUE;   // Abnormal substance findings (CTAS 5)
            case 10: return TriageLevel.YELLOW; // Nervous system (CTAS 3)
            case 11: return TriageLevel.GREEN;  // Genitourinary (CTAS 4)
            case 12: return TriageLevel.ORANGE; // Circulatory (avg CTAS 2)
            case 13: return TriageLevel.ORANGE; // Respiratory (CTAS 2)
            case 14: return TriageLevel.GREEN;  // Skin/Subcutaneous (CTAS 4)
            case 15: return TriageLevel.BLUE;   // General sensation/perception (CTAS 5)
            case 16: return TriageLevel.BLUE;   // Other general signs (CTAS 5)
            case 17: return TriageLevel.BLUE;   // Abnormal findings w/o diagnosis (CTAS 5)
            default:
                throw new IllegalArgumentException("Unknown diagnosis code: " + diagnosis_code);
        }
    }
}
