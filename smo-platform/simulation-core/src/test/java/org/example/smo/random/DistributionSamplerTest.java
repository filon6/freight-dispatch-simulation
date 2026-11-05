package org.example.smo.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistributionSamplerTest {
    @Test
    void exponentialUsesCorrectFormula() {
        DistributionSampler sampler = new DistributionSampler(() -> 0.5);

        assertEquals(
            -Math.log(0.5) / 2.0,
            sampler.exponential(2.0),
            1e-12
        );
    }

    @Test
    void exponentialHandlesZeroAndRejectsBadValues() {
        double delay = new DistributionSampler(
            () -> 0.0
        ).exponential(1.0);

        assertTrue(Double.isFinite(delay));
        assertTrue(delay >= 0);

        for (double invalid : new double[]{
            -0.1, 1.0, Double.NaN, Double.POSITIVE_INFINITY
        }) {
            assertThrows(
                IllegalStateException.class,
                () -> new DistributionSampler(
                    () -> invalid
                ).exponential(1.0)
            );
        }
    }

    @Test
    void exponentialRejectsInvalidLambda() {
        DistributionSampler sampler = new DistributionSampler(() -> 0.5);

        for (double invalid : new double[]{
            0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY
        }) {
            assertThrows(
                IllegalArgumentException.class,
                () -> sampler.exponential(invalid)
            );
        }
    }

    @Test
    void distributionsHavePlausibleMeans() {
        DistributionSampler arrivals = new DistributionSampler(new SeededRandomProvider(42));
        DistributionSampler services = new DistributionSampler(new SeededRandomProvider(43));

        int count = 100_000;

        double arrivalSum = 0;
        double serviceSum = 0;

        for (int i = 0; i < count; i++) {
            arrivalSum += arrivals.exponential(0.5);
            serviceSum += services.uniform(2.0, 5.0);
        }

        assertEquals(2.0, arrivalSum / count, 0.05);
        assertEquals(3.5, serviceSum / count, 0.03);
    }
}
