package com.jazzerator.arrange;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Instruments;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Note;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Track;

/** Chord accompaniment: voice-led voicings played in the groove's comping rhythm. */
final class CompPart {
    private static final long PPQ = MidiWriter.PPQ;
    private static final int MIN = 50;   // D3: lowest note a voice-led voicing may start on
    private static final int MAX = 70;   // Bb4: stays under the melody (which lives from D4 up)
    /** Average pitch the chords gravitate to (Bb3), so the comp sits in the same place in every section. */
    private static final double HOME = 58;
    /** Cost per semitone of drifting from home, relative to voice-leading movement cost. */
    private static final double HOME_PULL = 3.0;
    /** How far (in semitones of average pitch) the comp may stray from home before dropping color tones. */
    private static final double HOME_WINDOW = 3.0;
    private static final int VELOCITY = 58;
    /**
     * Lowest clean bottom note for each interval of 1-11 semitones. Thirds and wider follow the textbook low
     * interval limits (C3 for a minor 3rd ... F2 for a 7th). Seconds are stricter than the textbook (C4 for a
     * minor 2nd, B3 for a major 2nd): on piano, a close second below middle C clouds the chord.
     */
    private static final int[] LOW_INTERVAL_LIMIT = {0, 60, 59, 48, 46, 45, 47, 34, 43, 41, 41, 41};
    private static final double MUD_PENALTY = 12;
    private static final double VOICE_COUNT_CHANGE = 6;
    private static final double SPREAD_CHANGE = 0.5;
    /** Drop-2 voicings may reach below the close-voicing floor, but not into the bass's own register. */
    private static final int LOWEST_NOTE = 47; // B2
    private static final long INSERTED = 1;
    private static final int INSERTED_ACCENT = -6;
    /** The ceiling never forces voicings lower than this top note, or the chords would collide with the bass. */
    private static final int LOWEST_CEILING = 60;

    /** A comping attack: position in beats from the bar start, and length in beats. */
    record Hit(double beat, double length) {
    }

    private CompPart() {
    }

    /**
     * @param upperVoices the melody and any harmony line; the chords are voiced underneath them
     */
    static void render(Song song, Groove groove, Track track, int channel, Timing timing, Rng rng,
                       List<MelodyNote> upperVoices) {
        int beatsPerBar = song.spec().beatsPerBar();
        Groove.CompStyle style = articulation(groove.comp(), song.spec().instruments().comp());
        List<int[]> voicings = planVoicings(song, upperVoices);
        int index = 0;
        for (Section section : song.sections()) {
            for (ChordSlot slot : section.chords()) {
                int[] voicing = voicings.get(index++);
                switch (style) {
                    case BROKEN -> arpeggiate(track, channel, timing, slot, voicing, PPQ / 2,
                            new int[]{0, 1, 2, 3, 2, 1}, rng);
                    case CHIP_ARPEGGIO -> arpeggiate(track, channel, timing, slot, voicing, PPQ / 4,
                            new int[]{0, 1, 2, 3}, rng);
                    case STRUM -> strum(track, channel, timing, slot, voicing, rng);
                    default -> {
                        for (long[] span : spans(slot, section, hits(style, beatsPerBar))) {
                            // Groove hits on the beat get an accent; an attack inserted for a chord change (often a
                            // suspension resolving) is the release, not the push, so it plays under the groove.
                            int accent = span[2] == INSERTED ? INSERTED_ACCENT
                                    : Math.floorMod(span[0] - section.startTick(), PPQ) == 0 ? 6 : 0;
                            // One random lean per hit, shared by every note, so the chord stays even inside.
                            int hit = VELOCITY + accent + rng.fork("comp", span[0]).range(-3, 3);
                            for (int pitch : voicing) {
                                timing.note(track, channel, pitch, hit + topLift(pitch, voicing), span[0], span[1]);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * One voicing per chord slot, in song order. Voice-led from the home register, and kept underneath the melody
     * voices: a chord's top note stays below the lowest lead or harmony note sounding with it, the way an arranger
     * keeps accompaniment out of the tune's way, so the two never trade places and mask each other.
     */
    static List<int[]> planVoicings(Song song, List<MelodyNote> upperVoices) {
        List<int[]> voicings = new ArrayList<>();
        int[] previous = {(int) HOME}; // the first chord is voice-led from the home register itself
        Chord previousChord = null;
        for (Section section : song.sections()) {
            for (ChordSlot slot : section.chords()) {
                int ceiling = ceiling(slot, upperVoices);
                int[] voicing = resolveSuspension(previousChord, previous, slot.chord(), ceiling);
                if (voicing == null) {
                    voicing = nearestVoicing(slot.chord(), previous, ceiling);
                }
                voicings.add(voicing);
                previous = voicing;
                previousChord = slot.chord();
            }
        }
        return voicings;
    }

    /**
     * A suspension resolves by step within the same voicing: Asus4 (D E A) to A moves only the D, down to C#.
     * Re-voicing from scratch could drop the 4th and add the 3rd an octave away, which pops out on top.
     * Returns null when this isn't a sus-to-plain resolution or the result would break the ceiling.
     */
    static int[] resolveSuspension(Chord previousChord, int[] previous, Chord chord, int ceiling) {
        if (previousChord == null || previousChord.root() != chord.root() || chord.size() != 3) {
            return null;
        }
        int suspended;
        if (previousChord.quality() == Chord.Quality.SUS4) {
            suspended = Note.pc(chord.root() + 5);
        } else if (previousChord.quality() == Chord.Quality.SUS2) {
            suspended = Note.pc(chord.root() + 2);
        } else {
            return null;
        }
        int third = Note.pc(chord.root() + chord.quality().intervals()[1]);
        int[] resolved = previous.clone();
        boolean moved = false;
        for (int i = 0; i < resolved.length; i++) {
            if (Note.pc(resolved[i]) == suspended) {
                int up = Note.pc(third - suspended);
                resolved[i] += up <= 2 ? up : up - 12; // the nearest third: down from a 4th, up from a 2nd
                moved = true;
            }
        }
        java.util.Arrays.sort(resolved);
        // A resolving voice may step just under the usual floor (C#3 from D3); the bass is still an octave below.
        if (!moved || resolved[resolved.length - 1] > ceiling || resolved[0] < MIN - 2) {
            return null;
        }
        return resolved;
    }

    /** Highest top note allowed for a slot: just under the lowest melody-voice note sounding with it. */
    static int ceiling(ChordSlot slot, List<MelodyNote> upperVoices) {
        int lowest = Integer.MAX_VALUE;
        for (MelodyNote n : upperVoices) {
            boolean overlaps = n.startTick() < slot.endTick() && n.endTick() > slot.startTick();
            if (overlaps && n.durationTicks() > PPQ / 8) { // grace notes don't count
                lowest = Math.min(lowest, n.pitch());
            }
        }
        return lowest == Integer.MAX_VALUE ? MAX : Math.max(LOWEST_CEILING, Math.min(MAX, lowest - 1));
    }

    /**
     * Slow-attack instruments (strings, choirs, pads) can't articulate short rhythmic hits, so they sustain and
     * leave the rhythm to the drums and bass. Arpeggios and strums are left alone.
     */
    static Groove.CompStyle articulation(Groove.CompStyle style, int program) {
        boolean rhythmic = switch (style) {
            case BLOCK_QUARTERS, PUSH, STABS, CHARLESTON -> true;
            default -> false;
        };
        return rhythmic && Instruments.isSlowAttack(program) ? Groove.CompStyle.SUSTAIN : style;
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
                // Locked to the rock kick (1, 3, and-of-3): the push on "3 and" lands with the kick, so the
                // syncopation grooves instead of floating with nothing under it.
                if (waltz) {
                    hits.add(new Hit(0, 2));
                    hits.add(new Hit(2, 1));
                } else {
                    hits.add(new Hit(0, 2));
                    hits.add(new Hit(2, 0.5));
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
     * Pattern hits that fall inside the slot, as absolute [start, length, inserted] triples clipped to the slot end.
     * A chord that changes where the pattern has no hit still gets an attack on its first tick, flagged as inserted.
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
                    spans.add(new long[]{start, Math.min(Math.round(h.length() * PPQ), slot.endTick() - start), 0});
                }
            }
        }
        if (!spans.isEmpty() && spans.get(0)[0] != slot.startTick() && spans.get(0)[0] - slot.startTick() <= PPQ / 2) {
            // A groove hit within half a beat after the chord change moves onto the change, rather than the change
            // adding its own attack right before it (which stutters: 2&, 3, 3&).
            long[] first = spans.get(0);
            spans.set(0, new long[]{slot.startTick(), first[0] + first[1] - slot.startTick(), first[2]});
        } else if (spans.isEmpty() || spans.get(0)[0] != slot.startTick()) {
            long next = spans.isEmpty() ? slot.endTick() : spans.get(0)[0];
            spans.add(0, new long[]{slot.startTick(), Math.min(PPQ, next - slot.startTick()), INSERTED});
        }
        return spans;
    }

    private static void arpeggiate(Track track, int channel, Timing timing, ChordSlot slot, int[] voicing, long unit,
                                   int[] order, Rng rng) {
        int step = 0;
        for (long t = slot.startTick(); t < slot.endTick(); t += unit, step++) {
            int pitch = voicing[order[step % order.length] % voicing.length];
            long length = Math.min(unit * 2, slot.endTick() - t); // let notes ring into the next
            timing.note(track, channel, pitch, VELOCITY + (step == 0 ? 8 : 0) + rng.fork("arp", t).range(-2, 2), t,
                    Math.min(length, slot.endTick() - t));
        }
    }

    /** Sustained chord, rolled from the bottom up like a lazy guitar or e-piano strum. */
    private static void strum(Track track, int channel, Timing timing, ChordSlot slot, int[] voicing, Rng rng) {
        int hit = VELOCITY + rng.fork("strum", slot.startTick()).range(-3, 3);
        for (int k = 0; k < voicing.length; k++) {
            long offset = Math.min(k * 18L, slot.durationTicks() / 4);
            timing.note(track, channel, voicing[k], hit + topLift(voicing[k], voicing), slot.startTick() + offset,
                    slot.durationTicks() - offset);
        }
    }

    /** Pianists voice the top of a chord slightly forward; everything else in the hit plays evenly. */
    private static int topLift(int pitch, int[] voicing) {
        return pitch == voicing[voicing.length - 1] ? 3 : 0;
    }

    /**
     * Voice leading: among all inversions and octave placements inside the comp range, picks the voicing whose
     * notes move least relative to the previous chord, with a pull toward a home register. Without the pull the
     * chords drift with the harmony, and the comp alternates between hiding under the melody and popping out.
     */
    /**
     * Preference order, the way a pianist keeps their hand in place: a clean voicing near the home register (full
     * chord, then without the 9th, a color tone that's dropped when it can't be voiced clearly), then a clean
     * voicing anywhere under the melody, and only then the least muddy voicing that fits.
     */
    static int[] nearestVoicing(Chord chord, int[] previous, int maxTop) {
        Chord plain = chord.withAdd9(false);
        int[] best = null;
        for (double window : new double[]{HOME_WINDOW, Double.MAX_VALUE}) {
            best = bestFit(chord, previous, maxTop, true, window);
            if (best == null && chord.add9()) {
                best = bestFit(plain, previous, maxTop, true, window);
            }
            if (best != null) {
                return best;
            }
        }
        best = bestFit(chord, previous, maxTop, false, Double.MAX_VALUE);
        if (best == null && chord.add9()) {
            best = bestFit(plain, previous, maxTop, false, Double.MAX_VALUE);
        }
        return best != null ? best : lowestVoicing(plain);
    }

    private static int[] bestFit(Chord chord, int[] previous, int maxTop, boolean requireClean, double window) {
        int[] best = null;
        double bestCost = Double.MAX_VALUE;
        for (int[] v : candidates(chord)) {
            double average = java.util.Arrays.stream(v).average().orElse(HOME);
            int mud = mudViolations(v, suspendedTone(chord));
            if (v[v.length - 1] > maxTop || v[0] < LOWEST_NOTE || requireClean && mud > 0
                    || Math.abs(average - HOME) > window) {
                continue;
            }
            double cost = movement(v, previous) + movement(previous, v) + HOME_PULL * Math.abs(average - HOME)
                    + MUD_PENALTY * mud + textureChange(v, previous);
            if (cost < bestCost) {
                best = v;
                bestCost = cost;
            }
        }
        return best;
    }

    /**
     * Every inversion at every octave placement, each as a close voicing and as a drop-2 voicing (second-highest
     * note down an octave, which opens up low clusters). Chords of four or more notes are also tried without
     * their perfect fifth (the most expendable tone) and without their root (the bass already plays it).
     */
    static List<int[]> candidates(Chord chord) {
        List<int[]> out = new ArrayList<>();
        int fifth = Note.pc(chord.root() + 7);
        for (int inv = 0; inv < chord.size(); inv++) {
            for (int floor = MIN; floor < MIN + 12; floor++) {
                int[] close = chord.withInversion(inv).voicing(floor);
                List<int[]> shapes = new ArrayList<>(List.of(close));
                if (close.length >= 4 && chord.containsPc(fifth) && Note.pc(close[0]) != fifth) {
                    shapes.add(java.util.Arrays.stream(close).filter(p -> Note.pc(p) != fifth).toArray());
                }
                if (close.length >= 4) {
                    // Rootless: the bass already plays the root, so the piano can leave it out (D G# B for E7).
                    shapes.add(java.util.Arrays.stream(close).filter(p -> Note.pc(p) != chord.root()).toArray());
                }
                for (int[] shape : shapes) {
                    out.add(shape);
                    if (shape.length >= 4) {
                        out.add(drop2(shape));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Cost of changing the comp's texture: its number of notes and its spread. Keeping both steady makes the
     * accompaniment sound like one player with a consistent touch, instead of flickering between thin and thick
     * chords. The very first chord (voice-led from a single home note) is free to set the texture.
     */
    static double textureChange(int[] v, int[] previous) {
        if (previous.length < 2) {
            return 0;
        }
        int spread = v[v.length - 1] - v[0];
        int previousSpread = previous[previous.length - 1] - previous[0];
        return VOICE_COUNT_CHANGE * Math.abs(v.length - previous.length)
                + SPREAD_CHANGE * Math.abs(spread - previousSpread);
    }

    /** Second-highest note dropped an octave. */
    static int[] drop2(int[] close) {
        int[] v = close.clone();
        v[v.length - 2] -= 12;
        java.util.Arrays.sort(v);
        return v;
    }

    /**
     * Adjacent intervals whose lower note is below that interval's low interval limit (the arranger's rule of
     * thumb for where close intervals turn to mud: a major 2nd needs its bottom at Eb3 or higher, a minor 3rd at
     * C3, a major 3rd at Bb2, and so on).
     */
    static int mudViolations(int[] voicing) {
        return mudViolations(voicing, -1);
    }

    /**
     * As {@link #mudViolations(int[])}, but a second that involves the suspended tone of a sus chord is exempt:
     * that rub is the suspension itself, a deliberate tension that resolves on the next chord.
     */
    static int mudViolations(int[] voicing, int suspendedPc) {
        int count = 0;
        for (int k = 0; k + 1 < voicing.length; k++) {
            int interval = voicing[k + 1] - voicing[k];
            boolean suspension = interval <= 2
                    && (Note.pc(voicing[k]) == suspendedPc || Note.pc(voicing[k + 1]) == suspendedPc);
            if (interval >= 1 && interval <= 11 && voicing[k] < LOW_INTERVAL_LIMIT[interval] && !suspension) {
                count++;
            }
        }
        return count;
    }

    /** The suspended tone's pitch class for sus chords, or -1. */
    static int suspendedTone(Chord chord) {
        return switch (chord.quality()) {
            case SUS4 -> Note.pc(chord.root() + 5);
            case SUS2 -> Note.pc(chord.root() + 2);
            default -> -1;
        };
    }

    /** The voicing with the lowest top note, for when nothing fits under the ceiling. */
    private static int[] lowestVoicing(Chord chord) {
        int[] best = null;
        for (int inv = 0; inv < chord.size(); inv++) {
            for (int floor = MIN; floor < MIN + 12; floor++) {
                int[] v = chord.withInversion(inv).voicing(floor);
                if (best == null || v[v.length - 1] < best[best.length - 1]) {
                    best = v;
                }
            }
        }
        return best;
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
