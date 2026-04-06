package myau.util.skid.tenacity;

import java.awt.*;

public class Tenacity {
    public static final Tenacity INSTANCE = new Tenacity();

    public final Color getClientColor() {
        return new Color(236, 133, 209);
    }

    public final Color getAlternateClientColor() {
        return new Color(28, 167, 222);
    }
}
