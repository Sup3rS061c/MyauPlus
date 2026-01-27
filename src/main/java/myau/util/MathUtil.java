package myau.util;

import net.minecraft.util.Vec3;

public class MathUtil {

    public static Vec3 interpolate(Vec3 previousVec, Vec3 currentVec, float progress) {
        return new Vec3(
            previousVec.xCoord + (currentVec.xCoord - previousVec.xCoord) * progress,
            previousVec.yCoord + (currentVec.yCoord - previousVec.yCoord) * progress,
            previousVec.zCoord + (currentVec.zCoord - previousVec.zCoord) * progress
        );
    }

    public static double interpolate(double previousValue, double currentValue, double progress) {
        return previousValue + (currentValue - previousValue) * progress;
    }

    public static float interporate(float previousValue, float currentValue, float progress) {
        return previousValue + (currentValue - previousValue) * progress;
    }

    public static float normalize(double value, double min, double max) {
        return (float) ((value - min) / (max - min));
    }

    public static float clamp_float(float val, float min, float max) {
        if (val > max) {
            val = max;
        }
        if (val < min) {
            val = min;
        }
        return val;
    }

    public static double interporate(double previousValue, double currentValue, float progress) {
        return previousValue + (currentValue - previousValue) * progress;
    }

    public static double interporate(float progress, double previousValue, double currentValue) {
        return previousValue + (currentValue - previousValue) * progress;
    }
}