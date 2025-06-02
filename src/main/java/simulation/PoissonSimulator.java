package simulation;

import lombok.Getter;
import lombok.extern.java.Log;
import simulation.triage_classifiers.CTAS;
import simulation.triage_classifiers.MTS;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.License;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

/**
 * Simulator that uses a Poisson process to model patient arrivals,
 * handling treatment, waiting, and discharge in an emergency room.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Initializing simulation parameters and resources (ER, timers, queues).</li>
 *   <li>Generating patient arrivals based on a time‐dependent Poisson distribution.</li>
 *   <li>Moving patients from the waiting queue into treatment rooms when available.</li>
 *   <li>Processing ongoing treatments and discharging patients once their service time elapses.</li>
 *   <li>Collecting and printing summary statistics at the end of the simulation period.</li>
 * </ul>
 */
@Log
@Getter
public class PoissonSimulator {
    private final Config config;
    private final EmergencyRoom er;
    private final Random random;
    private final int populationSize;
    private LocalDateTime currentTime;
    private final List<Patient> treatingPatients;
    private final List<Patient> treatedPatients;
    private final double epsilon;
    private int rejectedPatients;
    private int[][] data;
    private int deltaHours;
    private final Expression arrivalExpression;
    private final Argument t;
    private final Map<Patient.TriageLevel, Double> avgTreatmentTimes;
    private boolean useConfig;

    /**
     * Constructs a PoissonSimulator with a specified population size.
     * <p>
     * Uses default emergency room settings ("MUMC", capacity 30, 15 treatment rooms)
     * and a constant arrival rate of 1 (for testing or custom runs).
     * </p>
     *
     * @param populationSize Population size of the city containing the hospital.
     * @throws IOException If loading {@link Config} fails.
     */
    public PoissonSimulator(int populationSize) throws IOException {
        this.config = Config.getInstance();
        this.populationSize = populationSize;
        this.er = new EmergencyRoom("MUMC", 30, 15);
        this.currentTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        this.random = new Random();
        this.deltaHours = 0;
        this.useConfig = true;
        this.t = new Argument("t=0");
        this.arrivalExpression = new Expression("1", t);
        this.treatingPatients = new ArrayList<>();
        this.treatedPatients = new ArrayList<>();
        this.epsilon = 0.001;
        this.rejectedPatients = 0;

        this.avgTreatmentTimes = Map.of(
            Patient.TriageLevel.RED, 120.0,
            Patient.TriageLevel.ORANGE, 90.0,
            Patient.TriageLevel.YELLOW, 60.0,
            Patient.TriageLevel.GREEN, 30.0,
            Patient.TriageLevel.BLUE, 10.0
        );
    }

    /**
     * Constructs a PoissonSimulator using configuration parameters from {@link Config}.
     * <p>
     * Reads population size, ER name, capacity, treatment rooms, and patient arrival function
     * from the JSON‐backed {@link Config} instance.
     * </p>
     *
     * @throws IOException If loading {@link Config} fails.
     */
    public PoissonSimulator() throws IOException {
        this.config = Config.getInstance();
        this.populationSize = config.getPopulationSize();
        this.er = new EmergencyRoom(
            config.getERName(),
            config.getERCapacity(),
            config.getERTreatmentRooms()
        );
        this.currentTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        this.random = new Random();
        this.deltaHours = 0;
        this.useConfig = true;
        this.t = new Argument("t=0");

        // Confirm non‐commercial use for mxparser
        License.iConfirmNonCommercialUse("KEN12");
        String exprString = config.getPatientArrivalFunctions()
                                   .get(config.getDefaultArrivalFunction());
        this.arrivalExpression = new Expression(exprString, t);
        System.out.println(
            "Initialized with expression '"
            + config.getDefaultArrivalFunction()
            + "': f(t) = "
            + arrivalExpression.getExpressionString()
        );

        this.treatingPatients = new ArrayList<>();
        this.treatedPatients = new ArrayList<>();
        this.epsilon = 0.001;
        this.rejectedPatients = 0;

        this.avgTreatmentTimes = Map.of(
            Patient.TriageLevel.RED, 120.0,
            Patient.TriageLevel.ORANGE, 90.0,
            Patient.TriageLevel.YELLOW, 60.0,
            Patient.TriageLevel.GREEN, 30.0,
            Patient.TriageLevel.BLUE, 10.0
        );
    }

    /**
     * Runs the simulation for a given duration, processing patient arrivals,
     * treatments, and discharges on an hourly basis.
     *
     * @param simulationDuration Total duration of the simulation (e.g., Duration.ofDays(7)).
     */
    public void runSimulation(Duration simulationDuration) {
        LocalDateTime endTime = currentTime.plus(simulationDuration);
        log.log(Level.INFO, "Starting ER simulation @ {0}", currentTime);

        data = new int[5][(int) simulationDuration.toHours()];
        while (currentTime.isBefore(endTime)) {
            processHour();
            deltaHours++;
            currentTime = currentTime.plusHours(1);
        }
        printStatistics();
    }

    /**
     * Processes one hourly time step in the simulation:
     * <ol>
     *   <li>Generate new arrivals via a Poisson distribution.</li>
     *   <li>Add arriving patients to the ER queue (reject if full).</li>
     *   <li>Process ongoing treatments and discharge if complete.</li>
     *   <li>Move waiting patients into available treatment rooms.</li>
     *   <li>Record queue, treatment, and room availability data.</li>
     * </ol>
     */
    private void processHour() {
        data[0][deltaHours] = deltaHours;
        int newPatientCount = generatePatientArrivals();
        System.out.println("\n" + currentTime + " - New patients arriving: " + newPatientCount);
        data[1][deltaHours] = newPatientCount;

        for (int i = 0; i < newPatientCount; i++) {
            Patient patient = generateRandomPatient();
            boolean admitted = er.addPatient(patient);
            if (!admitted) {
                rejectedPatients++;
            }
        }

        processTreatments();
        moveWaitingToTreatment();

        data[2][deltaHours] = er.getWaitingPatients().size();
        data[3][deltaHours] = treatingPatients.size();
        data[4][deltaHours] = er.getTreatmentRooms() - er.getOccupiedTreatmentRooms();

        System.out.println(
            "Hour summary: Waiting=" + er.getWaitingPatients().size()
            + ", In treatment=" + treatingPatients.size()
            + ", Rooms available=" + (er.getTreatmentRooms() - er.getOccupiedTreatmentRooms())
        );
    }

    /**
     * Iterates over currently treated patients and discharges those whose
     * sampled treatment time has elapsed.
     */
    private void processTreatments() {
        Iterator<Patient> iterator = treatingPatients.iterator();
        while (iterator.hasNext()) {
            Patient patient = iterator.next();
            LocalDateTime expectedDischargeTime = patient.getTreatmentStartTime()
                .plusMinutes((long) getTreatmentTimeForPatient(patient));
            if (!currentTime.isBefore(expectedDischargeTime)) {
                patient.setDischargeTime(currentTime);
                treatedPatients.add(patient);
                iterator.remove();
                er.freeTreatmentRoom();
            }
        }
    }

    /**
     * Moves highest‐priority waiting patients into available treatment rooms
     * until either no rooms remain or no patients are waiting.
     */
    private void moveWaitingToTreatment() {
        while (er.hasTreatmentRoomAvailable() && !er.getWaitingPatients().isEmpty()) {
            Patient patient = er.getNextPatient();
            patient.setTreatmentStartTime(currentTime);
            treatingPatients.add(patient);
            er.occupyTreatmentRoom();
        }
    }

    /**
     * Computes the number of new patient arrivals in the current hour using a
     * Poisson distribution adjusted for seasonality (monthly), weekday factors,
     * and hourly factors.
     *
     * @return Number of new arrivals this hour.
     */
    private int generatePatientArrivals() {
        // Factors for each day of week (Monday = index 1)
        double[] weekdayFactors = {
            0.8647, 1.1324, 1.0294, 1.0294, 1.0294, 1.0088, 0.9059
        };
        // Hourly multipliers for all 24 hours
        double[] hourFactors = {
            0.5236, 0.48,    0.4364, 0.3927, 0.3927, 0.3927, 0.3927, 0.5236,
            0.96,   1.5273,  1.7455, 1.6582, 1.44,   1.3091, 1.6582, 1.3964,
            1.1782, 1.1782,  1.1782, 1.1782, 1.1782, 1.1782, 0.96,   0.7418
        };

        int hour = currentTime.getHour();
        int dayOfWeek = currentTime.getDayOfWeek().getValue();
        int month = currentTime.getMonthValue();

        // Set 't' argument to current month for seasonal function
        t.setArgumentValue(month);
        double averageDailyRate = arrivalExpression.calculate();
        double weekdayRate = averageDailyRate * weekdayFactors[dayOfWeek - 1];
        double hourlyRate = (weekdayRate / 24) * hourFactors[hour];

        PoissonDistribution distribution = new PoissonDistribution(hourlyRate);
        return distribution.sample();
    }

    /**
     * Samples a diagnosis index (1–17) based on predefined probabilities.
     *
     * @return Diagnosis code (1‐based index).
     */
    private int generateDiagnosis() {
        double[] diagnosisProbs = {
            3.72908417e-02, 3.45021445e-02, 6.44438692e-04, 1.42655116e-01,
            4.82845207e-03, 2.06028792e-01, 4.42272662e-02, 1.19613046e-02,
            6.28956682e-06, 9.97375315e-02, 2.83615920e-02, 7.33431225e-02,
            1.14778789e-01, 4.28604950e-02, 4.97795023e-02, 4.95869448e-02,
            5.94073777e-02
        };
        double r = random.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < diagnosisProbs.length; i++) {
            cumulative += diagnosisProbs[i];
            if (r < cumulative) {
                return i + 1;
            }
        }
        // Fallback in case cumulative never exceeds r
        return diagnosisProbs.length;
    }

    /**
     * Generates a random {@link Patient} with:
     * <ul>
     *   <li>A unique UUID.</li>
     *   <li>A synthetic name "PatientXXXX".</li>
     *   <li>An age between 5 and 99.</li>
     *   <li>A triage level classified by {@link MTS}.</li>
     *   <li>A 5% chance to upgrade one triage level.</li>
     * </ul>
     *
     * @return Newly created {@link Patient}.
     */
    private Patient generateRandomPatient() {
        UUID id = UUID.randomUUID();
        String name = "Patient" + Math.abs(id.hashCode() % 10000);
        int age = 5 + random.nextInt(95);
        int diagnosis = generateDiagnosis();
        Patient.TriageLevel triageLevel = new CTAS().classify(diagnosis);

        // 5% chance to escalate triage priority
        if (random.nextDouble() < 0.05) {
            switch (triageLevel) {
                case BLUE -> triageLevel = Patient.TriageLevel.GREEN;
                case GREEN -> triageLevel = Patient.TriageLevel.YELLOW;
                case YELLOW -> triageLevel = Patient.TriageLevel.ORANGE;
                case ORANGE -> triageLevel = Patient.TriageLevel.RED;
                default -> {
                    // RED remains RED
                }
            }
        }
        return new Patient(id, name, age, triageLevel, currentTime);
    }

    /**
     * Samples an exponentially distributed treatment time (in minutes)
     * based on the patient's average service time for their triage level.
     *
     * @param patient Patient for whom to sample treatment duration.
     * @return Sampled treatment duration in minutes.
     */
    private double getTreatmentTimeForPatient(Patient patient) {
        double avgTime = avgTreatmentTimes.get(patient.getTriageLevel());
        ExponentialDistribution distribution = new ExponentialDistribution(avgTime);
        return distribution.sample();
    }

    /**
     * Prints summary statistics at the end of the simulation, including:
     * <ul>
     *   <li>Total population size.</li>
     *   <li>Simulation period (hours).</li>
     *   <li>Total patients (treated, in treatment, waiting, rejected).</li>
     *   <li>Number treated, in treatment, waiting, and rejected.</li>
     *   <li>Average waiting time and treatment time (if any patients treated).</li>
     * </ul>
     */
    private void printStatistics() {
        System.out.println("\n========== ER SIMULATION STATISTICS ==========");
        System.out.println("Population size: " + populationSize);
        System.out.println(
            "Simulation period: "
            + Duration.between(currentTime.minusHours(1), currentTime.plusHours(1)).toHours()
            + " hours"
        );
        int totalPatients = treatedPatients.size()
                          + treatingPatients.size()
                          + er.getWaitingPatients().size()
                          + rejectedPatients;
        System.out.println("Total patients: " + totalPatients);
        System.out.println("Patients treated: " + treatedPatients.size());
        System.out.println("Patients in treatment: " + treatingPatients.size());
        System.out.println("Patients waiting: " + er.getWaitingPatients().size());
        System.out.println("Patients rejected: " + rejectedPatients);

        long totalWaitingMinutes = 0;
        long totalTreatmentMinutes = 0;
        for (Patient patient : treatedPatients) {
            Duration waitingTime = Duration.between(
                patient.getArrivalTime(),
                patient.getTreatmentStartTime()
            );
            Duration treatmentTime = Duration.between(
                patient.getTreatmentStartTime(),
                patient.getDischargeTime()
            );
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
