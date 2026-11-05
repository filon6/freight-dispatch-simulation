package org.example.smo.random;

import java.util.Objects;

public final class DistributionSampler {

    private final RandomProvider random;

    public DistributionSampler(RandomProvider random) {
        this.random = Objects.requireNonNull(random);
    }

    // Экспоненциальное распределение для интервалов поступления
    public double exponential(double lambda) {
        if (!Double.isFinite(lambda) || lambda <= 0) {
            throw new IllegalArgumentException("Invalid lambda");
        }

        double u = nextUnitDouble();

        // Исключаем ноль, чтобы не вычислять ln(0)
        u = Math.max(u, Double.MIN_VALUE);

        double delay = -Math.log(u) / lambda;

        if (!Double.isFinite(delay) || delay < 0) {
            throw new IllegalStateException("Invalid arrival delay");
        }

        return delay;
    }

    // Равномерное распределение для времени обслуживания
    public double uniform(double min, double max) {
        if (!Double.isFinite(min)
            || !Double.isFinite(max)
            || min < 0
            || min > max) {
            throw new IllegalArgumentException("Invalid bounds");
        }

        if (min == max) {
            return min;
        }

        double u = nextUnitDouble();
        double result = min + (max - min) * u;

        return Math.max(min, Math.min(max, result));
    }

    private double nextUnitDouble() {
        double u = random.nextDouble();

        if (!Double.isFinite(u) || u < 0 || u >= 1) {
            throw new IllegalStateException("Invalid random value");
        }

        return u;
    }
}
