import java.time.Duration;

public class Main {
    public static void main(String[] args) {
        PoissonSimulator simulation = new PoissonSimulator(150000);

        // Run the simulation for 7 days
        simulation.runSimulation(Duration.ofDays(7));
    }
}
