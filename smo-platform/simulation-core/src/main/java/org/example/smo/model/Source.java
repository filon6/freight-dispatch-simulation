package org.example.smo.model;

import java.util.Objects;
import java.util.Random;

public class Source {
    private final int id;
    private final String channelName;
    private final double lambda;
    private final Random random;

    public Source(int id, String channelName, double lambda, Random random) {
        if (id <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("Channel name must not be empty");
        }

        if (!Double.isFinite(lambda) || lambda <= 0) {
            throw new IllegalArgumentException("Lambda must be a positive finite number");
        }

        this.id = id;
        this.channelName = channelName;
        this.lambda = lambda;
        this.random = Objects.requireNonNull(random, "Random generator must not be null");
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
        double u = 1.0 - random.nextDouble();
        double delay = -Math.log(u) / lambda;

        if (!Double.isFinite(delay)) {
            throw new IllegalStateException("Generated arrival delay is not finite");
        }

        return delay;
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
