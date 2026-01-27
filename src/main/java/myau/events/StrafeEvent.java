package myau.events;

import lombok.Getter;
import lombok.Setter;
import myau.event.events.Event;

@Setter
@Getter
public class StrafeEvent implements Event {
    private float strafe;
    private float forward;
    private float friction;

    public StrafeEvent(float strafe, float forward, float friction) {
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
    }

}
