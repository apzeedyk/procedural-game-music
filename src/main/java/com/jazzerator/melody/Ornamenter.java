package com.jazzerator.melody;

import com.jazzerator.form.Section;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Feel;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Scale;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Embellishes a plain melody with runs, trills, passing and neighbor tones, grace notes, repeated notes, merged
 * notes, and staccato. Invariants: every note keeps its original start and pitch except where a merge absorbs a
 * weak-beat note, ornaments never overlap, and no note crosses a barline.
 */
public final class Ornamenter {
    private static final long PPQ = MidiWriter.PPQ;
    private static final long GRACE = PPQ / 8;

    private Ornamenter() {
    }

    public static List<MelodyNote> ornament(List<MelodyNote> plain, SongSpec spec, Rng rng) {
        return ornament(plain, spec.pairing().ornaments(), spec.key(), spec.tempo(), spec.beatsPerBar(), spec.feel(),
                rng, null);
    }

    public static List<MelodyNote> ornament(List<MelodyNote> plain, SongSpec spec, List<Section> sections, Rng rng) {
        return ornament(plain, spec.pairing().ornaments(), spec.key(), spec.tempo(), spec.beatsPerBar(), spec.feel(),
                rng, sections);
    }

    /**
     * @param sections the song's harmony, so ornaments follow chromatic chord tones; null uses the plain key scale
     */
    public static List<MelodyNote> ornament(List<MelodyNote> plain, OrnamentStyle style, Key key, int tempo,
                                            int beatsPerBar, Feel feel, Rng rng, List<Section> sections) {
        Scale keyScale = key.scale();
        LongFunction<Scale> scaleAt = tick -> sections == null ? keyScale : keyScale.forChord(chordAt(sections, tick));
        long ticksPerBar = PPQ * beatsPerBar;
        // Runs move in 16ths, or 8th-note triplets under a swing feel.
        long runUnit = feel == Feel.SWING || feel == Feel.SHUFFLE ? PPQ / 3 : PPQ / 4;
        // Trills are 32nds at slow tempos, 16th-note triplets otherwise.
        long trillUnit = tempo < 100 ? PPQ / 8 : PPQ / 6;

        List<MelodyNote> out = new ArrayList<>();
        for (int i = 0; i < plain.size(); i++) {
            MelodyNote n = plain.get(i);
            MelodyNote next = i + 1 < plain.size() ? plain.get(i + 1) : null;

            if (rng.chance(style.grace())) {
                addGrace(out, n, scaleAt.apply(n.startTick()), rng.chance(0.5));
            }
            if (next != null && next.startTick() == n.endTick()
                    && !MelodyPlanner.isStrongBeat(next.startTick(), ticksPerBar, beatsPerBar)
                    && sameBar(n.startTick(), next.endTick() - 1, ticksPerBar)
                    && rng.chance(style.merge())) {
                out.add(new MelodyNote(n.pitch(), n.startTick(), n.durationTicks() + next.durationTicks(),
                        n.velocity()));
                i++; // the next note is absorbed
                continue;
            }

            // Ornaments that add pitches stay under one chord: a trill or run computed for one chord clashes
            // with the next (Bb trilling against an E7's B natural).
            boolean steady = sameChord(sections, n.startTick(), n.endTick() - 1);
            boolean runSteady = next != null && sameChord(sections, n.startTick(), next.startTick() - 1);

            List<MelodyNote> result = null;
            if (runSteady && rng.chance(style.run())) {
                result = run(n, next, scaleAt.apply(next.startTick() - 1), runUnit, ticksPerBar, rng);
            }
            if (result == null && steady && rng.chance(style.trill())) {
                result = trill(n, scaleAt.apply(n.startTick()), trillUnit);
            }
            if (result == null && steady && next != null && rng.chance(style.passing())) {
                result = passing(n, next, scaleAt.apply(n.startTick() + n.durationTicks() / 2), keyScale,
                        rng.chance(style.chromatic()));
            }
            if (result == null && steady && rng.chance(style.neighbor())) {
                result = neighbor(n, scaleAt.apply(n.startTick() + n.durationTicks() / 2), rng.chance(0.5));
            }
            if (result == null && rng.chance(style.split())) {
                result = split(n);
            }
            if (result == null && rng.chance(style.staccato())) {
                result = staccato(n);
            }
            out.addAll(result != null ? result : List.of(n));
        }
        return List.copyOf(out);
    }

    /** A 32nd-note grace note just before the beat, stolen from the previous note's tail or the rest before it. */
    private static void addGrace(List<MelodyNote> out, MelodyNote n, Scale scale, boolean fromAbove) {
        long start = n.startTick() - GRACE;
        if (start < 0 || Math.floorMod(n.startTick(), PPQ) != 0) {
            return; // only on beats, so the grace sits cleanly in front of the pulse
        }
        int pitch = fromAbove ? scale.step(n.pitch(), 1) : n.pitch() - 1;
        if (!inRange(pitch)) {
            return;
        }
        if (!out.isEmpty()) {
            MelodyNote last = out.get(out.size() - 1);
            if (last.endTick() > start) {
                if (last.durationTicks() < PPQ / 2 || last.startTick() >= start) {
                    return; // no room to steal
                }
                out.set(out.size() - 1,
                        new MelodyNote(last.pitch(), last.startTick(), start - last.startTick(), last.velocity()));
            }
        }
        out.add(new MelodyNote(pitch, start, GRACE, n.velocity() - 15));
    }

    /** A scale run that fills the end of this note (and any rest) and lands on the next note. */
    private static List<MelodyNote> run(MelodyNote n, MelodyNote next, Scale scale, long unit, long ticksPerBar,
                                        Rng rng) {
        if (next.pitch() == n.pitch()) {
            return null;
        }
        long room = next.startTick() - n.startTick();
        // Fill only the scale tones strictly between the two notes, keeping at least one unit of the original note.
        int between = scaleDistance(scale, n.pitch(), next.pitch()) - 1;
        int maxNotes = (int) Math.min(Math.min(6, room / unit - 1), between);
        if (maxNotes < 2) {
            return null;
        }
        int count = rng.range(2, maxNotes);
        long runStart = next.startTick() - count * unit;
        // The run lives inside this note's bar, so it never displaces a downbeat.
        if (!sameBar(n.startTick(), next.startTick() - 1, ticksPerBar)) {
            return null;
        }
        boolean up = next.pitch() > n.pitch();
        List<MelodyNote> notes = new ArrayList<>();
        notes.add(new MelodyNote(n.pitch(), n.startTick(), Math.min(n.durationTicks(), runStart - n.startTick()),
                n.velocity()));
        for (int j = 0; j < count; j++) {
            int distance = count - j;
            int pitch = scale.step(next.pitch(), up ? -distance : distance);
            if (!inRange(pitch)) {
                return null;
            }
            notes.add(new MelodyNote(pitch, runStart + j * unit, unit, n.velocity() - 10));
        }
        return notes;
    }

    /** Alternates with the upper neighbor for the first two thirds of a long note, then holds the main note. */
    private static List<MelodyNote> trill(MelodyNote n, Scale scale, long unit) {
        if (n.durationTicks() < PPQ * 3 / 2) {
            return null;
        }
        int upper = scale.step(n.pitch(), 1);
        if (!inRange(upper)) {
            return null;
        }
        long trillLength = (n.durationTicks() * 2 / 3) / (2 * unit) * (2 * unit);
        List<MelodyNote> notes = new ArrayList<>();
        for (long t = 0; t < trillLength; t += unit) {
            boolean main = (t / unit) % 2 == 0;
            notes.add(new MelodyNote(main ? n.pitch() : upper, n.startTick() + t, unit,
                    main ? n.velocity() : n.velocity() - 8));
        }
        notes.add(new MelodyNote(n.pitch(), n.startTick() + trillLength, n.durationTicks() - trillLength,
                n.velocity()));
        return notes;
    }

    /** Second half of the note becomes the tone between it and the next note (diatonic, or chromatic). */
    private static List<MelodyNote> passing(MelodyNote n, MelodyNote next, Scale scale, Scale keyScale,
                                            boolean chromatic) {
        if (n.durationTicks() < PPQ) {
            return null;
        }
        int interval = next.pitch() - n.pitch();
        int dir = Integer.signum(interval);
        int pitch;
        if (Math.abs(interval) == 3 || Math.abs(interval) == 4) {
            pitch = scale.step(n.pitch(), dir);
        } else if (Math.abs(interval) == 2 && chromatic) {
            pitch = n.pitch() + dir;
        } else {
            return null;
        }
        if (pitch == n.pitch() || pitch == next.pitch()) {
            return null;
        }
        if (keyScale.contains(pitch) && !scale.contains(pitch)) {
            return null; // the chord altered this note (G# in E7), so the key's version (G) would clash
        }
        long half = n.durationTicks() / 2;
        return List.of(
                new MelodyNote(n.pitch(), n.startTick(), half, n.velocity()),
                new MelodyNote(pitch, n.startTick() + half, n.durationTicks() - half, n.velocity() - 8));
    }

    /** Main - neighbor - main. */
    private static List<MelodyNote> neighbor(MelodyNote n, Scale scale, boolean upper) {
        if (n.durationTicks() < PPQ) {
            return null;
        }
        int pitch = scale.step(n.pitch(), upper ? 1 : -1);
        if (!inRange(pitch)) {
            return null;
        }
        long a = n.durationTicks() / 2;
        long b = n.durationTicks() / 4;
        return List.of(
                new MelodyNote(n.pitch(), n.startTick(), a, n.velocity()),
                new MelodyNote(pitch, n.startTick() + a, b, n.velocity() - 8),
                new MelodyNote(n.pitch(), n.startTick() + a + b, n.durationTicks() - a - b, n.velocity() - 4));
    }

    /** Two repeated notes of half the length. */
    private static List<MelodyNote> split(MelodyNote n) {
        if (n.durationTicks() < PPQ) {
            return null;
        }
        long half = n.durationTicks() / 2;
        return List.of(
                new MelodyNote(n.pitch(), n.startTick(), half, n.velocity()),
                new MelodyNote(n.pitch(), n.startTick() + half, n.durationTicks() - half, n.velocity() - 6));
    }

    /** Half length, leaving a rest. */
    private static List<MelodyNote> staccato(MelodyNote n) {
        if (n.durationTicks() < PPQ / 2) {
            return null;
        }
        return List.of(new MelodyNote(n.pitch(), n.startTick(), n.durationTicks() / 2, n.velocity()));
    }

    /** Number of scale steps from one pitch to another (direction ignored). */
    private static int scaleDistance(Scale scale, int from, int to) {
        int dir = Integer.signum(to - from);
        int steps = 0;
        for (int p = scale.snap(from); dir > 0 ? p < to : p > to; p = scale.step(p, dir)) {
            steps++;
        }
        return steps;
    }

    private static boolean sameChord(List<Section> sections, long from, long to) {
        return sections == null || chordAt(sections, from).equals(chordAt(sections, to));
    }

    private static Chord chordAt(List<Section> sections, long tick) {
        for (Section s : sections) {
            if (tick >= s.startTick() && tick < s.endTick()) {
                return MelodyPlanner.chordAt(s, tick);
            }
        }
        Section last = sections.get(sections.size() - 1);
        return MelodyPlanner.chordAt(last, last.endTick() - 1);
    }

    private static boolean sameBar(long a, long b, long ticksPerBar) {
        return a / ticksPerBar == b / ticksPerBar;
    }

    private static boolean inRange(int pitch) {
        return pitch >= MelodyPlanner.MIN_PITCH && pitch <= MelodyPlanner.MAX_PITCH;
    }
}
