package simulation;

import lombok.Getter;
import lombok.extern.java.Log;
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
    private boolean useConfig;

    private final Map<Patient.TriageLevel, Double> avgTreatmentTimes;

    /**
     * Constructs a PoissonSimulator using the specified population size.
     * Initializes configuration, emergency room, timing, and arrival functions.
     *
     * @param populationSize Population size of the city containing the hospital.
     * @throws IOException If configuration cannot be loaded.
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
     * Constructs a PoissonSimulator using configuration parameters.
     * Initializes configuration, emergency room, timing, and arrival functions.
     *
     * @throws IOException If configuration cannot be loaded.
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
        License.iConfirmNonCommercialUse("KEN12");
        String exprString = config.getPatientArrivalFunctions().get(config.getDefaultArrivalFunction());
        this.arrivalExpression = new Expression(exprString, t);
        System.out.println(
            "Initialized with expression '" + config.getDefaultArrivalFunction() + "': f(t) = " + arrivalExpression.getExpressionString()
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
     * Runs the simulation for the specified duration, processing patient
     * arrivals, treatments, and discharges on an hourly basis.
     *
     * @param simulationDuration Duration for which to run the simulation.
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
     * Processes one hour of simulation: generates arrivals, processes treatments,
     * moves waiting patients to treatment, and records data.
     */
    private void processHour() {
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
            "Hour summary: Waiting=" + er.getWaitingPatients().size() +
            ", In treatment=" + treatingPatients.size() +
            ", Rooms available=" + (er.getTreatmentRooms() - er.getOccupiedTreatmentRooms())
        );
    }

    /**
     * Processes ongoing treatments, discharging patients whose treatment
     * duration has elapsed.
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
     * Moves highest-priority waiting patients to available treatment rooms.
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
     * Generates a random number of patient arrivals based on Poisson distribution,
     * considering seasonal, weekday, and hourly factors.
     *
     * @return Number of new patient arrivals.
     */
    private int generatePatientArrivals() {
        double[] weekdayFactors = {0.8647, 1.1324, 1.0294, 1.0294, 1.0294, 1.0088, 0.9059};
        double[] hourFactors = {
            0.5236, 0.48, 0.4364, 0.3927, 0.3927, 0.3927, 0.3927, 0.5236,
            0.96, 1.5273, 1.7455, 1.6582, 1.44, 1.3091, 1.6582, 1.3964,
            1.1782, 1.1782, 1.1782, 1.1782, 1.1782, 1.1782, 0.96, 0.7418
        };

        int hour = currentTime.getHour();
        int dayOfWeek = currentTime.getDayOfWeek().getValue();
        int month = currentTime.getMonthValue();

        t.setArgumentValue(month);
        double averageDailyRate = arrivalExpression.calculate();
        double weekdayRate = averageDailyRate * weekdayFactors[dayOfWeek - 1];
        double hourlyRate = (weekdayRate / 24) * hourFactors[hour];

        PoissonDistribution distribution = new PoissonDistribution(hourlyRate);
        return distribution.sample();
    }

    /**
     * Generates a random diagnosis index based on predefined probabilities.
     *
     * @return Diagnosis index (1-based).
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
        return diagnosisProbs.length; // fallback
    }

    /**
     * Generates a random Patient with a unique ID, random name, age, and triage level.
     * There is a small chance to upgrade triage level randomly.
     *
     * @return A newly generated Patient.
     */
    private Patient generateRandomPatient() {
        UUID id = UUID.randomUUID();
        String name = "Patient" + Math.abs(id.hashCode() % 10000);
        int age = 5 + random.nextInt(95);
        int diagnosis = generateDiagnosis();
        Patient.TriageLevel triageLevel = new MTS().classify(diagnosis);

        if (random.nextDouble() < 0.05) {
            switch (triageLevel) {
                case BLUE -> triageLevel = Patient.TriageLevel.GREEN;
                case GREEN -> triageLevel = Patient.TriageLevel.YELLOW;
                case YELLOW -> triageLevel = Patient.TriageLevel.ORANGE;
                case ORANGE -> triageLevel = Patient.TriageLevel.RED;
                default -> { /* RED stays RED */ }
            }
        }
        return new Patient(id, name, age, triageLevel, currentTime);
    }

    /**
     * Samples an exponentially distributed treatment time based on the patient's triage level.
     *
     * @param patient The patient for whom to compute treatment time.
     * @return Sampled treatment duration in minutes.
     */
    private double getTreatmentTimeForPatient(Patient patient) {
        double avgTime = avgTreatmentTimes.get(patient.getTriageLevel());
        ExponentialDistribution distribution = new ExponentialDistribution(avgTime);
        return distribution.sample();
    }

    /**
     * Prints statistics summarizing the simulation: total patients, waiting times,
     * treatment times, and other relevant metrics.
     */
    private void printStatistics() {
        System.out.println("\n========== ER SIMULATION STATISTICS ==========");
        System.out.println("Population size: " + populationSize);
        System.out.println(
            "Simulation period: " + Duration.between(
                currentTime.minusHours(1), currentTime.plusHours(1)
            ).toHours() + " hours"
        );
        System.out.println(
            "Total patients: " + (treatedPatients.size() + treatingPatients.size()
            + er.getWaitingPatients().size() + rejectedPatients)
        );
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
