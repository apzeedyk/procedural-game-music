package com.jazzerator.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable weighted choice list with stable iteration order (needed for seed reproducibility). */
public final class Weighted<T> {
    private final Map<T, Double> weights;

    private Weighted(Map<T, Double> weights) {
        this.weights = Collections.unmodifiableMap(weights);
    }

    public static <T> Weighted<T> of(T item, double weight) {
        return new Weighted<T>(new LinkedHashMap<>()).and(item, weight);
    }

    @SafeVarargs
    public static <T> Weighted<T> uniform(T... items) {
        Map<T, Double> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(item, 1.0);
        }
        return new Weighted<>(map);
    }

    public Weighted<T> and(T item, double weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("Negative weight for " + item);
        }
        Map<T, Double> copy = new LinkedHashMap<>(weights);
        copy.put(item, weight);
        return new Weighted<>(copy);
    }

    /** The items that pass the test, with their weights; null if none do. */
    public Weighted<T> filter(java.util.function.Predicate<T> keep) {
        Map<T, Double> kept = new LinkedHashMap<>();
        weights.forEach((item, weight) -> {
            if (keep.test(item)) {
                kept.put(item, weight);
            }
        });
        return kept.isEmpty() ? null : new Weighted<>(kept);
    }

    public T pick(Rng rng) {
        return rng.weightedPick(weights);
    }

    public Set<T> items() {
        return weights.keySet();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Weighted<?> w && weights.equals(w.weights);
    }

    @Override
    public int hashCode() {
        return weights.hashCode();
    }

    @Override
    public String toString() {
        return weights.toString();
    }
}
