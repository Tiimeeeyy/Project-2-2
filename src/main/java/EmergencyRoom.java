import lombok.Data;

import java.util.Comparator;
import java.util.PriorityQueue;


@Data
public class EmergencyRoom {
    private String name;
    private int capacity;
    private PriorityQueue<Patient> waitingPatients;
    private int treatmentRooms;
    private int occupiedTreatmentRooms;

    public EmergencyRoom(String name, int capacity, int treatmentRooms) {
        this.name = name;
        this.capacity = capacity;
        this.treatmentRooms = treatmentRooms;
        this.occupiedTreatmentRooms = 0;

        this.waitingPatients = new PriorityQueue<>(
                Comparator.comparingInt(p -> p.getTriageLevel().getPriority())
        );
    }

    public boolean addPatient(Patient patient) {
        if (waitingPatients.size() < capacity) {
            waitingPatients.add(patient);
            return true;
        }
        return false;
    }

    public Patient getNextPatient() {
        return waitingPatients.poll();
    }

    public boolean hasTreatmentRoom() {
        return occupiedTreatmentRooms < treatmentRooms;
    }

    public void occupyTreatmentRoom() {
        if (occupiedTreatmentRooms < treatmentRooms) {
            occupiedTreatmentRooms++;
        }
    }

    public void releaseTreatmentRoom() {
        if (occupiedTreatmentRooms > 0) {
            occupiedTreatmentRooms--;
        }
    }

}
