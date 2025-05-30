package simulation.triage_classifiers;

import simulation.Patient;

public interface TriageClassifier {
    public Patient.TriageLevel classify(int diagnosis_code);
}
