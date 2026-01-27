package myau.events;

import lombok.Getter;
import lombok.Setter;
import myau.event.events.Event;

@Setter
@Getter
public class RaytraceEvent implements Event {
    private double range;

    public RaytraceEvent(double range) {
        this.range = range;
    }

}
