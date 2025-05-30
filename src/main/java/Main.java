import simulation.PoissonSimulator;

import java.io.IOException;
import java.time.Duration;
import org.mariuszgromada.math.mxparser.*;
public class Main {
    public static void main(String[] args) throws IOException {
        License.iConfirmNonCommercialUse("KEN12"); //for mxparser
        PoissonSimulator simulation = new PoissonSimulator();
        // Run the simulation for 7 days
        simulation.runSimulation(Duration.ofDays(7));
    }
}
