package myau.util.animation;

import myau.util.Animation;

public class DecelerateAnimation implements Animation {
    private final int duration;
    private final double min;
    private long startTime;
    private Direction direction;

    public DecelerateAnimation(int duration, double min) {
        this.duration = duration;
        this.min = min;
        this.startTime = System.currentTimeMillis();
        this.direction = Direction.FORWARDS;
    }

    @Override
    public void reset() {
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    @Override
    public boolean isDone() {
        return getOutput() >= 1.0 && direction == Direction.FORWARDS;
    }

    @Override
    public double getOutput() {
        if (direction == Direction.FORWARDS) {
            double progress = (double) (System.currentTimeMillis() - startTime) / duration;
            progress = Math.min(1.0, progress);
            return 1.0 - Math.pow(1.0 - progress, 2);
        } else {
            double progress = 1.0 - (double) (System.currentTimeMillis() - startTime) / duration;
            progress = Math.max(0.0, progress);
            return Math.pow(progress, 2);
        }
    }
}