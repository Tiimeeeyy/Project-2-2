package simulation;

import lombok.Setter;
import lombok.Getter;

@Getter @Setter
public class PatientService {
    private String name;
    private String triageLevel;
    private double chance;
    private double timeMin;
    private double timeMax;
}
