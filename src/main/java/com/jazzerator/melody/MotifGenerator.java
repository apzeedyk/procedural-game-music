package com.jazzerator.melody;

import com.jazzerator.preset.Feel;
import com.jazzerator.util.Rng;
import com.jazzerator.util.Weighted;
import java.util.ArrayList;
import java.util.List;

/** Builds random motifs from feel-appropriate rhythm cells and an arch-shaped stepwise contour. */
public final class MotifGenerator {
    // Rhythm cells in beats; negative = rest.
    private static final List<Double> QUARTER = List.of(1.0);
    private static final List<Double> EIGHTHS = List.of(0.5, 0.5);
    private static final List<Double> HALF = List.of(2.0);
    private static final List<Double> DOTTED = List.of(1.5, 0.5);
    private static final List<Double> SIXTEENTHS = List.of(0.25, 0.25, 0.25, 0.25);
    private static final List<Double> EIGHTH_TWO_SIXTEENTHS = List.of(0.5, 0.25, 0.25);
    private static final List<Double> REST = List.of(-1.0);
    private static final List<Double> PICKUP = List.of(-0.5, 0.5);

    private static final Weighted<List<Double>> STRAIGHT_CELLS = Weighted.of(QUARTER, 3.0).and(EIGHTHS, 3.0)
            .and(HALF, 1.5).and(DOTTED, 1.5).and(REST, 0.7).and(PICKUP, 0.7).and(EIGHTH_TWO_SIXTEENTHS, 0.5);
    private static final Weighted<List<Double>> SWING_CELLS = Weighted.of(QUARTER, 3.0).and(EIGHTHS, 4.0)
            .and(HALF, 1.0).and(DOTTED, 1.0).and(REST, 1.0).and(PICKUP, 1.0);
    private static final Weighted<List<Double>> DRIVING_CELLS = Weighted.of(EIGHTHS, 3.0).and(SIXTEENTHS, 2.0)
            .and(EIGHTH_TWO_SIXTEENTHS, 2.0).and(QUARTER, 2.0).and(DOTTED, 1.0).and(REST, 0.5);

    private static final Weighted<Integer> STEP_SIZE = Weighted.of(0, 1.0).and(1, 5.0).and(2, 2.0)
            .and(3, 0.6).and(4, 0.25);

    private MotifGenerator() {
    }

    public static Motif generate(Feel feel, int beatsPerBar, int bars, Rng rng) {
        Weighted<List<Double>> cells = switch (feel) {
            case STRAIGHT -> STRAIGHT_CELLS;
            case SWING, SHUFFLE -> SWING_CELLS;
            case DRIVING -> DRIVING_CELLS;
        };
        List<Double> rhythm = new ArrayList<>();
        for (int bar = 0; bar < bars; bar++) {
            double pos = 0;
            while (pos < beatsPerBar) {
                List<Double> cell = cells.pick(rng);
                double len = cell.stream().mapToDouble(Math::abs).sum();
                if (pos + len > beatsPerBar) {
                    continue; // cells never cross the barline
                }
                rhythm.addAll(cell);
                pos += len;
            }
        }
        // A motif must start sounding by its second beat and contain at least two notes.
        if (rhythm.get(0) < 0 && rhythm.get(1) < 0 || rhythm.stream().filter(d -> d > 0).count() < 2) {
            return generate(feel, beatsPerBar, bars, rng);
        }

        long noteCount = rhythm.stream().filter(d -> d > 0).count();
        List<Integer> steps = new ArrayList<>();
        for (int i = 0; i < noteCount; i++) {
            boolean firstHalf = i < noteCount / 2;
            boolean up = rng.chance(firstHalf ? 0.65 : 0.35); // arch: rise then fall
            int size = STEP_SIZE.pick(rng);
            steps.add(up ? size : -size);
        }
        return new Motif(List.copyOf(rhythm), List.copyOf(steps));
    }
}
