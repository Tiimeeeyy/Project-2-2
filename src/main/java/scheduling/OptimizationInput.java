package scheduling;

import lombok.*;
import staff.Demand;
import staff.ShiftDefinition;
import staff.StaffMemberInterface;

import java.util.List;
import java.util.Map;

/**
 * Contains all the necessary input data for the staff scheduling optimization process.
 * This includes staff members, shift definitions for the LP, demand requirements,
 * and global parameters for the optimization period and constraints.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder // Using Lombok's Builder pattern for convenient and readable instantiation
public class OptimizationInput {

    /**
     * List of staff members available for scheduling.
     * Each staff member implements {@link StaffMemberInterface} and contains their role, wage info, etc.
     * This corresponds to the set N in the LP.
     */
    @Singular // For adding individual staff members to the list with the builder
    private final List<StaffMemberInterface> staffMembers;

    /**
     * A map of LP shift definitions, where the key is the lpShiftId (e.g., "Ds", "Dl", "F")
     * and the value is the {@link ShiftDefinition} object.
     * This represents the set S from the LP, along with their properties (ShiftLength_s).
     */
    @Singular("putLpShift") // For adding individual shift definitions to the map with the builder
    private final Map<String, ShiftDefinition> lpShifts;

    /**
     * List of staffing demands ({@link Demand}) for various roles, days, and LP shifts.
     * This provides the RequiredNurses_qsd (or RequiredStaff_rsd) values for the LP.
     */
    @Singular // For adding individual demands to the list with the builder
    private final List<Demand> demands;

    /**
     * The total number of days in the planning period (e.g., 28 for a 4-week period).
     * This defines the scope of 'd' in the LP.
     */
    private final int numDaysInPeriod;

    /**
     * The total number of weeks in the planning period (e.g., 4).
     * This defines the scope of 'w' in the LP.
     */
    private final int numWeeksInPeriod;

    /**
     * Maximum number of regular hours a staff member can work per week before overtime applies (e.g., 40).
     * Used in constraint (4) of the LP: TotalRegHours_nw <= 40.
     */
    private final int maxRegularHoursPerWeek;

    /**
     * Maximum total hours (regular + overtime) a staff member can work per week (e.g., 48).
     * From LP text MaxHours_n, used in constraint (6): TotalActualHours_nw <= 48.
     * This should also align with Oregon labor laws (max 48 required hours/week for nurses).
     */
    private final int maxTotalHoursPerWeek;

    /**
     * Maximum total hours a staff member can work per day (e.g., 12).
     * Used in constraint (7) of the LP: Sum(X_nsd * ShiftLength_s) <= 12 for each day.
     * Aligns with Oregon labor laws (max 12 required hours in 24h for nurses).
     */
    private final int maxHoursPerDay;

    // Constructor is private when using @Builder.
    // Lombok will generate a constructor that takes an instance of OptimizationInputBuilder.
    // If you need a public constructor for some reason, you can add it manually or use @AllArgsConstructor.
    // For now, relying on the builder.
}
