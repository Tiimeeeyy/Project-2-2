package simulation;

import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a patient attending the Emergency room.
 * Contains the most important patient information, like triage level, a name and timestamps of entry, treatment and discharge.
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
     * Constructs a new simulation.Patient object with the given attributes
     * @param id The patients assigned ID
     * @param name The name of the simulation.Patient
     * @param age The age of the simulation.Patient
     * @param triageLevel The patients triage level
     * @param arrivalTime The arrival time of the simulation.Patient
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
         * @param description A human-readable description of the triage level.
         */
        TriageLevel(int priority, String description) {
            this.priority = priority;
            this.description = description;
        }

        public int getPriority() {
            return priority;
        }
    }

    public TriageLevel getTriageLevel() {
        return triageLevel;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public LocalDateTime getTreatmentStartTime() {
        return treatmentStartTime;
    }

    public LocalDateTime getDischargeTime() {
        return dischargeTime;
    }

    public void setDischargeTime(LocalDateTime dischargeTime) {
        this.dischargeTime = dischargeTime;
    }

    public void setTreatmentStartTime(LocalDateTime treatmentStartTime) {
        this.treatmentStartTime = treatmentStartTime;
    }
}
