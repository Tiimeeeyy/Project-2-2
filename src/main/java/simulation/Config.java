package simulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.Map;

/**
 * Singleton class that parses the config.json file into a Config instance
 * so that the rest of the simulation can access configuration parameters.
 *
 * IMPORTANT: To change configuration values, edit config.json directly. Comments should be added here,
 * not in the JSON file.
 *
 * To add a new variable to config.json:
 *   • Add a private field to this class (e.g., private int foo;).
 *   • Add the same field name in the JSON (e.g., "foo": 42).
 *   • If the field name in JSON differs, use {@link JsonProperty} (e.g., @JsonProperty("foo")).
 *   • If the field is a custom type, ensure that its fields match the JSON structure.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = false)
public final class Config {
    private static Config instance;

    private int populationSize;

    @JsonProperty("ERName")
    private String ERName;

    private int patientMinAge;
    @JsonProperty("ERCapacity")
    private int ERCapacity;

    @JsonProperty("ERTreatmentRooms")
    private int ERTreatmentRooms;

    private int patientMaxAge;
    private int maxHoursPerDay;
    private int maxRegularHoursPerWeek;
    private int maxTotalHoursPerWeek;
    private double overtimeMultiplier;
    private String defaultArrivalFunction;
    private Map<String, String> patientArrivalFunctions;
    private Map<String, Integer> staffCounts;
    private Map<String, Double> triageNurseRequirements;
    private Map<String, Double> triageRPRequirements;
    private Map<String, Double> triagePhysicianRequirements;
    private Map<String, Double> hourlyWages;
    private Map<String, Double> avgTreatmentTimesMins;
    @JsonProperty("LPNRatio")
    private double LPNRatio;
    @JsonProperty("CNARatio")
    private double CNARatio;
    private int estTraumaPatientsDay;
    private int estTraumaPatientsEvening;
    private int estTraumaPatientsNight;
    private int estNonTraumaPatientsDay;
    private int estNonTraumaPatientsEvening;
    private int estNonTraumaPatientsNight;
    private boolean visualize;
    private boolean useRandomSchedule;

    // Private constructor to enforce singleton pattern
    private Config() {}

    /**
     * Retrieves the singleton instance of the configuration, loading
     * data from config.json if not already loaded.
     *
     * @return The Config singleton instance.
     * @throws IOException If the JSON file cannot be read or parsed.
     */
    public static Config getInstance() throws IOException {
        if (instance == null) {
            ObjectMapper mapper = new ObjectMapper();
            instance = mapper.readValue(
                Config.class.getResource("../config.json"),
                Config.class
            );
        }
        return instance;
    }
}
