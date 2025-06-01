package simulation;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a service offered to patients, including service name,
 * associated triage level, probability of occurrence, and time bounds.
 */
@Getter
@Setter
public class PatientService {
    private String name;
    private String triageLevel;
    private double chance;
    private double timeMin;
    private double timeMax;
}
