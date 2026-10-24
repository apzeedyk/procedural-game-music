package com.jazzerator.arrange;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Track;

/** Chord accompaniment: voice-led voicings played in the groove's comping rhythm. */
final class CompPart {
    private static final long PPQ = MidiWriter.PPQ;
    private static final int FLOOR = 55; // G3: first voicing
    private static final int MIN = 52;   // E3: lowest note a voice-led voicing may start on
    private static final int MAX = 79;   // G5
    private static final int VELOCITY = 58;

    /** A comping attack: position in beats from the bar start, and length in beats. */
    record Hit(double beat, double length) {
    }

    private CompPart() {
    }

    static void render(Song song, Groove groove, Track track, int channel, Timing timing, Rng rng) {
        int beatsPerBar = song.spec().beatsPerBar();
        int[] previous = null;
        for (Section section : song.sections()) {
            for (ChordSlot slot : section.chords()) {
                int[] voicing = previous == null ? slot.chord().voicing(FLOOR) : nearestVoicing(slot.chord(), previous);
                previous = voicing;
                switch (groove.comp()) {
                    case BROKEN -> arpeggiate(track, channel, timing, slot, voicing, PPQ / 2,
                            new int[]{0, 1, 2, 3, 2, 1}, rng);
                    case CHIP_ARPEGGIO -> arpeggiate(track, channel, timing, slot, voicing, PPQ / 4,
                            new int[]{0, 1, 2, 3}, rng);
                    case STRUM -> strum(track, channel, timing, slot, voicing, rng);
                    default -> {
                        for (long[] span : spans(slot, section, hits(groove.comp(), beatsPerBar))) {
                            int accent = Math.floorMod(span[0] - section.startTick(), PPQ) == 0 ? 6 : 0;
                            for (int pitch : voicing) {
                                timing.note(track, channel, pitch, VELOCITY + accent + rng.range(-4, 4), span[0],
                                        span[1]);
                            }
                        }
                    }
                }
            }
        }
    }

    static List<Hit> hits(Groove.CompStyle style, int beatsPerBar) {
        List<Hit> hits = new ArrayList<>();
        boolean waltz = beatsPerBar == 3;
        switch (style) {
            case SUSTAIN -> hits.add(new Hit(0, beatsPerBar));
            case BLOCK_QUARTERS -> {
                for (int b = waltz ? 1 : 0; b < beatsPerBar; b++) { // waltz: "oom" is the bass, comp plays "pah-pah"
                    hits.add(new Hit(b, 0.8));
                }
            }
            case PUSH -> {
                hits.add(new Hit(0, 1.5));
                hits.add(new Hit(1.5, waltz ? 1.5 : 1));
                if (!waltz) {
                    hits.add(new Hit(2.5, 1.5));
                }
            }
            case STABS -> {
                for (double b = 0; b < beatsPerBar; b += 0.5) {
                    hits.add(new Hit(b, 0.3));
                }
            }
            case CHARLESTON -> {
                hits.add(new Hit(0, 0.8));
                hits.add(new Hit(1.5, 0.4));
                if (!waltz) {
                    hits.add(new Hit(3, 0.5));
                }
            }
            default -> hits.add(new Hit(0, beatsPerBar));
        }
        return hits;
    }

    /**
     * Pattern hits that fall inside the slot, as absolute [start, length] pairs clipped to the slot end. A chord
     * that changes where the pattern has no hit still gets an attack on its first tick.
     */
    static List<long[]> spans(ChordSlot slot, Section section, List<Hit> pattern) {
        List<long[]> spans = new ArrayList<>();
        long ticksPerBar = section.ticksPerBar();
        long firstBar = (slot.startTick() - section.startTick()) / ticksPerBar;
        long lastBar = (slot.endTick() - 1 - section.startTick()) / ticksPerBar;
        for (long bar = firstBar; bar <= lastBar; bar++) {
            long barStart = section.startTick() + bar * ticksPerBar;
            for (Hit h : pattern) {
                long start = barStart + Math.round(h.beat() * PPQ);
                if (start >= slot.startTick() && start < slot.endTick()) {
                    spans.add(new long[]{start, Math.min(Math.round(h.length() * PPQ), slot.endTick() - start)});
                }
            }
        }
        if (spans.isEmpty() || spans.get(0)[0] != slot.startTick()) {
            long next = spans.isEmpty() ? slot.endTick() : spans.get(0)[0];
            spans.add(0, new long[]{slot.startTick(), Math.min(PPQ, next - slot.startTick())});
        }
        return spans;
    }

    private static void arpeggiate(Track track, int channel, Timing timing, ChordSlot slot, int[] voicing, long unit,
                                   int[] order, Rng rng) {
        int step = 0;
        for (long t = slot.startTick(); t < slot.endTick(); t += unit, step++) {
            int pitch = voicing[order[step % order.length] % voicing.length];
            long length = Math.min(unit * 2, slot.endTick() - t); // let notes ring into the next
            timing.note(track, channel, pitch, VELOCITY + (step == 0 ? 8 : 0) + rng.range(-4, 4), t,
                    Math.min(length, slot.endTick() - t));
        }
    }

    /** Sustained chord, rolled from the bottom up like a lazy guitar or e-piano strum. */
    private static void strum(Track track, int channel, Timing timing, ChordSlot slot, int[] voicing, Rng rng) {
        for (int k = 0; k < voicing.length; k++) {
            long offset = Math.min(k * 18L, slot.durationTicks() / 4);
            timing.note(track, channel, voicing[k], VELOCITY + rng.range(-5, 3), slot.startTick() + offset,
                    slot.durationTicks() - offset);
        }
    }

    /**
     * Voice leading: among all inversions and octave placements inside the comp range, picks the voicing whose
     * notes move least relative to the previous chord.
     */
    static int[] nearestVoicing(Chord chord, int[] previous) {
        int[] best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int inv = 0; inv < chord.size(); inv++) {
            for (int floor = MIN; floor < MIN + 12; floor++) {
                int[] v = chord.withInversion(inv).voicing(floor);
                if (v[v.length - 1] > MAX) {
                    continue;
                }
                int cost = movement(v, previous) + movement(previous, v);
                if (cost < bestCost) {
                    best = v;
                    bestCost = cost;
                }
            }
        }
        return best != null ? best : chord.voicing(FLOOR);
    }

    /** Sum over notes in {@code from} of the distance to the nearest note in {@code to}. */
    private static int movement(int[] from, int[] to) {
        int total = 0;
        for (int a : from) {
            int nearest = Integer.MAX_VALUE;
            for (int b : to) {
                nearest = Math.min(nearest, Math.abs(a - b));
            }
            total += nearest;
        }
        return total;
    }
}
