package com.jazzerator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RngTest {
    @Test
    void sameSeedSameSequence() {
        Rng a = new Rng(42);
        Rng b = new Rng(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.range(0, 1000), b.range(0, 1000));
        }
    }

    @Test
    void weightedPickNeverPicksZeroWeight() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("never", 0.0);
        weights.put("always", 1.0);
        Rng rng = new Rng(7);
        for (int i = 0; i < 1000; i++) {
            assertEquals("always", rng.weightedPick(weights));
        }
    }

    @Test
    void rangeIsInclusive() {
        Rng rng = new Rng(1);
        boolean sawMin = false, sawMax = false;
        for (int i = 0; i < 1000; i++) {
            int v = rng.range(3, 5);
            assertTrue(v >= 3 && v <= 5);
            sawMin |= v == 3;
            sawMax |= v == 5;
        }
        assertTrue(sawMin && sawMax);
    }
}
