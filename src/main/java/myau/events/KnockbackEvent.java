package myau.events;

import lombok.Getter;
import myau.event.events.callables.EventCancellable;

@Getter
public class KnockbackEvent extends EventCancellable {
    private double x;
    private double y;
    private double z;

    public KnockbackEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setX(double x) {
        this.x = x;
        this.setCancelled(true);
    }

    public void setY(double y) {
        this.y = y;
        this.setCancelled(true);
    }

    public void setZ(double z) {
        this.z = z;
        this.setCancelled(true);
    }
}
