package myau.events;

import lombok.Getter;
import lombok.Setter;
import myau.event.events.Event;

@Setter
@Getter
public class PickEvent implements Event {
    private double range;

    public PickEvent(double double1) {
        this.range = double1;
    }

}
