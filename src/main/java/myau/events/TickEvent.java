package myau.events;

import lombok.Getter;
import myau.event.events.Event;
import myau.event.types.EventType;

@Getter
public class TickEvent implements Event {
    private final EventType type;

    public TickEvent(EventType type) {
        this.type = type;
    }

}
