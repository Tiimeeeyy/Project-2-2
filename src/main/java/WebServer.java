import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import simulation.Config;
import simulation.DES;
import simulation.Patient;
import spark.Spark;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mariuszgromada.math.mxparser.License;

public class WebServer {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private static DES lastSimulation = null;
    
    public static void main(String[] args) throws IOException {
        License.iConfirmNonCommercialUse("ken12");
        Spark.port(8080);
        Spark.staticFileLocation("/public");
        configureCORS();
        defineEndpoints();
        
        System.out.println("Server started at http://localhost:8080");
    }

    private static void configureCORS() {
        Spark.options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }
            
            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            
            return "OK";
        });
        
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Content-Length, Accept, Origin");
            response.type("application/json");
        });
    }

    private static void defineEndpoints() throws IOException {
        Spark.get("/api/hello", (request, response) -> {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Hello from ER Simulation API!");
            return gson.toJson(result);
        });
        
        // Run simulation
        Spark.post("/api/simulation/run", (request, response) -> {
            Map<String, Object> body = gson.fromJson(request.body(), new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
            
            int days = 1;
            Map<String, Object> hyperparameters = new HashMap<>();
            String triageLevel = null;
            String arrivalFunction = "sinusoidal_24h"; // Default from config
            String triageClassifier = "CTAS";
            
            if (body != null) {
                if (body.containsKey("days")) {
                    days = ((Double) body.get("days")).intValue();
                }
                if (body.containsKey("hyperparameters")) {
                    Object hyperparamObj = body.get("hyperparameters");
                    if (hyperparamObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hyperparamMap = (Map<String, Object>) hyperparamObj;
                        hyperparameters = hyperparamMap;
                    }
                }
                if (body.containsKey("triageLevel")) {
                    triageLevel = (String) body.get("triageLevel");
                }
                if (body.containsKey("arrivalFunction")) {
                    arrivalFunction = (String) body.get("arrivalFunction");
                }
                // Keep backward compatibility with old "scenario" parameter
                if (body.containsKey("scenario")) {
                    arrivalFunction = (String) body.get("scenario");
                }
                if (body.containsKey("triageClassifier")) {
                    triageClassifier = (String) body.get("triageClassifier");
                }
            }
            
            DES simulation = new DES();
            
            // Configure simulation based on parameters
            if (!hyperparameters.isEmpty()) {
                simulation.setHyperparameters(hyperparameters);
            }
            
            if (triageLevel != null && !triageLevel.isEmpty()) {
                try {
                    Patient.TriageLevel triage = Patient.TriageLevel.valueOf(triageLevel.toUpperCase());
                    simulation.setFocusTriageLevel(triage);
                } catch (IllegalArgumentException e) {
                    // Invalid triage level, ignore
                }
            }
            
            simulation.setScenarioType(arrivalFunction);
            simulation.setTriageClassifier(triageClassifier);
            simulation.start(Duration.ofDays(days));
            
            lastSimulation = simulation;
            
            // Prepare response with simulation results
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("patientsProcessed", simulation.getTreatedPatients().size());
            result.put("patientsRejected", simulation.getPatientsRejected());
            result.put("simulationTime", days);
            result.put("hasChartData", true);
            
            return gson.toJson(result);
        });
        
        // Get chart data
        Spark.get("/api/simulation/chartdata", (request, response) -> {
            if (lastSimulation == null) {
                return gson.toJson(Map.of("error", "No simulation data available. Run a simulation first."));
            }
            
            int[][] data = lastSimulation.getData();
            
            // Transform to a format better for JavaScript charting
            Map<String, Object> result = new HashMap<>();
            List<Integer> hours = new ArrayList<>();
            List<Integer> arrivals = new ArrayList<>();
            List<Integer> waiting = new ArrayList<>();
            List<Integer> treating = new ArrayList<>();
            List<Integer> openRooms = new ArrayList<>();
            
            for (int i = 0; i < data[0].length; i++) {
                hours.add(data[0][i]);
                arrivals.add(data[1][i]);
                waiting.add(data[2][i]);
                treating.add(data[3][i]);
                openRooms.add(data[4][i]);
            }
            
            result.put("hours", hours);
            result.put("arrivals", arrivals);
            result.put("waiting", waiting);
            result.put("treating", treating);
            result.put("openRooms", openRooms);
            
            return gson.toJson(result);
        });
        
        // Get detailed simulation data
        Spark.get("/api/simulation/data", (request, response) -> {
            if (lastSimulation == null) {
                return gson.toJson(Map.of("error", "No simulation data available. Run a simulation first."));
            }
            
            // Prepare complete statistics about the simulation
            Map<String, Object> result = new HashMap<>();
            result.put("totalPatients", 
                lastSimulation.getTreatedPatients().size() + 
                lastSimulation.getTreatingPatients().size() + 
                lastSimulation.getPatientsRejected());
            result.put("patientsProcessed", lastSimulation.getTreatedPatients().size());
            result.put("patientsRejected", lastSimulation.getPatientsRejected());
            
            return gson.toJson(result);
        });
        
        // Add additional endpoints for patient statistics
        Spark.get("/api/patients/triage", (request, response) -> {
            if (lastSimulation == null) {
                return gson.toJson(Map.of("error", "No simulation data available. Run a simulation first."));
            }
            
            // Calculate actual triage counts from the simulation data
            Map<String, Integer> triageCounts = new HashMap<>();
            triageCounts.put("RED", 0);
            triageCounts.put("ORANGE", 0);
            triageCounts.put("YELLOW", 0);
            triageCounts.put("GREEN", 0);
            triageCounts.put("BLUE", 0);
            
            // Count triage levels in treated patients
            for (Patient patient : lastSimulation.getTreatedPatients()) {
                String triage = patient.getTriageLevel().name();
                triageCounts.put(triage, triageCounts.getOrDefault(triage, 0) + 1);
            }
            
            // Count triage levels in treating patients
            for (Patient patient : lastSimulation.getTreatingPatients()) {
                String triage = patient.getTriageLevel().name();
                triageCounts.put(triage, triageCounts.getOrDefault(triage, 0) + 1);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("triageCounts", triageCounts);
            return gson.toJson(result);
        });
        
        // New endpoints for configuration
        Spark.get("/api/config/hyperparameters", (request, response) -> {
            Map<String, Object> defaultParams = new HashMap<>();
            defaultParams.put("interarrivalTime", 15.0);
            defaultParams.put("treatmentCapacity", 15);
            defaultParams.put("waitingCapacity", 30);
            return gson.toJson(defaultParams);
        });
        
        Spark.get("/api/config/triage-levels", (request, response) -> {
            List<Map<String, String>> triageLevels = new ArrayList<>();
            for (Patient.TriageLevel level : Patient.TriageLevel.values()) {
                Map<String, String> levelInfo = new HashMap<>();
                levelInfo.put("value", level.name());
                levelInfo.put("label", level.getDescription());
                triageLevels.add(levelInfo);
            }
            return gson.toJson(triageLevels);
        });
        
        Spark.get("/api/config/triage-classifiers", (request, response) -> {
            List<Map<String, String>> classifiers = new ArrayList<>();
            
            Map<String, String> ctas = new HashMap<>();
            ctas.put("value", "CTAS");
            ctas.put("label", "Canadian Triage and Acuity Scale (CTAS)");
            ctas.put("description", "5-level triage system used in Canada");
            classifiers.add(ctas);
            
            Map<String, String> esi = new HashMap<>();
            esi.put("value", "ESI");
            esi.put("label", "Emergency Severity Index (ESI)");
            esi.put("description", "5-level triage system used in the United States");
            classifiers.add(esi);
            
            Map<String, String> mts = new HashMap<>();
            mts.put("value", "MTS");
            mts.put("label", "Manchester Triage System (MTS)");
            mts.put("description", "5-level triage system used in the United Kingdom");
            classifiers.add(mts);
            
            return gson.toJson(classifiers);
        });
        
        Spark.get("/api/config/scenarios", (request, response) -> {
            List<Map<String, String>> scenarios = new ArrayList<>();
            
            try {
                Config config = Config.getInstance();
                Map<String, String> arrivalFunctions = config.getPatientArrivalFunctions();
                
                for (Map.Entry<String, String> entry : arrivalFunctions.entrySet()) {
                    Map<String, String> scenario = new HashMap<>();
                    scenario.put("value", entry.getKey());
                    scenario.put("label", formatScenarioLabel(entry.getKey()));
                    scenario.put("description", entry.getValue());
                    scenarios.add(scenario);
                }
            } catch (IOException e) {
                // Fallback to default scenarios if config can't be loaded
                Map<String, String> regular = new HashMap<>();
                regular.put("value", "sinusoidal_24h");
                regular.put("label", "Regular Operation");
                regular.put("description", "(-0.25)*cos((pi/12)*t)+0.75");
                scenarios.add(regular);
                
                Map<String, String> emergency = new HashMap<>();
                emergency.put("value", "constant_2x");
                emergency.put("label", "Emergency Scenario");
                emergency.put("description", "2");
                scenarios.add(emergency);
            }
            
            return gson.toJson(scenarios);
        });
        
        // Get utility statistics
        Spark.get("/api/simulation/utilities", (request, response) -> {
            if (lastSimulation == null) {
                return gson.toJson(Map.of("error", "No simulation data available. Run a simulation first."));
            }
            
            Map<String, Object> utilities = new HashMap<>();
            
            // Calculate room utilization
            int[][] data = lastSimulation.getData();
            if (data != null && data.length > 4) {
                double avgUtilization = 0.0;
                int totalRooms = 15; // Default from config
                
                for (int i = 0; i < data[3].length; i++) {
                    int treatingPatients = data[3][i];
                    avgUtilization += (double) treatingPatients / totalRooms;
                }
                avgUtilization = avgUtilization / data[3].length * 100;
                
                utilities.put("roomUtilization", Math.round(avgUtilization * 100.0) / 100.0);
            }
            
            // Calculate throughput
            int totalPatients = lastSimulation.getTreatedPatients().size() + lastSimulation.getPatientsRejected();
            double throughput = totalPatients > 0 ? 
                (double) lastSimulation.getTreatedPatients().size() / totalPatients * 100 : 0;
            utilities.put("throughput", Math.round(throughput * 100.0) / 100.0);
            
            // Calculate rejection rate
            double rejectionRate = totalPatients > 0 ? 
                (double) lastSimulation.getPatientsRejected() / totalPatients * 100 : 0;
            utilities.put("rejectionRate", Math.round(rejectionRate * 100.0) / 100.0);
            
            return gson.toJson(utilities);
        });

        // Get staff statistics
        Spark.get("/api/simulation/staff-statistics", (request, response) -> {
            if (lastSimulation == null) {
                return gson.toJson(Map.of("error", "No simulation data available. Run a simulation first."));
            }
            
            Map<String, Object> staffStats = new HashMap<>();
            
            // Mock staff statistics based on simulation data
            int[][] data = lastSimulation.getData();
            if (data != null && data.length > 4) {
                // Calculate average utilization metrics
                double avgTreatingPatients = 0.0;
                double avgWaitingPatients = 0.0;
                
                for (int i = 0; i < data[3].length; i++) {
                    avgTreatingPatients += data[3][i];
                    avgWaitingPatients += data[2][i];
                }
                
                if (data[3].length > 0) {
                    avgTreatingPatients = avgTreatingPatients / data[3].length;
                    avgWaitingPatients = avgWaitingPatients / data[2].length;
                }
                
                // Mock physician statistics based on patient load
                Map<String, Object> physicianStats = new HashMap<>();
                int totalPhysicians = 8; // Mock value
                physicianStats.put("totalStaff", totalPhysicians);
                physicianStats.put("activeStaff", Math.max(1, (int)(avgTreatingPatients * 0.6))); // Assume 60% of treating patients need physicians
                physicianStats.put("utilization", Math.min(100, Math.round(avgTreatingPatients / totalPhysicians * 100 * 100.0) / 100.0));
                physicianStats.put("avgPatientsPerShift", Math.round(avgTreatingPatients / Math.max(1, totalPhysicians) * 100.0) / 100.0);
                
                // Mock nurse statistics
                Map<String, Object> nurseStats = new HashMap<>();
                int totalNurses = 15; // Mock value
                nurseStats.put("totalStaff", totalNurses);
                nurseStats.put("activeStaff", Math.max(1, (int)(avgTreatingPatients * 0.8))); // Assume 80% of treating patients need nurses
                nurseStats.put("utilization", Math.min(100, Math.round(avgTreatingPatients / totalNurses * 100 * 100.0) / 100.0));
                nurseStats.put("avgPatientsPerShift", Math.round(avgTreatingPatients / Math.max(1, totalNurses) * 100.0) / 100.0);
                
                // Mock resident statistics
                Map<String, Object> residentStats = new HashMap<>();
                int totalResidents = 4; // Mock value
                residentStats.put("totalStaff", totalResidents);
                residentStats.put("activeStaff", Math.max(1, (int)(avgTreatingPatients * 0.3))); // Assume 30% of treating patients involve residents
                residentStats.put("utilization", Math.min(100, Math.round(avgTreatingPatients / totalResidents * 100 * 100.0) / 100.0));
                residentStats.put("avgPatientsPerShift", Math.round(avgTreatingPatients / Math.max(1, totalResidents) * 100.0) / 100.0);
                
                // Mock admin staff statistics
                Map<String, Object> adminStats = new HashMap<>();
                int totalAdmin = 3; // Mock value
                adminStats.put("totalStaff", totalAdmin);
                adminStats.put("activeStaff", totalAdmin); // Admin staff typically always active
                adminStats.put("utilization", Math.min(100, Math.round((avgWaitingPatients + avgTreatingPatients) / 20 * 100 * 100.0) / 100.0)); // Based on total patient load
                adminStats.put("avgPatientsProcessed", Math.round((avgWaitingPatients + avgTreatingPatients) / Math.max(1, totalAdmin) * 100.0) / 100.0);
                
                staffStats.put("physicians", physicianStats);
                staffStats.put("nurses", nurseStats);
                staffStats.put("residents", residentStats);
                staffStats.put("administration", adminStats);
                
                // Overall statistics
                Map<String, Object> overallStats = new HashMap<>();
                overallStats.put("totalStaff", totalPhysicians + totalNurses + totalResidents + totalAdmin);
                overallStats.put("averageUtilization", Math.round(((Double)physicianStats.get("utilization") + 
                    (Double)nurseStats.get("utilization") + 
                    (Double)residentStats.get("utilization") + 
                    (Double)adminStats.get("utilization")) / 4 * 100.0) / 100.0);
                overallStats.put("patientToStaffRatio", Math.round(avgTreatingPatients / (totalPhysicians + totalNurses) * 100.0) / 100.0);
                
                staffStats.put("overall", overallStats);
            }
            
            return gson.toJson(staffStats);
        });
    }
    
    private static String formatScenarioLabel(String key) {
        // Convert snake_case to Title Case
        String[] parts = key.split("_");
        StringBuilder formatted = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                formatted.append(" ");
            }
            String part = parts[i];
            if (part.length() > 0) {
                formatted.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    formatted.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return formatted.toString();
    }
}