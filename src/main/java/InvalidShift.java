/**
 * Custom exception to be thrown, when a given shift cant be added to the staff users schedule.
 */
public class InvalidShift extends RuntimeException {
    public InvalidShift(String message) {
        super(message);
    }
}
