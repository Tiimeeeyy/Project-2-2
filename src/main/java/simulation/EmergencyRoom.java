package simulation;

import lombok.Getter;
import lombok.Setter;
import staff.Role;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
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
    private Config config;

    private Map<String,Double> availableStaff;

    /**
     * Constructs a new EmergencyRoom with specified parameters.
     *
     * @param name           The name of the emergency room.
     * @param capacity       Maximum number of patients that can wait.
     * @param treatmentRooms Number of available treatment rooms.
     */
    public EmergencyRoom(String name, int capacity, int treatmentRooms) throws IOException {
        this.config = Config.getInstance();
        this.name = name;
        this.capacity = capacity;
        this.treatmentRooms = treatmentRooms;
        this.occupiedTreatmentRooms = 0;
        this.waitingPatients = new PriorityQueue<>(Comparator.comparingInt(p -> p.getTriageLevel().getPriority()));
        this.availableStaff =new HashMap<String,Double>();
        double nurses = 0.0;
        double physicians = 0.0;
        double residents = 0.0;
        for (Map.Entry<String,Integer> entry:config.getStaffCounts().entrySet()
             ) {
            switch (entry.getKey()){
                case "REGISTERED_NURSE":
                case "LICENSED_PRACTICAL_NURSE":
                case "CERTIFIED_NURSING_ASSISTANT":
                    nurses+=(double)entry.getValue();
                    break;
                case "ATTENDING_PHYSICIAN":
                    physicians+=(double)entry.getValue();
                    break;
                case "RESIDENT_PHYSICIAN":
                    residents+=(double)entry.getValue();
                    break;
            }
        }
        availableStaff.put("Nurses",nurses);
        availableStaff.put("Physicians",physicians);
        availableStaff.put("Residents",residents);
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

    /**
     * Simulates taking up staff resources
     * @param type the type of staff (nurse, physician, resident) that's being used
     * @param amount the number of staff members of the given type being used
     */
    public void occupyStaff(String type, double amount){
        availableStaff.put(type,availableStaff.get(type)-amount);
    }

    /**
     * Simulates freeing up staff resources
     * @param type the type of staff (nurse, physician, resident) that's being freed
     * @param amount the number of staff members of the given type being freed
     */
    public void freeStaff(String type, double amount){
        availableStaff.put(type,availableStaff.get(type)+amount);
    }

}
