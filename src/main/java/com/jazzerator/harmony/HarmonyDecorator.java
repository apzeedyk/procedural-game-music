package com.jazzerator.harmony;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Chord.Quality;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Note;
import com.jazzerator.theory.Scale;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;

/**
 * Randomly embellishes a plain progression: borrowed chords, deceptive cadences, 7ths/9ths, secondary
 * dominants, tritone substitutions, suspensions, and bass inversions. Splitting decorations (secondary
 * dominants, suspensions) divide an existing slot in half, so the total span never changes.
 */
public final class HarmonyDecorator {
    private static final long MIN_SPLIT_TICKS = MidiWriter.PPQ * 2L;

    private HarmonyDecorator() {
    }

    public static List<ChordSlot> decorate(List<ChordSlot> plain, Key key, HarmonyStyle style, Rng rng) {
        List<ChordSlot> slots = new ArrayList<>(plain);
        borrowed(slots, key, style, rng);
        deceptive(slots, key, style, rng);
        extensions(slots, key, style, rng);
        slots = secondaryDominants(slots, key, style, rng);
        tritoneSubs(slots, style, rng);
        slots = suspensions(slots, key, style, rng);
        inversions(slots, style, rng);
        return List.copyOf(slots);
    }

    /** Scale degree in semitones above the tonic. */
    private static int degree(Chord chord, Key key) {
        return Note.pc(chord.root() - key.tonic());
    }

    private static boolean isTriad(Chord c) {
        return c.quality() == Quality.MAJ || c.quality() == Quality.MIN;
    }

    /** Modal mixture: major keys borrow iv and bVI from the parallel minor; minor keys borrow the major V7. */
    private static void borrowed(List<ChordSlot> slots, Key key, HarmonyStyle style, Rng rng) {
        boolean minor = key.mode().isMinor();
        for (int i = 0; i < slots.size(); i++) {
            ChordSlot s = slots.get(i);
            Chord c = s.chord();
            int deg = degree(c, key);
            Chord replacement = null;
            if (!minor && deg == 5 && c.quality() == Quality.MAJ) {
                replacement = c.withQuality(Quality.MIN);
            } else if (!minor && deg == 9 && c.quality() == Quality.MIN) {
                replacement = new Chord(key.tonic() + 8, Quality.MAJ);
            } else if (minor && deg == 7 && c.quality() == Quality.MIN) {
                replacement = c.withQuality(Quality.DOM7);
            }
            if (replacement != null && rng.chance(style.borrowed())) {
                slots.set(i, new ChordSlot(replacement, s.startTick(), s.durationTicks()));
            }
        }
    }

    /**
     * V -> I landing exactly at the section's midpoint becomes V -> vi (or V -> bVI in minor). Only the midpoint
     * is eligible, so the section-ending cadence always resolves.
     */
    private static void deceptive(List<ChordSlot> slots, Key key, HarmonyStyle style, Rng rng) {
        if (slots.size() < 2) {
            return;
        }
        long span = slots.get(slots.size() - 1).endTick() - slots.get(0).startTick();
        long midpoint = slots.get(0).startTick() + span / 2;
        for (int i = 1; i < slots.size() - 1; i++) {
            ChordSlot s = slots.get(i);
            if (s.endTick() == midpoint && degree(s.chord(), key) == 0
                    && degree(slots.get(i - 1).chord(), key) == 7 && rng.chance(style.deceptive())) {
                Chord sub = key.mode().isMinor()
                        ? new Chord(key.tonic() + 8, Quality.MAJ)
                        : new Chord(key.tonic() + 9, Quality.MIN);
                slots.set(i, new ChordSlot(sub, s.startTick(), s.durationTicks()));
            }
        }
    }

    /** Adds diatonic 7ths (and sometimes 9ths) to triads. */
    private static void extensions(List<ChordSlot> slots, Key key, HarmonyStyle style, Rng rng) {
        Scale scale = key.scale();
        for (int i = 0; i < slots.size(); i++) {
            ChordSlot s = slots.get(i);
            Chord c = s.chord();
            int root = c.root();
            if (rng.chance(style.seventh())) {
                Quality q = switch (c.quality()) {
                    case MIN -> Quality.MIN7;
                    case MAJ -> degree(c, key) != 7 && scale.contains(root + 11) ? Quality.MAJ7 : Quality.DOM7;
                    case DIM -> scale.contains(root + 9) && !scale.contains(root + 10) ? Quality.DIM7 : Quality.HALF_DIM7;
                    default -> c.quality();
                };
                c = c.withQuality(q);
            }
            boolean ninthFits = scale.contains(root + 2) && c.quality() != Quality.DIM
                    && c.quality() != Quality.HALF_DIM7 && c.quality() != Quality.DIM7;
            if (!c.add9() && ninthFits && rng.chance(style.ninth())) {
                c = c.withAdd9(true);
            }
            slots.set(i, new ChordSlot(c, s.startTick(), s.durationTicks()));
        }
    }

    /** Splits the slot before a chord so its second half is that chord's V7 (e.g. C | Am -> C E7 | Am). */
    private static List<ChordSlot> secondaryDominants(List<ChordSlot> slots, Key key, HarmonyStyle style, Rng rng) {
        List<ChordSlot> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ChordSlot cur = slots.get(i);
            Chord target = cur.chord();
            boolean stableTarget = target.quality() != Quality.DIM && target.quality() != Quality.DIM7
                    && target.quality() != Quality.HALF_DIM7 && target.quality() != Quality.AUG;
            if (i > 0 && stableTarget && degree(target, key) != 0) {
                ChordSlot prev = out.get(out.size() - 1);
                int dominantRoot = Note.pc(target.root() + 7);
                boolean alreadyDominant = prev.chord().root() == dominantRoot;
                if (!alreadyDominant && prev.durationTicks() >= MIN_SPLIT_TICKS
                        && rng.chance(style.secondaryDominant())) {
                    out.remove(out.size() - 1);
                    long half = prev.durationTicks() / 2;
                    out.add(new ChordSlot(prev.chord(), prev.startTick(), half));
                    out.add(new ChordSlot(new Chord(dominantRoot, Quality.DOM7), prev.startTick() + half,
                            prev.durationTicks() - half));
                }
            }
            out.add(cur);
        }
        return out;
    }

    /** A dominant 7th resolving down a fifth is replaced by the dominant a tritone away (G7 -> C becomes Db7 -> C). */
    private static void tritoneSubs(List<ChordSlot> slots, HarmonyStyle style, Rng rng) {
        for (int i = 0; i < slots.size() - 1; i++) {
            ChordSlot s = slots.get(i);
            Chord c = s.chord();
            boolean resolvesByFifth = slots.get(i + 1).chord().root() == Note.pc(c.root() + 5);
            if (c.quality() == Quality.DOM7 && resolvesByFifth && rng.chance(style.tritoneSub())) {
                Chord sub = new Chord(c.root() + 6, Quality.DOM7, c.add9(), 0);
                slots.set(i, new ChordSlot(sub, s.startTick(), s.durationTicks()));
            }
        }
    }

    /**
     * A triad becomes suspended for its first half, resolving to the plain chord. The suspension must stay in the
     * key: sus4 when its fourth is diatonic, otherwise sus2, otherwise none (IV in a major key has no diatonic
     * sus4: C-F-G in G major would put F natural against F#).
     */
    private static List<ChordSlot> suspensions(List<ChordSlot> slots, Key key, HarmonyStyle style, Rng rng) {
        Scale scale = key.scale();
        List<ChordSlot> out = new ArrayList<>();
        for (ChordSlot s : slots) {
            Chord c = s.chord();
            Quality sus = scale.contains(c.root() + 5) ? Quality.SUS4 : scale.contains(c.root() + 2) ? Quality.SUS2 : null;
            if (sus != null && isTriad(c) && !c.add9() && s.durationTicks() >= MIN_SPLIT_TICKS
                    && rng.chance(style.suspension())) {
                long half = s.durationTicks() / 2;
                out.add(new ChordSlot(c.withQuality(sus), s.startTick(), half));
                out.add(new ChordSlot(c, s.startTick() + half, s.durationTicks() - half));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** Picks an inversion whose bass moves by step into the next chord, for a walking bass line. */
    private static void inversions(List<ChordSlot> slots, HarmonyStyle style, Rng rng) {
        for (int i = 0; i < slots.size() - 1; i++) {
            ChordSlot s = slots.get(i);
            Chord c = s.chord();
            if (!rng.chance(style.inversion())) {
                continue;
            }
            int nextBass = slots.get(i + 1).chord().root();
            int best = 0;
            double bestScore = bassScore(c.bassPc(), nextBass);
            for (int inv = 1; inv <= 2; inv++) {
                double score = bassScore(c.withInversion(inv).bassPc(), nextBass);
                if (score < bestScore) {
                    best = inv;
                    bestScore = score;
                }
            }
            slots.set(i, new ChordSlot(c.withInversion(best), s.startTick(), s.durationTicks()));
        }
    }

    /** Lower is better: a 1-2 semitone step into the next bass note is ideal; repeating the note is worst. */
    private static double bassScore(int bassPc, int nextBassPc) {
        int d = Math.abs(Note.pc(nextBassPc - bassPc));
        d = Math.min(d, 12 - d);
        return d == 0 ? 3 : Math.abs(d - 1.5);
    }
}
