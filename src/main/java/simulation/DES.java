package simulation;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import simulation.triage_classifiers.CTAS;
import simulation.triage_classifiers.ESI;
import simulation.triage_classifiers.MTS;
import simulation.triage_classifiers.TriageClassifier;
import lombok.Getter;
//TODO: Add javadoc
@Getter
public class DES {
    private final Config config;
    private final EmergencyRoom er;
    private final Random random;
    private final Expression arrivalExpression;
    private final Argument t;
    private final Map<Patient.TriageLevel, Double> avgTreatmentTimes;
    private LinkedList<Event> eventList; // Changed to LinkedList for removeFirst/removeLast
    private Duration deltaTime;
    private double interarrivalTimeMins;
    private final boolean DETAILED_LOGGING = false; // Changed to false for web interface
    private int eventsProcessed = 0;
    private Duration simDuration;
    private int patientsTreated;
    private int patientsRejected;
    
    // Additional fields for data collection and configuration - GUI RELATED
    private int[][] data;
    private List<Patient> treatedPatients;
    private List<Patient> treatingPatients;
    private Map<String, Object> hyperparameters;
    private Patient.TriageLevel focusTriageLevel;
    private String scenarioType;
    private TriageClassifier triageClassifier;

    public DES() throws IOException {
        this.patientsRejected = 0;
        this.patientsTreated = 0;
        this.eventList = new LinkedList<>();
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
        
        // Initialize new fields - FOR GUI
        this.treatedPatients = new ArrayList<>();
        this.treatingPatients = new ArrayList<>();
        this.hyperparameters = new HashMap<>();
        this.focusTriageLevel = null;
        this.scenarioType = "regular"; // Default scenario
        this.triageClassifier = new CTAS(); // Default triage classifier
    }
    public void start(Duration duration){
        //create arrivals for the entire duration
        simDuration = duration;
        
        // Initialize data collection arrays - wouldnt compile without this
        int hours = (int) duration.toHours();
        this.data = new int[5][hours];

        while(eventList.isEmpty() || eventList.getLast().getTime().compareTo(duration)<=0){
            createArrival();
        }
        eventList.removeLast();
        int numArrivals = eventList.size();
        System.out.println("Simulation duration: "+ duration.toString().substring(2));
        System.out.println(numArrivals+" arrivals generated");
        
        // Record data during simulation
        int currentHour = 0;
        while(!eventList.isEmpty()&&deltaTime.compareTo(duration)<0){
            nextEvent();
            
            // Record hourly data
            int newHour = (int) deltaTime.toHours();
            if (newHour != currentHour && newHour < hours) {
                recordHourlyData(newHour);
                currentHour = newHour;
            }
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
        treatingPatients.add(p);
        eventList.add(new Event(deltaTime.plus(p.getTreatmentTime()),"release", p));
    }

    private void release(Patient p){
        patientsTreated++;
        if(DETAILED_LOGGING){
            System.out.println("EVENT "+eventsProcessed+" | TIME "+deltaTime.toString().substring(2)+" | Patient "+p.getName()+" is discharged from the ER.");
        }
        er.freeTreatmentRoom();
        treatingPatients.remove(p);
        treatedPatients.add(p);
        if(!er.getWaitingPatients().isEmpty()){
            treat(er.getNextPatient());
        }
    }
    
    private void recordHourlyData(int hour) {
        if (hour < data[0].length) {
            data[0][hour] = hour;
            // ts not poisson so 0 lmk if it breaks smth - emre
            data[1][hour] = 0; 
            data[2][hour] = er.getWaitingPatients().size();
            data[3][hour] = treatingPatients.size();
            data[4][hour] = er.getTreatmentRooms() - er.getOccupiedTreatmentRooms();
        }
    }
    
    // Configuration methods for web interface
    public void setHyperparameters(Map<String, Object> hyperparameters) {
        this.hyperparameters = hyperparameters;
        
        if (hyperparameters.containsKey("interarrivalTime")) {
            this.interarrivalTimeMins = ((Number) hyperparameters.get("interarrivalTime")).doubleValue();
        }
    }
    
    public void setFocusTriageLevel(Patient.TriageLevel triageLevel) {
        this.focusTriageLevel = triageLevel;
    }
    
    public void setScenarioType(String scenarioType) {
        this.scenarioType = scenarioType;
        // Apply scenario-specific modifications
        if ("emergency".equals(scenarioType)) {
            // Increase arrival rate during emergency
            this.interarrivalTimeMins = this.interarrivalTimeMins * 0.5; // Double the arrival rate
        }
    }
    
    public void setTriageClassifier(String classifierType) {
        switch (classifierType.toUpperCase()) {
            case "CTAS" -> this.triageClassifier = new CTAS();
            case "ESI" -> this.triageClassifier = new ESI();
            case "MTS" -> this.triageClassifier = new MTS();
            default -> this.triageClassifier = new CTAS(); // Default fallback
        }
    }

    private Patient generateRandomPatient() {
        UUID id = UUID.randomUUID();
        String name = "Patient" + Math.abs(id.hashCode() % 10000);
        int age = 5 + random.nextInt(95);
        int diagnosis = generateDiagnosis();
        Patient.TriageLevel triageLevel = triageClassifier.classify(diagnosis);

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
