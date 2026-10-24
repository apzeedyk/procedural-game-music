package com.jazzerator.util;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Single seeded source of randomness so every song is reproducible from its seed. */
public final class Rng {
    private final long seed;
    private final SplittableRandom random;

    public Rng(long seed) {
        this.seed = seed;
        this.random = new SplittableRandom(seed);
    }

    public long seed() {
        return seed;
    }

    /** Uniform int in [min, max] inclusive. */
    public int range(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    /** Picks a key with probability proportional to its weight. Iteration order must be stable (use LinkedHashMap). */
    public <T> T weightedPick(Map<T, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = random.nextDouble() * total;
        T last = null;
        for (Map.Entry<T, Double> e : weights.entrySet()) {
            last = e.getKey();
            r -= e.getValue();
            if (r < 0) {
                return last;
            }
        }
        return last;
    }
}
