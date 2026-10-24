package com.jazzerator.melody;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Note;
import com.jazzerator.theory.Scale;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the lead line. Each section label gets its own motif and a phrase plan
 * (motif, repeat, variation, ..., cadence), chosen once so repeated labels sound the same. Realization is
 * deterministic: strong beats land on chord tones, other notes move by scale step.
 */
public final class MelodyPlanner {
    public static final int MIN_PITCH = 62; // D4
    public static final int MAX_PITCH = 86; // D6
    private static final double CADENCE_BEATS = 2.0;
    private static final int LEAP = 5; // semitones; a 4th or wider counts as a leap

    private MelodyPlanner() {
    }

    public static List<MelodyNote> plan(SongSpec spec, List<Section> sections, Rng rng) {
        int beatsPerBar = spec.beatsPerBar();
        Map<String, List<Motif>> phrasePlans = new HashMap<>();
        List<MelodyNote> melody = new ArrayList<>();
        for (Section section : sections) {
            if (section.label().equals("Intro")) {
                continue; // the intro is accompaniment only
            }
            int motifBars = section.bars() >= 8 ? 2 : 1;
            List<Motif> phrases = phrasePlans.computeIfAbsent(section.label(),
                    l -> phrasePlan(spec, section.bars() / motifBars, motifBars, rng));
            int pitch = center(section.label());
            long tick = section.startTick();
            for (Motif phrase : phrases) {
                pitch = realize(phrase, tick, section, spec.key().scale(), beatsPerBar, pitch, melody);
                tick += motifBars * section.ticksPerBar();
            }
        }
        return List.copyOf(melody);
    }

    /** Motif first, alternating repeats and variations, ending with a cadence. */
    private static List<Motif> phrasePlan(SongSpec spec, int phraseCount, int motifBars, Rng rng) {
        Motif motif = MotifGenerator.generate(spec.feel(), spec.beatsPerBar(), motifBars, rng);
        List<Motif> plan = new ArrayList<>();
        for (int i = 0; i < phraseCount; i++) {
            Motif phrase;
            if (i == 0 || i % 2 == 1) {
                phrase = motif;
            } else {
                phrase = vary(motif, rng);
            }
            if (i == phraseCount - 1) {
                phrase = phrase.withCadence(Math.min(CADENCE_BEATS, spec.beatsPerBar()));
            }
            plan.add(phrase);
        }
        return plan;
    }

    private static Motif vary(Motif motif, Rng rng) {
        if (rng.chance(0.3)) {
            return motif.inverted();
        }
        Motif varied = motif;
        int mutations = rng.range(1, 2);
        for (int m = 0; m < mutations; m++) {
            int index = rng.range(0, motif.steps().size() - 1);
            varied = varied.withStep(index, varied.steps().get(index) + (rng.chance(0.5) ? 1 : -1));
        }
        return varied;
    }

    /** Main sections sit mid-register; contrasting sections sit higher. */
    private static int center(String label) {
        return switch (label) {
            case "B", "Chorus", "Bridge" -> 76;
            case "C" -> 74;
            default -> 71;
        };
    }

    /** Writes one phrase into {@code out}; returns the last pitch so the next phrase continues smoothly. */
    private static int realize(Motif motif, long startTick, Section section, Scale scale, int beatsPerBar,
                               int previousPitch, List<MelodyNote> out) {
        long tick = startTick;
        int pitch = previousPitch;
        int lastInterval = 0;
        int noteIndex = 0;
        int noteCount = motif.steps().size();
        for (double beats : motif.rhythm()) {
            long ticks = Math.round(Math.abs(beats) * MidiWriter.PPQ);
            if (beats < 0) {
                tick += ticks;
                continue;
            }
            Chord chord = chordAt(section, tick);
            int step = motif.steps().get(noteIndex);
            if (Math.abs(lastInterval) >= LEAP && Integer.signum(step) == Integer.signum(lastInterval)) {
                step = -Integer.signum(step); // leap recovery: step back against a large leap
            }
            int target = scale.step(pitch, step);
            if (target > MAX_PITCH || target < MIN_PITCH) {
                target = scale.step(pitch, -step); // bounce off the edge of the range
            }
            if (Math.abs(target - pitch) == 6) {
                target = scale.step(target, -Integer.signum(step)); // no tritone leaps
            }
            boolean strong = isStrongBeat(tick - section.startTick(), section.ticksPerBar(), beatsPerBar);
            boolean cadence = noteIndex == noteCount - 1 && ticks >= MidiWriter.PPQ * 2L;
            if (cadence) {
                target = nearestWithPc(target, chord.root());
            } else if (strong) {
                target = nearestChordTone(target, chord);
            }
            target = clamp(target);
            out.add(new MelodyNote(target, tick, ticks, strong ? 96 : 82));
            lastInterval = target - pitch;
            pitch = target;
            tick += ticks;
            noteIndex++;
        }
        return pitch;
    }

    static boolean isStrongBeat(long tickInSection, long ticksPerBar, int beatsPerBar) {
        long inBar = Math.floorMod(tickInSection, ticksPerBar);
        if (inBar % MidiWriter.PPQ != 0) {
            return false;
        }
        long beat = inBar / MidiWriter.PPQ;
        return beat == 0 || (beatsPerBar == 4 && beat == 2);
    }

    static Chord chordAt(Section section, long tick) {
        for (ChordSlot slot : section.chords()) {
            if (tick >= slot.startTick() && tick < slot.endTick()) {
                return slot.chord();
            }
        }
        return section.chords().get(section.chords().size() - 1).chord();
    }

    private static int nearestChordTone(int target, Chord chord) {
        for (int d = 0; d <= 6; d++) {
            if (chord.containsPc(target - d)) {
                return target - d;
            }
            if (chord.containsPc(target + d)) {
                return target + d;
            }
        }
        return target;
    }

    private static int nearestWithPc(int target, int pc) {
        int below = target - Math.floorMod(target - pc, 12);
        int above = below + 12;
        return target - below <= above - target ? below : above;
    }

    /** Octave-shifts into range; chord tones stay chord tones. */
    private static int clamp(int pitch) {
        while (pitch > MAX_PITCH) {
            pitch -= 12;
        }
        while (pitch < MIN_PITCH) {
            pitch += 12;
        }
        return pitch;
    }
}
