package myau.events;

import lombok.Getter;
import myau.event.events.Event;
import myau.event.types.EventType;

public class UpdateEvent implements Event {
    @Getter
    private final EventType type;
    @Getter
    private final float yaw;
    @Getter
    private final float pitch;
    @Getter
    private float newYaw;
    @Getter
    private float newPitch;
    private float prevYaw;
    private int lastPriority = -1;
    private int priority = -1;
    @Getter
    private boolean rotated = false;

    public UpdateEvent(EventType type, float yaw, float pitch, float newYaw, float newPitch) {
        this.type = type;
        this.yaw = yaw;
        this.pitch = pitch;
        this.newYaw = newYaw;
        this.newPitch = newPitch;
        this.prevYaw = newYaw;
    }

    public float getPreYaw() {
        return this.prevYaw;
    }

    public int isRotating() {
        return this.priority;
    }

    public void setRotation(float yaw, float pitch, int priority) {
        if (this.type == EventType.PRE && this.lastPriority <= priority) {
            this.newYaw = yaw;
            this.newPitch = pitch;
            this.lastPriority = priority;
            this.rotated = true;
        }
    }

    public void setPervRotation(float yaw, int priority) {
        if (this.type == EventType.PRE && this.priority < priority) {
            this.prevYaw = yaw;
            this.priority = priority;
            this.rotated = true;
        }
    }
}
