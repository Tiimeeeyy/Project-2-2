package scheduling;

import lombok.Getter;

/**
 * A simple data class to hold key performance indicators (KPIs) from a simulation period.
 * This object is used to provide feedback to the scheduler for the next period.
 */
@Getter
public class PerformanceMetrics {
    private final double rejectionRate; // Percentage of patients rejected
    private final double averageWaitingTimeMins; // Average time patients wait

    public PerformanceMetrics(double rejectionRate, double averageWaitingTimeMins) {
        this.rejectionRate = rejectionRate;
        this.averageWaitingTimeMins = averageWaitingTimeMins;
    }
}
