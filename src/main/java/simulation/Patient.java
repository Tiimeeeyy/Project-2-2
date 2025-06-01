package simulation;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a patient in the emergency room, including identifying information,
 * triage level, and timestamps for arrival, treatment start, and discharge.
 */
@Getter
@Setter
public class Patient {
    private final UUID id;
    private String name;
    private int age;
    private TriageLevel triageLevel;
    private LocalDateTime arrivalTime;
    private LocalDateTime treatmentStartTime;
    private LocalDateTime dischargeTime;

    /**
     * Constructs a new Patient with the given attributes.
     *
     * @param id             Unique identifier for the patient.
     * @param name           Name of the patient.
     * @param age            Age of the patient.
     * @param triageLevel    Assigned triage level.
     * @param arrivalTime    Timestamp of patient arrival.
     */
    public Patient(UUID id, String name, int age, TriageLevel triageLevel, LocalDateTime arrivalTime) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.triageLevel = triageLevel;
        this.arrivalTime = arrivalTime;
    }

    /**
     * Enumerates patient triage levels, each with a priority and description.
     * Lower numeric priority indicates higher urgency.
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
         * Constructs a new TriageLevel.
         *
         * @param priority    Numeric priority (lower = higher urgency).
         * @param description Human-readable description of the level.
         */
        TriageLevel(int priority, String description) {
            this.priority = priority;
            this.description = description;
        }
    }
}
