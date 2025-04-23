import lombok.Data;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Poisson simulation for simulating patients arriving / getting treated / getting discharged / waiting etc.
 */
@Log @Data
public class PoissonSimulator {
    private Config config;
    private final EmergencyRoom er;
    private final Random random = new Random();
    private final int populationSize;
    private LocalDateTime currentTime;
    private final List<Patient> treatingPatients = new ArrayList<>();
    private final List<Patient> treatedPatients = new ArrayList<>();
    private int rejectedPatients = 0;
    private int[][] data;
    private int deltaHours;

    // !HYPERPARAMETER!
    private final double hourlyPatientRate;

    /**
     * Treatment times!
     */
    private final Map<Patient.TriageLevel, Double> avgTreatmentTimes = Map.of(
            Patient.TriageLevel.RED, 120.0,
            Patient.TriageLevel.ORANGE, 90.0,
            Patient.TriageLevel.YELLOW, 60.0,
            Patient.TriageLevel.GREEN, 45.0,
            Patient.TriageLevel.BLUE, 30.0
    );

    /**
     * Constructs a Simulator Object. The object can be used to run Poisson process simulations for a hospital in a city of a given size.
     * @param populationSize The Population size of the city the simulated hospital is in.
     */
    public PoissonSimulator(int populationSize) throws IOException {
        this.config = Config.getInstance();
        this.populationSize = populationSize;
        this.hourlyPatientRate = populationSize * 0.00008;
        this.er = new EmergencyRoom("MUMC", 30, 15);
        this.currentTime = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0);
        this.deltaHours = 0;
    }
    public PoissonSimulator() throws IOException {
        this.config = Config.getInstance();
        this.populationSize = config.getPopulationSize();
        this.hourlyPatientRate = populationSize * 0.00008;
        this.er = new EmergencyRoom(config.getERName(), config.getERCapacity(), config.getERTreatmentRooms());
        this.currentTime = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0);
        this.deltaHours = 0;
    }

    /**
     * Main simulation method. Initializes a simulation with a given simulation duration.
     * @param simulationDuration The duration of the simulation.
     */
    public void runSimulation(Duration simulationDuration) {
        LocalDateTime endTime = currentTime.plus(simulationDuration);

        log.log(Level.INFO, "Starting ER simulation @ {0}", currentTime);
        log.log(Level.INFO, "Expected Patients / hour: {0}", hourlyPatientRate);
        data = new int[5][(int)simulationDuration.toHours()];
        while(currentTime.isBefore(endTime)) {
            processHour();
            deltaHours++;
            currentTime = currentTime.plusHours(1);
        }
        Chart chart = new Chart(data);
        printStatistics();
        chart.display();
    }

    /**
     * Processes an hour step of the simulation. Handles patient arrivals and discharges in an hourly fashion.
     */
    private void processHour() {
        PoissonDistribution poissonDistribution = new PoissonDistribution(hourlyPatientRate); //TODO: scale by scenario expressions
        int newPatientCount = poissonDistribution.sample();

        int hour = currentTime.getHour();
        data[0][deltaHours] = hour;
        boolean isWeekend = currentTime.getDayOfWeek().getValue() >= 6;

        if ((hour >= 17 && hour <= 22) || isWeekend) {
            // More patients during evening times and weekends
            newPatientCount = (int) (newPatientCount * 1.2);
        } else if (hour >= 23 || hour <= 5) {
            // Fewer patients after 11 PM
            newPatientCount = (int) (newPatientCount * 0.6);
        }

        System.out.println("\n" + currentTime + " - New patients arriving: " + newPatientCount);
        data[1][deltaHours] = newPatientCount;
        for (int i = 0; i < newPatientCount; i++) {
            Patient patient = generateRandomPatient();

            boolean admitted = er.addPatient(patient);
            if (!admitted) {
                rejectedPatients++;
                //System.out.println("Patient " + patient.getName() + " (Triage: " + patient.getTriageLevel() + ") rejected - ER is full");
            } else {
                //System.out.println("Patient " + patient.getName() + "(Triage: " + patient.getTriageLevel() + ") arrived in ER");
            }
        }

        processTreatments();

        moveWaitingToTreatment();
        data[2][deltaHours] = er.getWaitingPatients().size();
        data[3][deltaHours] = treatingPatients.size();
        data[4][deltaHours] = (er.getTreatmentRooms() - er.getOccupiedTreatmentRooms());
        System.out.println("Hour summary: Waiting=" + er.getWaitingPatients().size() +
                ", In treatment=" + treatingPatients.size() +
                ", Rooms available=" + (er.getTreatmentRooms() - er.getOccupiedTreatmentRooms()));
    }

    /**
     * Processes a treatment for a given T
     */
    private void processTreatments() {
        Iterator<Patient> iterator = treatingPatients.iterator();

        while (iterator.hasNext()) {
            Patient patient = iterator.next();
            LocalDateTime expectedDischargeTime = patient.getTreatmentStartTime().plusMinutes(
                    (long) getTreatmentTimeForPatient(patient));

            if (currentTime.isAfter(expectedDischargeTime) || currentTime.isEqual(expectedDischargeTime)) {
                patient.setDischargeTime(currentTime);
                treatedPatients.add(patient);
                iterator.remove();
                er.freeTreatmentRoom();

                //System.out.println("Patient " + patient.getName() + " (Triage: " + patient.getTriageLevel() + ") discharged");
            }
        }
    }

    /**
     * Moves a patient from waiting to treatment.
     */
    private void moveWaitingToTreatment() {
        while (er.hasTreatmentRoomAvailable() && !er.getWaitingPatients().isEmpty()) {
            Patient patient = er.getNextPatient();
            patient.setTreatmentStartTime(currentTime);
            treatingPatients.add(patient);
            er.occupyTreatmentRoom();

            //System.out.println("Patient " + patient.getName() + " (Triage: " + patient.getTriageLevel() + ") moved to treatment");
        }
    }

    /**
     * Generates a random Patient Object.
     * @return The randomly generated patient.
     */
    private Patient generateRandomPatient() {
        UUID id = UUID.randomUUID();
        String name = "Patient" + Math.abs(id.hashCode() % 10000);
        int age = 5 + random.nextInt(95); // Ages 5-99

        Patient.TriageLevel triageLevel; //TODO: update existing triage levels with PatientService objects
        int rand = random.nextInt(100);
        if (rand < 5) {
            triageLevel = Patient.TriageLevel.RED;
        } else if (rand < 15) {
            triageLevel = Patient.TriageLevel.ORANGE;
        } else if (rand < 35) {
            triageLevel = Patient.TriageLevel.YELLOW;
        } else if (rand < 75) {
            triageLevel = Patient.TriageLevel.GREEN;
        } else {
            triageLevel = Patient.TriageLevel.BLUE;
        }

        return new Patient(id, name, age, triageLevel, currentTime);
    }

    /**
     * Samples an Exponential Distributed time from the average time for a given Patients Triage.
     * @param patient The patient that will be treated.
     * @return An exponentially sampled treatment time based on the patients Triage.
     */
    private double getTreatmentTimeForPatient(Patient patient) {
        double avgTime = avgTreatmentTimes.get(patient.getTriageLevel());
        ExponentialDistribution distribution = new ExponentialDistribution(avgTime);
        return distribution.sample();
    }

    /**
     * Utility function to print out statistics from the simulation.
     */
    private void printStatistics() {
        System.out.println("\n========== ER SIMULATION STATISTICS ==========");
        System.out.println("Population size: " + populationSize);
        System.out.println("Simulation period: " + Duration.between(currentTime.minusHours(1), currentTime.plusHours(1)).toHours() + " hours");
        System.out.println("Total patients: " + (treatedPatients.size() + treatingPatients.size() + er.getWaitingPatients().size() + rejectedPatients));
        System.out.println("Patients treated: " + treatedPatients.size());
        System.out.println("Patients in treatment: " + treatingPatients.size());
        System.out.println("Patients waiting: " + er.getWaitingPatients().size());
        System.out.println("Patients rejected: " + rejectedPatients);

        long totalWaitingMinutes = 0;
        long totalTreatmentMinutes = 0;

        for (Patient patient : treatedPatients) {
            Duration waitingTime = Duration.between(patient.getArrivalTime(), patient.getTreatmentStartTime());
            Duration treatmentTime = Duration.between(patient.getTreatmentStartTime(), patient.getDischargeTime());

            totalWaitingMinutes += waitingTime.toMinutes();
            totalTreatmentMinutes += treatmentTime.toMinutes();
        }

        if (!treatedPatients.isEmpty()) {
            double avgWaitingTime = totalWaitingMinutes / (double) treatedPatients.size();
            double avgTreatmentTime = totalTreatmentMinutes / (double) treatedPatients.size();

            System.out.println("Average waiting time: " + avgWaitingTime + " minutes");
            System.out.println("Average treatment time: " + avgTreatmentTime + " minutes");
        }
    }
}
