package org.example.smo.model;

public final class Clock {
    private double currentTime;

    public Clock() {
        this(0.0);
    }

    public Clock(double initialTime) {
        validateTime(initialTime);
        this.currentTime = initialTime;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public void advanceTo(double time) {
        validateTime(time);

        if (time < currentTime) {
            throw new IllegalArgumentException("Model time cannot move backwards");
        }

        currentTime = time;
    }

    private static void validateTime(double time) {
        if (!Double.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Model time must be finite and non-negative");
        }
    }
}
