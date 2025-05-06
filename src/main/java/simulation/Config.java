package simulation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.Map;

/**
 * This parses the config file into the simulation.Config instance to be read by the rest of the program.
 *
 * IMPORTANT: To change the config, edit config.json! This is just its POJO representation. You can't put comments in a JSON
 * though, so put 'em here.
 *
 * To add a new variable to config.json:
 *      add it to this class, e.g. private int foo
 *      add it to the JSON using the same variable name, in quotes e.g. "foo": 42
 *      if it doesn't recognize it, add the JsonProperty tag, e.g. @JsonProperty("foo")
 *      if one of your variables is a custom type/class, just make sure the variables in that class are formatted the same way
 */
@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = false) //turn to true to stop warning about unfamiliar stuff in the JSON
public final class Config {
    private static Config _instance;
    private int populationSize;
    @JsonProperty("ERName")
    private String ERName;
    private int patientMinAge;
    @JsonProperty("ERCapacity")
    private int ERCapacity;
    @JsonProperty("ERTreatmentRooms")
    private int ERTreatmentRooms;
    private int patientMaxAge;
    private String defaultArrivalFunction;
    private PatientService[] patientServices;
    private Map<String,String> patientArrivalFunctions;
    private boolean visualize;
    private Config() {}
    public static Config getInstance() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if(_instance==null) {
            //instead of instantiating with 'new', we use Jackson to instantiate from the json
            _instance = mapper.readValue(Config.class.getResource("../config.json"), Config.class);
            //that way the config can build its own interpreter, so we don't need a new class
        }
        return _instance;
    }
}
