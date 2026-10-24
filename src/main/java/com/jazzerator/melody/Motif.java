package com.jazzerator.melody;

import java.util.ArrayList;
import java.util.List;

/**
 * A short melodic idea stored independent of pitch, so it can be re-fitted to any chord.
 *
 * @param rhythm durations in beats; negative values are rests
 * @param steps  one entry per sounding note: scale steps from the previous note
 */
public record Motif(List<Double> rhythm, List<Integer> steps) {

    public double beats() {
        return rhythm.stream().mapToDouble(Math::abs).sum();
    }

    /** Mirror the contour: every upward step becomes downward and vice versa. */
    public Motif inverted() {
        return new Motif(rhythm, steps.stream().map(s -> -s).toList());
    }

    public Motif withStep(int index, int step) {
        List<Integer> copy = new ArrayList<>(steps);
        copy.set(index, step);
        return new Motif(rhythm, List.copyOf(copy));
    }

    /**
     * Keeps the rhythm up to {@code holdBeats} before the end, then holds one final note to the end. The final
     * note is the cadence note; the realizer lands it on the chord root.
     */
    public Motif withCadence(double holdBeats) {
        double cutoff = beats() - holdBeats;
        List<Double> newRhythm = new ArrayList<>();
        List<Integer> newSteps = new ArrayList<>();
        double pos = 0;
        int noteIndex = 0;
        for (double d : rhythm) {
            if (pos >= cutoff) {
                break;
            }
            double len = Math.min(Math.abs(d), cutoff - pos);
            newRhythm.add(d < 0 ? -len : len);
            if (d > 0) {
                newSteps.add(steps.get(noteIndex++));
            }
            pos += Math.abs(d);
        }
        newRhythm.add(holdBeats);
        newSteps.add(-1); // gentle descent into the cadence
        return new Motif(List.copyOf(newRhythm), List.copyOf(newSteps));
    }
}
