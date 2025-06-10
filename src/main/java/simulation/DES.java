package simulation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import scheduling.*;
import simulation.triage_classifiers.CTAS;
import simulation.triage_classifiers.ESI;
import simulation.triage_classifiers.MTS;
import simulation.triage_classifiers.TriageClassifier;
import lombok.Getter;
import staff.*;

/**
 * Contains the Discrete Event Simulation for the ER and its associated data collection for the GUI.
 */
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
    private int patientsTreated;
    private int patientsRejected;
    private LocalDateTime startTime;
    
    // Additional fields for data collection and configuration - GUI RELATED
    private int[][] data;
    private List<Patient> treatedPatients;
    private List<Patient> treatingPatients;
    private Map<String, Object> hyperparameters;
    private Patient.TriageLevel focusTriageLevel;
    private String scenarioType;
    private TriageClassifier triageClassifier;
    private OptimizedScheduleOutput nurseSchedule;
    private OptimizedScheduleOutput physicianSchedule;
    private OptimizedScheduleOutput residentSchedule;
    private String stringOutputData;
    private final boolean useRandomSchedule;
    private int totalArrivals;
    private int hourlyArrivals;
    private int totalERAdmissions;
    private double totalTreatmentTime;
    private double avgTreatmentTime;
    private double totalWaitTime;
    private double avgWaitTime;

    public DES() throws IOException {
        this.totalTreatmentTime=0;
        this.stringOutputData = "Hour,Arrivals,Waiting,Treating,Available Rooms,Total Treatment Time," +
                "Average Treatment Time,Total Wait Time,Average Wait Time,Total Arrivals\n";
        this.useRandomSchedule = false;
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
                Patient.TriageLevel.RED, config.getAvgTreatmentTimesMins().get("RED"),
                Patient.TriageLevel.ORANGE,  config.getAvgTreatmentTimesMins().get("ORANGE"),
                Patient.TriageLevel.YELLOW,  config.getAvgTreatmentTimesMins().get("YELLOW"),
                Patient.TriageLevel.GREEN,  config.getAvgTreatmentTimesMins().get("GREEN"),
                Patient.TriageLevel.BLUE,  config.getAvgTreatmentTimesMins().get("BLUE")
        );
        
        // Initialize new fields - FOR GUI
        this.treatedPatients = new ArrayList<>();
        this.treatingPatients = new ArrayList<>();
        this.hyperparameters = new HashMap<>();
        this.focusTriageLevel = null;
        this.scenarioType = "regular"; // Default scenario
        this.triageClassifier = new CTAS(); // Default triage classifier
    }

    /**
     * Starts the simulation, starting at midnight. All parameters except Duration are from the config.json file.
     * @param duration the duration of the simulation.
     */
    public void start(Duration duration) throws FileNotFoundException {
        //schedule staff
        System.out.println("Scheduling staff...");
        nurseSchedule = getSchedule(duration, "nurse");
        physicianSchedule = getSchedule(duration, "physician");
        residentSchedule = getSchedule(duration, "resident");

        this.hourlyArrivals = 0;
        // Initialize data collection arrays - wouldn't compile without this
        int hours = (int) duration.toHours();
        this.data = new int[10][hours];


        //create arrivals for the entire duration
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
            nextEvent(duration);
            
            // Record hourly data
            int newHour = (int) deltaTime.toHours();
            if (newHour != currentHour && newHour < hours) {
                recordHourlyData(newHour);
                currentHour = newHour;
            }
        }
        writeToCSV();
        System.out.println("Summary ("+duration.toString().substring(2)+" duration):\n"+eventsProcessed+" events processed\n"+numArrivals+" patient arrivals\n"
                +patientsTreated+" patients treated\n"+patientsRejected+" patients rejected");
    }

    /**
     * Generates an arrival event prior to the start of the simulation. Interarrival times follow a Poisson process,
     * drawing arrivals from an exponential distribution with the interarrivalTime variable as parameter.
     */
    private void createArrival(){
        Duration lastEventTime;
        if (eventList.isEmpty()){
            lastEventTime = Duration.ZERO;
        } else {
            lastEventTime = eventList.getLast().getTime();
        }
        t.setArgumentValue(+(int)Math.floor(lastEventTime.toHours()));
        ExponentialDistribution distribution = new ExponentialDistribution(interarrivalTimeMins/arrivalExpression.calculate());
        Patient p = generateRandomPatient();
        eventList.add(new Event(Duration.ofMinutes((long)distribution.sample()).plus(lastEventTime), "arrival", p));
    }

    /**
     * Ticks over to the next event in the simulation and calls the relevant function to process it.
     * @param simDuration the total duration of the simulation
     */
    private void nextEvent(Duration simDuration){
        eventList.sort(null);
        Event e = eventList.removeFirst();
        if(e.getTime().compareTo(simDuration)<0) {
            eventsProcessed++;
            deltaTime = e.getTime();
            switch (e.getType()) {
                case "arrival":
                    arrival(e.getPatient());
                    break;
                case "release":
                    release(e.getPatient());
                    break;
            }
        }
    }

    /**
     * Handles a single arrival event for a given patient.
     * @param p the patient that arrives
     */
    private void arrival(Patient p){
        totalArrivals++;
        p.setArrivalTime(deltaTime);
        hourlyArrivals++;
        if (DETAILED_LOGGING) {
            System.out.println("EVENT " + eventsProcessed + " | TIME " + deltaTime.toString().substring(2) + " | Patient " + p.getName() + " arrives needing " + p.getTriageLevel().getDescription().toLowerCase() + " care.");
        }
        if(er.addPatient(p)) {
            totalERAdmissions++;
            if (canTreatPatient(p)) {
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

    /**
     * Checks if it's possible to treat a given patient based on their triage level and the ER's availability.
     * @param p the patient to check
     * @return true if there are enough resources to treat the patient, false otherwise
     */
    private boolean canTreatPatient(Patient p){
        if(er.hasTreatmentRoomAvailable()){
            String triageLevel = p.getTriageLevel().name();
            double reqNurses = config.getTriageNurseRequirements().get(triageLevel);
            double reqPhysicians = config.getTriagePhysicianRequirements().get(triageLevel);
            double reqRPs = config.getTriageRPRequirements().get(triageLevel);
            //currently treats nurse roles equally
            Map<String,Double> availableStaff = er.getAvailableStaff();
            if((reqNurses<=availableStaff.get("Nurses"))
                    &&(reqPhysicians<=availableStaff.get("Physicians"))
                    &&(reqRPs<=availableStaff.get("Residents")))
            {return true;}
        }
        return false;
    }

    /**
     * Simulates treating a patient. Assumes that canTreatPatient has already been evaluated as true before call. This
     * creates a "release" event for the end of the treatment duration.
     * @param p the patient to treat
     */
    private void treat(Patient p){
        totalWaitTime+=deltaTime.minus(p.getArrivalTime()).toSeconds();
        avgWaitTime=totalWaitTime/totalERAdmissions;
        if(DETAILED_LOGGING){
            System.out.println(new String(new char[("EVENT "+eventsProcessed+" | TIME "+deltaTime.toString().substring(2)).length()]).replace('\0',' ')+" | Patient "+p.getName()+" begins treatment.");
        }
        er.occupyStaff("Nurses",config.getTriageNurseRequirements().get(p.getTriageLevel().name()));
        er.occupyStaff("Physicians",config.getTriagePhysicianRequirements().get(p.getTriageLevel().name()));
        er.occupyStaff("Residents",config.getTriageRPRequirements().get(p.getTriageLevel().name()));
        er.occupyTreatmentRoom();
        treatingPatients.add(p);
        eventList.add(new Event(deltaTime.plus(p.getTreatmentTime()),"release", p));
    }

    /**
     * Discharges a patient from the ER. If there are other patients that can be treated, sends the next highest
     * priority patient to treatment
     * @param p the patient being discharged
     */
    private void release(Patient p){
        patientsTreated++;
        totalTreatmentTime+=p.getTreatmentTime().toSeconds();
        avgTreatmentTime=totalTreatmentTime/patientsTreated;
        if(DETAILED_LOGGING){
            System.out.println("EVENT "+eventsProcessed+" | TIME "+deltaTime.toString().substring(2)+" | Patient "+p.getName()+" is discharged from the ER.");
        }
        er.freeStaff("Nurses",config.getTriageNurseRequirements().get(p.getTriageLevel().name()));
        er.freeStaff("Physicians",config.getTriagePhysicianRequirements().get(p.getTriageLevel().name()));
        er.freeStaff("Residents",config.getTriageRPRequirements().get(p.getTriageLevel().name()));
        er.freeTreatmentRoom();
        treatingPatients.remove(p);
        treatedPatients.add(p);
        if(!er.getWaitingPatients().isEmpty()){
            if(canTreatPatient(er.getWaitingPatients().peek())) {
                treat(er.getNextPatient());
            }
        }
    }

    /**
     * Retrieves the optimized schedule for a supercategory of staff roles (nurse, attending, or resident) using the LP
     * system with the parameters given in config.json.
     * @param duration the duration of the simulation
     * @param staffType the type of staff ("nurse", "physician", or "resident") to get a schedule for
     * @return an OptimizedScheduleOutput object containing the schedule requested
     */
    private OptimizedScheduleOutput getSchedule(Duration duration,String staffType){
        OptimizationInput input = OptimizationInput.builder()
                .staffMembers(genStaff())
                .demands(genDemands(duration))
                .lpShifts(new HashMap<>() {{
                    put("d8", new ShiftDefinition("d8", Shift.DAY_8H));
                    put("e8", new ShiftDefinition("e8", Shift.EVENING_8H));
                    put("n8", new ShiftDefinition("n8", Shift.NIGHT_8H));
                }})
                .maxHoursPerDay(config.getMaxHoursPerDay())
                .maxRegularHoursPerWeek(config.getMaxRegularHoursPerWeek())
                .maxTotalHoursPerWeek(config.getMaxTotalHoursPerWeek())
                .numDaysInPeriod((int)Math.ceil((double)duration.toHours()/24))
                .numWeeksInPeriod((int)Math.ceil((double)duration.toHours()/168))
                .build();
        switch (staffType){
            case "nurse" -> {return (new NurseScheduler()).optimizeNurseSchedule(input);}
            case "physician" -> {return (new PhysicianScheduler()).optimizePhysicianSchedule(input);}
            case "resident" -> {return (new ResidentPhysicianScheduler()).optimizeResidentSchedule(input);}
        }
        return null;
    }

    /**
     * Creates a list of staff members based on the role counts described in config.json's staffCounts variable.
     * @return the full list of all staff members.
     */
    private List<StaffMemberInterface> genStaff(){
        List<StaffMemberInterface> staff = new ArrayList<>();
        for(Map.Entry<String,Integer> role:config.getStaffCounts().entrySet()){
            for(int i = 0; i<role.getValue();i++) {
                if (Role.isNurseRole(Role.valueOf(role.getKey()))) {
                    UUID id = UUID.randomUUID();
                    staff.add(new Nurse(
                            id,
                            role.getKey()+id.toString(),
                            Role.valueOf(role.getKey()),
                            config.getHourlyWages().get(role.getKey()),
                            config.getOvertimeMultiplier()
                            )
                    );
                }
                if (Role.isPhysicianRole(Role.valueOf(role.getKey()))) {
                    UUID id = UUID.randomUUID();
                    staff.add(new Physician(
                                    id,
                                    role.getKey()+id.toString(),
                                    Role.valueOf(role.getKey()),
                                    config.getHourlyWages().get(role.getKey()),
                                    config.getOvertimeMultiplier()
                            )
                    );
                }
                if (Role.isResidentRole(Role.valueOf(role.getKey()))) {
                    UUID id = UUID.randomUUID();
                    staff.add(new ResidentPhysician(
                                    id,
                                    role.getKey()+id.toString(),
                                    Role.valueOf(role.getKey()),
                                    config.getHourlyWages().get(role.getKey()),
                                    config.getOvertimeMultiplier()
                            )
                    );
                }
            }
        }
        return staff;
    }

    /**
     * Creates the set of demands that the LP is bound by when considering ER needs and legal compliance. These needs
     * are based on the OregonStaffingRules class's interpretation of the ER described in config.json.
     * @param duration the duration of the simulation
     * @return a list of Demand objects describing the needs of the ER.
     */
    private List<Demand> genDemands(Duration duration){
        List<Demand> demands = new ArrayList<>();
        Map<Role,Integer> dayRequirements = OregonStaffingRules.getStaffRequirements(
                config.getEstTraumaPatientsDay(),
                config.getEstNonTraumaPatientsDay(),
                config.getCNARatio(),
                config.getLPNRatio());
        Map<Role,Integer> eveningRequirements = OregonStaffingRules.getStaffRequirements(
                config.getEstTraumaPatientsEvening(),
                config.getEstNonTraumaPatientsEvening(),
                config.getCNARatio(),
                config.getLPNRatio());
        Map<Role,Integer> nightRequirements = OregonStaffingRules.getStaffRequirements(
                config.getEstTraumaPatientsNight(),
                config.getEstNonTraumaPatientsNight(),
                config.getCNARatio(),
                config.getLPNRatio());
        int days = (int)Math.ceil((double)duration.toHours()/24);
        for (int dayIndex = 0; dayIndex < days; dayIndex++) {
            for (Map.Entry<Role,Integer> entry: dayRequirements.entrySet()
                 ) {
                demands.add(new Demand(entry.getKey(),dayIndex,"d8",entry.getValue()));
            }
            for (Map.Entry<Role,Integer> entry: eveningRequirements.entrySet()
            ) {
                demands.add(new Demand(entry.getKey(),dayIndex,"e8",entry.getValue()));
            }
            for (Map.Entry<Role,Integer> entry: nightRequirements.entrySet()
            ) {
                demands.add(new Demand(entry.getKey(),dayIndex,"n8",entry.getValue()));
            }
        }
        return demands;
    }

    /**
     * Records data from the last hour for GUI display.
     * @param hour the hour (as an integer count from the beginning of the simulation) to record data for
     */
    private void recordHourlyData(int hour) {
        if (hour < data[0].length) {
            data[0][hour] = hour;
            // ts not poisson so 0 lmk if it breaks smth - emre
            data[1][hour] = hourlyArrivals;
            data[2][hour] = er.getWaitingPatients().size();
            data[3][hour] = treatingPatients.size();
            data[4][hour] = er.getTreatmentRooms() - er.getOccupiedTreatmentRooms();
            data[5][hour] = (int)totalTreatmentTime;
            data[6][hour] = (int)avgTreatmentTime;
            data[7][hour] = (int)totalWaitTime;
            data[8][hour] = (int)avgWaitTime;
            data[9][hour] = totalArrivals;
        }
        logToCSV(hour);
        hourlyArrivals = 0;
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

    /**
     * Generates a random patient according to a set of probabilities regarding diagnoses with varying triage levels.
     * @return a random Patient object
     */
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

    /**
     * Generates a condition for a patient to present with, which can be later turned into a triage level.
     * @return
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
     * Adds the current contents of the data array to the output string to be written to CSV later
     * @param hour the current hour of the simulation
     */
    private void logToCSV(int hour){
        for (int[] datum:data
             ) {
            stringOutputData+=Integer.toString(datum[hour])+",";
        }
        stringOutputData=stringOutputData.substring(0,stringOutputData.length()-1)+"\n";
    }

    /**
     * Writes the contents of the output string to a file.
     * @throws FileNotFoundException if the
     */
    private void writeToCSV() throws FileNotFoundException {
        String filename = "log_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMHHmmss"))+".csv";
        File output = new File(filename);
        try (PrintWriter writer = new PrintWriter(output)) {
            writer.println(stringOutputData);
        }
        System.out.println("Printed output to "+filename);
    }
}
