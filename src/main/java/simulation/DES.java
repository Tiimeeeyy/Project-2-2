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
    private boolean useRandomSchedule;
    private int hourlyArrivals;

    public DES() throws IOException {
        this.stringOutputData = "Hour,Arrivals,Waiting,Treating,Available Rooms\n";
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
     * @param totalSimulationDuration the duration of the simulation.2
     */
// In src/main/java/simulation/DES.java

    public void start(Duration totalSimulationDuration) throws FileNotFoundException {
        // Define the cycle for scheduling and simulation
        Duration schedulingPeriod = Duration.ofDays(28);
        Duration totalTimeSimulated = Duration.ZERO;
        int cycleNumber = 1;

        // Initialize data collection for the total simulation duration
        int totalHours = (int) totalSimulationDuration.toHours();
        this.data = new int[5][totalHours];
        this.hourlyArrivals = 0;

        System.out.println("Starting simulation for a total of " + totalSimulationDuration.toDays() + " days, in " + schedulingPeriod.toDays() + "-day cycles.");

        // Main simulation loop
        while (totalTimeSimulated.compareTo(totalSimulationDuration) < 0) {
            System.out.println("\n--- Starting Simulation Cycle " + cycleNumber + " (Time: " + totalTimeSimulated.toDays() + " to " + (totalTimeSimulated.toDays() + schedulingPeriod.toDays()) + " days) ---");

            // 1. SCHEDULE STAFF for the upcoming 28-day period
            System.out.println("Generating optimal staff schedule for the next " + schedulingPeriod.toDays() + " days...");
            // The getSchedule method is now called with the fixed schedulingPeriod
            nurseSchedule = getSchedule(schedulingPeriod, "nurse");
            physicianSchedule = getSchedule(schedulingPeriod, "physician");
            residentSchedule = getSchedule(schedulingPeriod, "resident");

            // 2. GENERATE PATIENT ARRIVALS for the current cycle
            Duration cycleEndTime = totalTimeSimulated.plus(schedulingPeriod);
            System.out.println("Generating patient arrivals for the current cycle (until " + cycleEndTime + ")...");
            generateArrivalsForCycle(totalTimeSimulated, cycleEndTime);
            System.out.println(eventList.size() + " arrival events generated for this cycle.");

            // 3. RUN SIMULATION for the current cycle
            System.out.println("Processing events for cycle " + cycleNumber + "...");
            int currentHour = (int) deltaTime.toHours();

            // Process events only within the current cycle's time window
            while (!eventList.isEmpty() && eventList.peek().getTime().compareTo(cycleEndTime) < 0) {
                nextEvent(totalSimulationDuration); // The event itself sets the new deltaTime

                // Record hourly data as before
                int newHour = (int) deltaTime.toHours();
                if (newHour > currentHour && newHour < totalHours) {
                    recordHourlyData(newHour);
                    for(int h = currentHour + 1; h < newHour; h++) {
                        // Fill in any hours with no events
                        recordHourlyData(h);
                    }
                    currentHour = newHour;
                }
            }

            // Update total simulated time
            totalTimeSimulated = totalTimeSimulated.plus(schedulingPeriod);
            cycleNumber++;
            System.out.println("--- End of Simulation Cycle " + (cycleNumber - 1) + " ---");
        }

        // Final actions after all cycles are complete
        writeToCSV();
        System.out.println("\nSummary (" + totalSimulationDuration.toString().substring(2) + " duration):\n" + eventsProcessed + " events processed\n"
                + patientsTreated + " patients treated\n" + patientsRejected + " patients rejected");
    }
    // In src/main/java/simulation/DES.java

    /**
     * Clears the event list and generates new arrival events for a specific time period.
     * @param cycleStartTime The start time for the event generation window.
     * @param cycleEndTime The end time for the event generation window.
     */
    private void generateArrivalsForCycle(Duration cycleStartTime, Duration cycleEndTime) {
        eventList.clear();
        Duration currentTime = cycleStartTime;

        while (currentTime.compareTo(cycleEndTime) < 0) {
            // Calculate the arrival rate for the current hour
            t.setArgumentValue(currentTime.toHours());
            double currentInterarrivalTime = interarrivalTimeMins / arrivalExpression.calculate();

            // Generate time to next arrival
            ExponentialDistribution distribution = new ExponentialDistribution(currentInterarrivalTime);
            Duration timeToNextArrival = Duration.ofMinutes((long) Math.max(1, distribution.sample())); // Ensure at least 1 minute passes

            Duration newEventTime = currentTime.plus(timeToNextArrival);

            // Add the event only if it falls within the current cycle
            if (newEventTime.compareTo(cycleEndTime) < 0) {
                Patient p = generateRandomPatient(newEventTime); // Pass arrival time to patient
                eventList.add(new Event(newEventTime, "arrival", p));
            }
            currentTime = newEventTime;
        }
        // Sort events by time to ensure correct processing order
        eventList.sort(null);
    }
    /**
     * Generates an arrival event prior to the start of the simulation. Interarrival times follow a Poisson process,
     * drawing arrivals from an exponential distribution with the interarrivalTime variable as parameter.
     */
//    private void createArrival(){
//        Duration lastEventTime;
//        if (eventList.isEmpty()){
//            lastEventTime = Duration.ZERO;
//        } else {
//            lastEventTime = eventList.getLast().getTime();
//        }
//        t.setArgumentValue(+(int)Math.floor(lastEventTime.toHours()));
//        ExponentialDistribution distribution = new ExponentialDistribution(interarrivalTimeMins/arrivalExpression.calculate());
//        Patient p = generateRandomPatient();
//        eventList.add(new Event(Duration.ofMinutes((long)distribution.sample()).plus(lastEventTime), "arrival", p));
//    }

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
        hourlyArrivals++;
        if (DETAILED_LOGGING) {
            System.out.println("EVENT " + eventsProcessed + " | TIME " + deltaTime.toString().substring(2) + " | Patient " + p.getName() + " arrives needing " + p.getTriageLevel().getDescription().toLowerCase() + " care.");
        }
        if(er.addPatient(p)) {
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
        OptimizationInput input = SchedulingInputFactory.createInput(this.config, duration);
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
    
    public void setScenarioType(String arrivalFunctionName) {
        this.scenarioType = arrivalFunctionName;
        
        // Update the arrival expression with the new function
        try {
            Map<String, String> arrivalFunctions = config.getPatientArrivalFunctions();
            
            if (arrivalFunctions.containsKey(arrivalFunctionName)) {
                // Create a new expression with the selected arrival function
                String exprString = arrivalFunctions.get(arrivalFunctionName);
                this.arrivalExpression.setExpressionString(exprString);
                System.out.println("Updated arrival function to '" + arrivalFunctionName + "': f(t) = " + exprString);
            } else {
                System.out.println("Warning: Arrival function '" + arrivalFunctionName + "' not found in config. Using default.");
            }
        } catch (Exception e) {
            System.out.println("Error updating arrival function: " + e.getMessage());
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
    private Patient generateRandomPatient(Duration arrivalTime) {
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
        return new Patient(id, name, age, triageLevel, arrivalTime, treatmentTime);
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
