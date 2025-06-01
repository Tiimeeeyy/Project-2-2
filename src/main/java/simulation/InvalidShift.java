package simulation;

/**
 * Exception thrown when attempting to assign an invalid shift to a staff member.
 */
public class InvalidShift extends RuntimeException {
    /**
     * Constructs a new InvalidShift exception with the specified detail message.
     *
     * @param message Detailed message for the exception.
     */
    public InvalidShift(String message) {
        super(message);
    }
}
