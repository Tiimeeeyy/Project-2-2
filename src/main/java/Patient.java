import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a patient attending the Emergency room.
 * Contains the most important patient information, like triage level, a name and timestamps of entry, tretment and discharge.
 */
@Data
public class Patient {
    private final UUID id;
    private String name;
    private int age;
    private TriageLevel triageLevel;
    private LocalDateTime arrivalTime;
    private LocalDateTime treatmentStartTime;
    private LocalDateTime dischargeTime;

    /**
     * Constructs a new Patient object with the given attributes
     * @param id The patients assigned ID
     * @param name The name of the Patient
     * @param age The age of the Patient
     * @param triageLevel The patients triage level
     * @param arrivalTime The arrival time of the Patient
     */
    public Patient(UUID id, String name, int age, TriageLevel triageLevel, LocalDateTime arrivalTime) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.triageLevel = triageLevel;
        this.arrivalTime = arrivalTime;
    }

    /**
     * Enumerates the patients triage level, with priority and description
     */
    @Getter
    public enum TriageLevel {
        RED(1, "Immediate"),
        ORANGE(2, "Very Urgent"),
        YELLOW(3, "Urgent"),
        GREEN(4, "Standard"),
        BLUE(5, "Non Urgent");

        private final int priority;
        private final String description;

        /**
         * Creates a new triage level.
         * @param priority The priority level (lower = higher priority)
         * @param description A human readable description of the triage level.
         */
        TriageLevel(int priority, String description) {
            this.priority = priority;
            this.description = description;
        }
    }

}
