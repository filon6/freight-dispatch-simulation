package org.example.smo.random;

public final class RandomStreams {
    private final long seed;

    public RandomStreams(long seed) {
        this.seed = seed;
    }

    public RandomProvider forSource(int sourceId) {
        return new SeededRandomProvider(
            derive(0x5352435F524E4753L, sourceId)
        );
    }

    public RandomProvider forProcessor(int processorId) {
        return new SeededRandomProvider(
            derive(0x5052435F524E4753L, processorId)
        );
    }

    private long derive(long domain, int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid stream ID");
        }

        long z = seed ^ domain ^ (Integer.toUnsignedLong(id) * 0x9E3779B97F4A7C15L);

        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;

        return z ^ (z >>> 31);
    }
}
