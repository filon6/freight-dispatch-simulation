package org.example.smo.random;

import java.util.Random;

public final class SeededRandomProvider implements RandomProvider {
    private final Random random;

    public SeededRandomProvider(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }
}
