package org.example.smo.model;

import org.example.smo.random.DistributionSampler;
import org.example.smo.random.RandomProvider;

import java.util.Objects;
import java.util.Random;

public class Source {
    private final int id;
    private final String channelName;
    private final double lambda;
    private final DistributionSampler sampler;

    public Source(
        int id,
        String channelName,
        double lambda,
        RandomProvider random
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid source ID");
        }

        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("Invalid channel name");
        }

        if (!Double.isFinite(lambda) || lambda <= 0) {
            throw new IllegalArgumentException("Invalid lambda");
        }

        this.id = id;
        this.channelName = channelName;
        this.lambda = lambda;
        this.sampler = new DistributionSampler(random);
    }

    @Deprecated
    public Source(
        int id,
        String channelName,
        double lambda,
        Random random
    ) {
        this(
            id,
            channelName,
            lambda,
            (RandomProvider) Objects.requireNonNull(random)::nextDouble
        );
    }

    public Request generateRequest(long requestId, double currentTime) {
        return new Request(
            requestId,
            id,
            currentTime,
            "Cargo",
            "Origin",
            "Destination"
        );
    }

    public double sampleNextArrivalDelay() {
        return sampler.exponential(lambda);
    }

    public double nextArrivalTime(double previousArrivalTime) {
        if (!Double.isFinite(previousArrivalTime)
            || previousArrivalTime < 0) {
            throw new IllegalArgumentException("Invalid time");
        }

        double next = previousArrivalTime + sampleNextArrivalDelay();

        if (!Double.isFinite(next)) {
            throw new IllegalStateException("Invalid next arrival time");
        }

        return next;
    }

    public int getId() {
        return id;
    }

    public String getChannelName() {
        return channelName;
    }

    public double getLambda() {
        return lambda;
    }
}
