import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import simulation.PoissonSimulator;
import simulation.Patient;
import spark.Spark;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private static PoissonSimulator lastSimulation = null;
    
    public static void main(String[] args) throws IOException {
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
            Map<String, Object> body = gson.fromJson(request.body(), Map.class);
            
            int days = 7;
            if (body != null && body.containsKey("days")) {
                days = ((Double) body.get("days")).intValue();
            }
            
            PoissonSimulator simulation = new PoissonSimulator();
            simulation.runSimulation(Duration.ofDays(days));
            
            lastSimulation = simulation;
            
            // Prepare response with simulation results
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("patientsProcessed", simulation.getTreatedPatients().size());
            result.put("patientsRejected", simulation.getRejectedPatients());
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
                lastSimulation.getRejectedPatients());
            result.put("patientsProcessed", lastSimulation.getTreatedPatients().size());
            result.put("patientsRejected", lastSimulation.getRejectedPatients());
            
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
    }
}