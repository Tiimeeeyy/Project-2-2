import java.io.FileNotFoundException;
import java.io.IOException;

import org.mariuszgromada.math.mxparser.License;
import simulation.DES;

import java.time.Duration;

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
        //PoissonSimulator simulation = new PoissonSimulator();

        // Run the simulation for 7 days
        //simulation.runSimulation(Duration.ofDays(7));
        repeat(10,Duration.ofDays(100));
    }

    public static void repeat(int iterations, Duration duration) throws IOException {
        for (int i = 0; i < iterations; i++) {
            run(duration);
        }
    }

    public static void run(Duration duration) throws IOException {
        DES simulation = new DES();
        simulation.start(duration);
    }
}
