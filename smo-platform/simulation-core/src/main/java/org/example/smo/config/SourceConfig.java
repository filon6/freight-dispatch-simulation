package org.example.smo.config;

public record SourceConfig(
    int id,
    double lambda
) {
    public SourceConfig {
        if (id <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        if (!Double.isFinite(lambda) || lambda <= 0) {
            throw new IllegalArgumentException("Lambda must be a positive finite number");
        }
    }
}
