package myau.events;

import lombok.Getter;
import lombok.Setter;
import myau.event.events.Event;

@Setter
@Getter
public class SafeWalkEvent implements Event {
    private boolean safeWalk;

    public SafeWalkEvent(boolean safeWalk) {
        this.safeWalk = safeWalk;
    }

}
