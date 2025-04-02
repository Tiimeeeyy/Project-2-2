import lombok.Data;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Class that described the emergency room. 
 * Manages patient waiting queue, treatment rooms and capacity constraints.
 */
@Data
public class EmergencyRoom {
    private String name;
    private int capacity;
    private PriorityQueue<Patient> waitingPatients;
    private int treatmentRooms;
    private int occupiedTreatmentRooms;

    /**Constructs a enw Emergency Room Object
     *
     * @param name The name of the Emergency Room
     * @param capacity The maximum capacity / number of patients that can wait
     * @param treatmentRooms Number of treatment rooms available
     */
    public EmergencyRoom(String name, int capacity, int treatmentRooms) {
        this.name = name;
        this.capacity = capacity;
        this.treatmentRooms = treatmentRooms;
        this.occupiedTreatmentRooms = 0;

        this.waitingPatients = new PriorityQueue<>(
                Comparator.comparingInt(p -> p.getTriageLevel().getPriority())
        );
    }

    /**
     * Attempts to add a patient to the waiting room
     * @param patient The patient to add to the queue
     * @return true if patient can be added, false otherwise
     */
    public boolean addPatient(Patient patient) {
        if (waitingPatients.size() < capacity) {
            waitingPatients.add(patient);
            return true;
        }
        return false;
    }

    /**
     * Retrieves and removes the highest priority patient from the waiting queue.
     * @return The next patient to be treated based on triage level
     */
    public Patient getNextPatient() {
        return waitingPatients.poll();
    }

    /**
     * Checks if there are any treatment rooms available.
     * @return True if at least one treatment room is available, false otherwise
     */
    public boolean hasTreatmentRoomAvailable() {
        return occupiedTreatmentRooms < treatmentRooms;
    }

    /**
     * Marks a treatment room as occupied. Only increases the count if treatment rooms are available
     */
    public void occupyTreatmentRoom() {
        if (occupiedTreatmentRooms < treatmentRooms) {
            occupiedTreatmentRooms++;
        }
    }

    /**
     * Marks a treatment room as free. Only decreases the amount if at least one treatment room is occupied
     */
    public void freeTreatmentRoom() {
        if (occupiedTreatmentRooms > 0) {
            occupiedTreatmentRooms--;
        }
    }

}
