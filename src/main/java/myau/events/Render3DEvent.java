package myau.events;

import lombok.Getter;
import myau.event.events.Event;

@Getter
public class Render3DEvent implements Event {
    private final float partialTicks;

    public Render3DEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

}
