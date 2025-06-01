package simulation.triage_classifiers;

import simulation.Patient.TriageLevel;

/**
 * Interface defining a classifier that assigns a {@link TriageLevel}
 * based on an integer diagnosis code.
 */
public interface TriageClassifier {

    /**
     * Classifies a patient to a triage level given a diagnosis code.
     *
     * @param diagnosis_code The diagnosis code to use for classification.
     * @return The resulting {@link TriageLevel}.
     */
    TriageLevel classify(int diagnosis_code);
}
