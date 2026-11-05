package org.example.smo.model;

import org.example.smo.random.DistributionSampler;
import org.example.smo.random.RandomProvider;

public final class ServiceTimeGenerator {

    private final double min;
    private final double max;
    private final DistributionSampler sampler;

    public ServiceTimeGenerator(
        double min,
        double max,
        RandomProvider random
    ) {
        if (!Double.isFinite(min)
            || !Double.isFinite(max)
            || min < 0
            || min > max) {
            throw new IllegalArgumentException("Invalid bounds");
        }

        this.min = min;
        this.max = max;
        this.sampler = new DistributionSampler(random);
    }

    public double sampleServiceTime() {
        return sampler.uniform(min, max);
    }
}
