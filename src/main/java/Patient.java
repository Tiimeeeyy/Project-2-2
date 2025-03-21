import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Patient {
    private final UUID id;
    private String name;
    private int age;
    private TriageLevel triageLevel;
    private LocalDateTime arrivalTime;
    private LocalDateTime treatmentStartTime;
    private LocalDateTime dischargeTime;

    public Patient(UUID id, String name, int age, TriageLevel triageLevel, LocalDateTime arrivalTime) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.triageLevel = triageLevel;
        this.arrivalTime = arrivalTime;
    }

    @Getter
    public enum TriageLevel {
        RED(1, "Immediate"),
        ORANGE(2, "Very Urgent"),
        YELLOW(3, "Urgent"),
        GREEN(4, "Standard"),
        BLUE(5, "Non Urgent");

        private final int priority;
        private final String description;

        TriageLevel(int priority, String description) {
            this.priority = priority;
            this.description = description;
        }
    }

}
