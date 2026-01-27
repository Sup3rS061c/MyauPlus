package myau.events;

import lombok.Getter;
import lombok.Setter;
import myau.event.events.callables.EventCancellable;

@Setter
@Getter
public class SlowdownEvent extends EventCancellable {
    private boolean cancelled;
}