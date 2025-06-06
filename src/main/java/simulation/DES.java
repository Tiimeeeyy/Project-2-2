package simulation;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import simulation.triage_classifiers.CTAS;
//TODO: Add javadoc
public class DES {
    private final Config config;
    private final EmergencyRoom er;
    private final Random random;
    private final Expression arrivalExpression;
    private final Argument t;
    private final Map<Patient.TriageLevel, Double> avgTreatmentTimes;
    private ArrayList<Event> eventList;
    private Duration deltaTime;
    private double interarrivalTimeMins;
    private final boolean DETAILED_LOGGING = true;
    private int eventsProcessed = 0;
    private Duration simDuration;
    private int patientsTreated;
    private int patientsRejected;

    public DES() throws IOException {
        this.patientsRejected = 0;
        this.patientsTreated = 0;
        this.eventList = new ArrayList<>();
        this.deltaTime = Duration.ZERO;
        this.interarrivalTimeMins = 15;
        this.config = Config.getInstance();
        this.er = new EmergencyRoom(
                config.getERName(),
                config.getERCapacity(),
                config.getERTreatmentRooms()
        );
        this.random = new Random();
        this.t = new Argument("t=0");

        String exprString = config.getPatientArrivalFunctions()
                .get(config.getDefaultArrivalFunction());
        this.arrivalExpression = new Expression(exprString, t);
        System.out.println(
                "Initialized with expression '"
                        + config.getDefaultArrivalFunction()
                        + "': f(t) = "
                        + arrivalExpression.getExpressionString()
        );

        this.avgTreatmentTimes = Map.of(
                Patient.TriageLevel.RED, 120.0,
                Patient.TriageLevel.ORANGE, 90.0,
                Patient.TriageLevel.YELLOW, 60.0,
                Patient.TriageLevel.GREEN, 30.0,
                Patient.TriageLevel.BLUE, 10.0
        );
    }
    public void start(Duration duration){
        //create arrivals for the entire duration
        simDuration = duration;

        while(eventList.isEmpty() || eventList.getLast().getTime().compareTo(duration)<=0){
            createArrival();
        }
        eventList.removeLast();
        int numArrivals = eventList.size();
        System.out.println("Simulation duration: "+ duration.toString().substring(2));
        System.out.println(numArrivals+" arrivals generated");
        while(!eventList.isEmpty()&&deltaTime.compareTo(duration)<0){
            nextEvent();
        }
        System.out.println("Summary ("+duration.toString().substring(2)+" duration):\n"+eventsProcessed+" events processed\n"+numArrivals+" patient arrivals\n"
                +patientsTreated+" patients treated\n"+patientsRejected+" patients rejected");
    }
    private void createArrival(){
        Duration lastEventTime;
        if (eventList.isEmpty()){
            lastEventTime = Duration.ZERO;
        } else {
            lastEventTime = eventList.getLast().getTime();
        }
        t.setArgumentValue((int)Math.floor(lastEventTime.toHours()));
        ExponentialDistribution distribution = new ExponentialDistribution(interarrivalTimeMins/arrivalExpression.calculate());
        Patient p = generateRandomPatient();
        eventList.add(new Event(Duration.ofMinutes((long)distribution.sample()).plus(lastEventTime), "arrival", p));
    }
    private void nextEvent(){
        eventList.sort(null);
        Event e = eventList.removeFirst();
        if(e.getTime().compareTo(simDuration)<0) {
            eventsProcessed++;
            deltaTime = e.getTime();
            switch (e.getType()) {
                case "arrival":
                    arrival(deltaTime, e.getPatient());
                    break;
                case "release":
                    release(e.getPatient());
                    break;
            }
        }
    }

    private void arrival(Duration arrivalTime, Patient p){
        if (DETAILED_LOGGING) {
            System.out.println("EVENT " + eventsProcessed + " | TIME " + deltaTime.toString().substring(2) + " | Patient " + p.getName() + " arrives needing " + p.getTriageLevel().getDescription().toLowerCase() + " care.");
        }
        if(er.addPatient(p)) {
            if (er.hasTreatmentRoomAvailable()) {
                treat(er.getNextPatient());
            } else {
                if (DETAILED_LOGGING) {
                    System.out.println(new String(new char[("EVENT " + eventsProcessed + " | TIME " + deltaTime.toString().substring(2)).length()]).replace('\0', ' ') + " | Patient " + p.getName() + " entered the waiting room.");
                }
            }
        } else {
            patientsRejected++;
            if (DETAILED_LOGGING) {
                System.out.println(new String(new char[("EVENT " + eventsProcessed + " | TIME " + deltaTime.toString().substring(2)).length()]).replace('\0', ' ') + " | Patient " + p.getName() + " was rejected.");
            }
        }
    }

    private void treat(Patient p){
        if(DETAILED_LOGGING){
            System.out.println(new String(new char[("EVENT "+eventsProcessed+" | TIME "+deltaTime.toString().substring(2)).length()]).replace('\0',' ')+" | Patient "+p.getName()+" begins treatment.");
        }
        er.occupyTreatmentRoom();
        eventList.add(new Event(deltaTime.plus(p.getTreatmentTime()),"release", p));
    }

    private void release(Patient p){
        patientsTreated++;
        if(DETAILED_LOGGING){
            System.out.println("EVENT "+eventsProcessed+" | TIME "+deltaTime.toString().substring(2)+" | Patient "+p.getName()+" is discharged from the ER.");
        }
        er.freeTreatmentRoom();
        if(!er.getWaitingPatients().isEmpty()){
            treat(er.getNextPatient());
        }
    }

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
        //Treatment times follow a normal distribution, shape varies based on triage level.
        NormalDistribution treatmentTimeDist = new NormalDistribution(avgTreatmentTimes.get(triageLevel),0.25*avgTreatmentTimes.get(triageLevel));
        Duration treatmentTime = Duration.ofMinutes((long)treatmentTimeDist.sample());
        return new Patient(id, name, age, triageLevel, deltaTime, treatmentTime);
    }

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
}
