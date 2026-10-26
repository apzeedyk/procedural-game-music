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
 * dominants, suspensions) divide an existing slot in half, so the total span never changes. Every random choice
 * is keyed by decoration type and position, so one decision never shifts the dice for another.
 */
public final class HarmonyDecorator {
    private static final long MIN_SPLIT_TICKS = MidiWriter.PPQ * 2L;

    private HarmonyDecorator() {
    }

    public static List<ChordSlot> decorate(List<ChordSlot> plain, Key key, HarmonyStyle style, Rng rng) {
        if (isModal(key)) {
            style = style.forModalKey();
        }
        List<ChordSlot> slots = new ArrayList<>(plain);
        borrowed(slots, key, style, rng);
        deceptive(slots, key, style, rng);
        extensions(slots, key, style, rng);
        slots = secondaryDominants(slots, key, style, rng);
        tritoneSubs(slots, key, style, rng);
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

    /**
     * Modal mixture: major keys borrow iv and bVI from the parallel minor; minor keys borrow the major V7. Borrowed
     * chords only appear where they lead somewhere, never as a section's first chord: iv right before I or V (the
     * bittersweet IV-iv-I lift), bVI before bVII, V or I (the heroic bVI-bVII-I cadence), V7 before i.
     */
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
            // Borrowing the parallel minor's color (bVI, iv) is the point, so only the mode's avoided notes count.
            int next = i + 1 < slots.size() ? degree(slots.get(i + 1).chord(), key) : -1;
            boolean leadsHome = i > 0 && switch (deg) {
                case 5 -> next == 0 || next == 7;           // iv -> I or V
                case 9 -> next == 10 || next == 7 || next == 0; // bVI -> bVII, V or I
                case 7 -> next == 0;                        // V7 -> i
                default -> false;
            };
            if (replacement != null && rng.fork("borrowed", s.startTick()).chance(style.borrowed())
                    && leadsHome && !HarmonyPlanner.usesAvoided(replacement, key)) {
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
                    && degree(slots.get(i - 1).chord(), key) == 7 && rng.fork("deceptive", s.startTick()).chance(style.deceptive())) {
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
            boolean fallsByFifth = i + 1 < slots.size() && slots.get(i + 1).chord().root() == Note.pc(root + 5);
            if (rng.fork("seventh", s.startTick()).chance(style.seventh())) {
                // Only the 7th the mode provides: G gets F in C major (G7) but F# in C Lydian (Gmaj7).
                Quality q = switch (c.quality()) {
                    case MIN -> scale.contains(root + 10) ? Quality.MIN7 : c.quality();
                    case MAJ -> scale.contains(root + 11) ? (style.majorSevenths() ? Quality.MAJ7 : c.quality())
                            // In a modal key a dominant 7th falling a fifth (E7 -> A in E Mixolydian) is V7 -> I of
                            // the parent major and makes that chord sound like home, so it stays a triad there.
                            : scale.contains(root + 10) && !(isModal(key) && fallsByFifth) ? Quality.DOM7
                            : c.quality();
                    case DIM -> scale.contains(root + 9) && !scale.contains(root + 10) ? Quality.DIM7 : Quality.HALF_DIM7;
                    default -> c.quality();
                };
                c = c.withQuality(q);
            }
            if (isModal(key) && fallsByFifth && c.quality() == Quality.DOM7) {
                // Even when the progression itself asks for it (IV7 -> bVII in Dorian is V7 -> I of the parent
                // major), a modal key re-voices the dominant to its in-mode form so the modal tonic stays home.
                c = c.withQuality(scale.contains(root + 11) && style.majorSevenths() ? Quality.MAJ7 : Quality.MAJ);
            }
            boolean ninthFits = scale.contains(root + 2) && c.quality() != Quality.DIM
                    && c.quality() != Quality.HALF_DIM7 && c.quality() != Quality.DIM7;
            if (!c.add9() && ninthFits && rng.fork("ninth", s.startTick()).chance(style.ninth())) {
                c = c.withAdd9(true);
            }
            slots.set(i, new ChordSlot(c, s.startTick(), s.durationTicks()));
        }
    }

    /**
     * Splits the slot before a chord so its second half is that chord's V7 (e.g. C | Am -> C E7 | Am). Skipped when
     * the V7 would contain the "other" third of the home chord (C natural in A major, C# in A minor): that note
     * flips the song's major/minor quality, and the cadence makes its target sound like a new tonic. D7 -> G in
     * A Mixolydian is the classic case.
     */
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
                Chord dominant = new Chord(dominantRoot, Quality.DOM7);
                // Roll before applying the key-quality rule, so rejecting a dominant doesn't shift the random
                // sequence that every later decision in the song depends on.
                if (!alreadyDominant && prev.durationTicks() >= MIN_SPLIT_TICKS
                        && rng.fork("secondaryDominant", cur.startTick()).chance(style.secondaryDominant())
                        && !breaksMode(dominant, key)) {
                    out.remove(out.size() - 1);
                    long half = prev.durationTicks() / 2;
                    out.add(new ChordSlot(prev.chord(), prev.startTick(), half));
                    out.add(new ChordSlot(dominant, prev.startTick() + half, prev.durationTicks() - half));
                }
            }
            out.add(cur);
        }
        return out;
    }

    /**
     * True if a chromatic chord would undo the key's identity: it holds the home chord's other third, or a note
     * the mode avoids (a leading tone in Mixolydian, a plain 4th in Lydian, ...).
     */
    static boolean breaksMode(Chord chord, Key key) {
        if (flipsTonicQuality(chord, key)) {
            return true;
        }
        for (int offset : key.mode().avoid()) {
            if (chord.containsPc(key.tonic() + offset)) {
                return true;
            }
        }
        return false;
    }

    /** Modes with notes to avoid (Mixolydian, Dorian, Lydian, Phrygian) follow modal rather than tonal habits. */
    static boolean isModal(Key key) {
        return key.mode().avoid().length > 0;
    }

    /** True if the chord contains the third that the home chord doesn't have (b3 in a major mode, 3 in a minor one). */
    static boolean flipsTonicQuality(Chord chord, Key key) {
        int otherThird = Note.pc(key.tonic() + (key.mode().isMinor() ? 4 : 3));
        return chord.containsPc(otherThird);
    }

    /** A dominant 7th resolving down a fifth is replaced by the dominant a tritone away (G7 -> C becomes Db7 -> C). */
    private static void tritoneSubs(List<ChordSlot> slots, Key key, HarmonyStyle style, Rng rng) {
        for (int i = 0; i < slots.size() - 1; i++) {
            ChordSlot s = slots.get(i);
            Chord c = s.chord();
            boolean resolvesByFifth = slots.get(i + 1).chord().root() == Note.pc(c.root() + 5);
            Chord sub = new Chord(c.root() + 6, Quality.DOM7, c.add9(), 0);
            if (c.quality() == Quality.DOM7 && resolvesByFifth
                    && rng.fork("tritoneSub", s.startTick()).chance(style.tritoneSub()) && !breaksMode(sub, key)) {
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
        ChordSlot last = slots.get(slots.size() - 1);
        for (ChordSlot s : slots) {
            Chord c = s.chord();
            if (isModal(key) && s != last) {
                out.add(s); // modal: one chord per bar, the sus-resolve reserved for the section's last bar
                continue;
            }
            Quality sus = scale.contains(c.root() + 5) ? Quality.SUS4 : scale.contains(c.root() + 2) ? Quality.SUS2 : null;
            if (sus != null && isTriad(c) && !c.add9() && s.durationTicks() >= MIN_SPLIT_TICKS
                    && rng.fork("suspension", s.startTick()).chance(style.suspension())) {
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
            if (!rng.fork("inversion", s.startTick()).chance(style.inversion())) {
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
