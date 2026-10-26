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

    /**
     * An independent stream for one named decision, e.g. {@code fork("suspension", tick)}. Keys should name
     * <em>what</em> is decided and <em>where</em> (a musical position), never how many decisions came before, so
     * that changing one decision can't shift the dice for any other. Same seed and key always give the same stream.
     */
    public Rng fork(Object... key) {
        StringBuilder sb = new StringBuilder();
        for (Object part : key) {
            sb.append(part).append('\u001f');
        }
        return new Rng(mix(seed ^ fnv1a(sb.toString())));
    }

    /** FNV-1a 64-bit: a stable string hash (String.hashCode is only 32 bits). */
    private static long fnv1a(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** SplitMix64 finalizer, so nearby seeds and keys give unrelated streams. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
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
