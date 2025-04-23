import java.io.IOException;
import java.time.Duration;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class Main {
    public static void main(String[] args) throws IOException {
        PoissonSimulator simulation = new PoissonSimulator();
        // Run the simulation for 7 days
        simulation.runSimulation(Duration.ofDays(7));
    }
}
