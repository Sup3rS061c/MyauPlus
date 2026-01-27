package myau.events;

import lombok.Getter;
import myau.event.events.Event;

@Getter
public class Render2DEvent implements Event {
    private final float partialTicks;

    public Render2DEvent(float float1) {
        this.partialTicks = float1;
    }

}
