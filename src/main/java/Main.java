import simulation.PoissonSimulator;

import java.io.IOException;
import java.time.Duration;
import org.mariuszgromada.math.mxparser.License;

/**
 * Entry point for running the Emergency Room Poisson simulation.
 */
public class Main {

    /**
     * Main method to initialize and run the PoissonSimulator.
     *
     * @param args Command-line arguments (unused).
     * @throws IOException If configuration loading fails.
     */
    public static void main(String[] args) throws IOException {
        // Confirm non-commercial use for mxparser library
        License.iConfirmNonCommercialUse("KEN12");

        // Instantiate simulator using configuration values
        PoissonSimulator simulation = new PoissonSimulator();

        // Run the simulation for 7 days
        simulation.runSimulation(Duration.ofDays(7));
    }
}
