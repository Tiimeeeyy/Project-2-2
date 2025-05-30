package simulation;

import lombok.Data;
import lombok.extern.java.Log;
import simulation.triage_classifiers.*;

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
    private final double epsilon = 0.001;
    private int rejectedPatients = 0;
    private int[][] data;
    private int deltaHours;
    private Expression e;
    private Argument t;
    private boolean useConfig=false;

    /**
     * Treatment times!
     */
    private final Map<Patient.TriageLevel, Double> avgTreatmentTimes = Map.of(
            Patient.TriageLevel.RED, 120.0,
            Patient.TriageLevel.ORANGE, 90.0,
            Patient.TriageLevel.YELLOW, 60.0,
            Patient.TriageLevel.GREEN, 30.0,
            Patient.TriageLevel.BLUE, 10.0
    );

    /**
     * Constructs a Simulator Object. The object can be used to run Poisson process simulations for a hospital in a city of a given size.
     * @param populationSize The Population size of the city the simulated hospital is in.
     */
    public PoissonSimulator(int populationSize) throws IOException {
        this.config = Config.getInstance();
        this.populationSize = populationSize;
        this.er = new EmergencyRoom("MUMC", 30, 15);
        this.currentTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        this.deltaHours = 0;
        this.useConfig = true;
        this.t = new Argument("t=0");
        this.e = new Expression("1",t);
    }
    public PoissonSimulator() throws IOException {
        this.config = Config.getInstance();
        this.populationSize = config.getPopulationSize();
        this.er = new EmergencyRoom(config.getERName(), config.getERCapacity(), config.getERTreatmentRooms());
        this.currentTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        this.deltaHours = 0;

        License.iConfirmNonCommercialUse("KEN12");
        this.t = new Argument("t=0");
        this.e = new Expression(config.getPatientArrivalFunctions().get(config.getDefaultArrivalFunction()),t);
        System.out.println("Initialized with expression '"+config.getDefaultArrivalFunction()+"': f(t) = "+e.getExpressionString());
    }

    /**
     * Main simulation method. Initializes a simulation with a given simulation duration.
     * @param simulationDuration The duration of the simulation.
     */
    public void runSimulation(Duration simulationDuration) {
        LocalDateTime endTime = currentTime.plus(simulationDuration);

        log.log(Level.INFO, "Starting ER simulation @ {0}", currentTime);
        data = new int[5][(int)simulationDuration.toHours()];
        while(currentTime.isBefore(endTime)) {
            processHour();
            deltaHours++;
            currentTime = currentTime.plusHours(1);
        }
        printStatistics();
    }

    /**
     * Processes an hour step of the simulation. Handles patient arrivals and discharges in an hourly fashion.
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
                //System.out.println("simulation.Patient " + patient.getName() + " (Triage: " + patient.getTriageLevel() + ") rejected - ER is full");
            } else {
                //System.out.println("simulation.Patient " + patient.getName() + "(Triage: " + patient.getTriageLevel() + ") arrived in ER");
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

                //System.out.println("simulation.Patient " + patient.getName() + " (Triage: " + patient.getTriageLevel() + ") discharged");
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

            //System.out.println("simulation.Patient " + patient.getName() + " (Triage: " + patient.getTriageLevel() + ") moved to treatment");
        }
    }

    private int generatePatientArrivals(){
        double[] weekdayFactors = {0.8647, 1.1324, 1.0294, 1.0294, 1.0294, 1.0088, 0.9059};
        double[] hourFactors = {
            0.5236, 0.48, 0.4364, 0.3927, 0.3927, 0.3927, 0.3927, 0.5236, 0.96, 1.5273, 1.7455, 1.6582, 
            1.44, 1.3091, 1.6582, 1.3964, 1.1782, 1.1782, 1.1782, 1.1782, 1.1782, 1.1782, 0.96, 0.7418
        };

        int hour = currentTime.getHour();
        int day_of_the_week = currentTime.getDayOfWeek().getValue();
        int month = currentTime.getMonthValue();


        t.setArgumentValue(month);
        // provides average daily arrivals for a given month
        double average_daily_arrival_rate = e.calculate();
        // adjust for fluctuations based on day of the week
        double weekday_arrival_rate = average_daily_arrival_rate * weekdayFactors[day_of_the_week - 1];
        // convert to hourly arrivals and adjust for hour of the day
        double hourly_arrival_rate = (weekday_arrival_rate / 24) * hourFactors[hour];

        PoissonDistribution poissonDistribution = new PoissonDistribution(hourly_arrival_rate);
        return poissonDistribution.sample();
    }

    private int generateDiagnosis(){
        // String[] diagnosisCodes = {
        //     "SYM001", "SYM002", "SYM003", "SYM004", "SYM005", "SYM006",
        //     "SYM007", "SYM008", "SYM009", "SYM010", "SYM011", "SYM012",
        //     "SYM013", "SYM014", "SYM015", "SYM016", "SYM017"
        // };
        double[] diagnosisProbs = {
            3.72908417e-02, 3.45021445e-02, 6.44438692e-04, 1.42655116e-01,
            4.82845207e-03, 2.06028792e-01, 4.42272662e-02, 1.19613046e-02,
            6.28956682e-06, 9.97375315e-02, 2.83615920e-02, 7.33431225e-02,
            1.14778789e-01, 4.28604950e-02, 4.97795023e-02, 4.95869448e-02,
            5.94073777e-02
        };

        // Sample a diagnosis code based on the given probabilities
        double r = random.nextDouble();
        double cumulative = 0.0;
        int diagnosisIndex = 0;
        for (int i = 0; i < diagnosisProbs.length; i++) {
            cumulative += diagnosisProbs[i];
            if (r < cumulative) {
            diagnosisIndex = i;
            break;
            }
        }
        return diagnosisIndex+1;

    }

    /**
     * Generates a random simulation.Patient Object.
     * @return The randomly generated patient.
     */
    private Patient generateRandomPatient() {
        UUID id = UUID.randomUUID();
        String name = "simulation.Patient" + Math.abs(id.hashCode() % 10000);
        int age = 5 + random.nextInt(95); // Ages 5-99

        
        int diagnosis = generateDiagnosis();


        Patient.TriageLevel triageLevel = new MTS().classify(diagnosis);

        // random change (10% to upgrade triage level)
        if (random.nextDouble() < 0.05) {
            switch (triageLevel) {
                case BLUE:
                    triageLevel = Patient.TriageLevel.GREEN;
                    break;
                case GREEN:
                    triageLevel = Patient.TriageLevel.YELLOW;
                    break;
                case YELLOW:
                    triageLevel = Patient.TriageLevel.ORANGE;
                    break;
                case ORANGE:
                    triageLevel = Patient.TriageLevel.RED;
                    break;
                default:
                    // RED is already the highest, do nothing
                    break;
            }
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
