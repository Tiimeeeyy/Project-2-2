package simulation;

import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Represents an emergency room that manages patient wait queues,
 * treatment rooms, and capacity constraints.
 */
@Getter
@Setter
public class EmergencyRoom {
    private final String name;
    private final int capacity;
    private final PriorityQueue<Patient> waitingPatients;
    private final int treatmentRooms;
    private int occupiedTreatmentRooms;

    /**
     * Constructs a new EmergencyRoom with specified parameters.
     *
     * @param name           The name of the emergency room.
     * @param capacity       Maximum number of patients that can wait.
     * @param treatmentRooms Number of available treatment rooms.
     */
    public EmergencyRoom(String name, int capacity, int treatmentRooms) {
        this.name = name;
        this.capacity = capacity;
        this.treatmentRooms = treatmentRooms;
        this.occupiedTreatmentRooms = 0;
        this.waitingPatients = new PriorityQueue<>(Comparator.comparingInt(p -> p.getTriageLevel().getPriority()));
    }

    /**
     * Attempts to add a patient to the waiting queue.
     *
     * @param patient The patient to add.
     * @return {@code true} if added successfully; {@code false} if at capacity.
     */
    public boolean addPatient(Patient patient) {
        if (waitingPatients.size() < capacity) {
            waitingPatients.add(patient);
            return true;
        }
        return false;
    }

    /**
     * Retrieves and removes the highest priority waiting patient.
     *
     * @return The next patient for treatment, or {@code null} if none.
     */
    public Patient getNextPatient() {
        return waitingPatients.poll();
    }

    /**
     * Checks if a treatment room is available.
     *
     * @return {@code true} if at least one room is free; {@code false} otherwise.
     */
    public boolean hasTreatmentRoomAvailable() {
        return occupiedTreatmentRooms < treatmentRooms;
    }

    /**
     * Marks one treatment room as occupied, if available.
     */
    public void occupyTreatmentRoom() {
        if (occupiedTreatmentRooms < treatmentRooms) {
            occupiedTreatmentRooms++;
        }
    }

    /**
     * Frees one occupied treatment room, if any are occupied.
     */
    public void freeTreatmentRoom() {
        if (occupiedTreatmentRooms > 0) {
            occupiedTreatmentRooms--;
        }
    }
}
