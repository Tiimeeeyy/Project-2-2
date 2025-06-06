package simulation;

import lombok.Getter;

import java.time.Duration;
@Getter
public class Event implements Comparable<Event>{
    private final Duration time;
    private final String type;
    private final Patient patient;
    public Event(Duration startTime, String type, Patient patient){
        this.time = startTime;
        this.type = type;
        this.patient = patient;
    }

    @Override
    public int compareTo(Event o) {
        return this.time.compareTo(o.time);
    }
}
