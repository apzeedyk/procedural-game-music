package com.jazzerator.melody;

import com.jazzerator.form.Section;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Scale;
import java.util.ArrayList;
import java.util.List;

/**
 * A second voice that shadows the melody in parallel diatonic thirds below, the classic horn-section or
 * twin-guitar sound. Notes on strong beats and long notes are adjusted to a chord tone so the pair never clashes
 * with the harmony.
 */
public final class Harmonizer {
    /** D4: below this the second voice would sink into the accompaniment and double the piano. */
    public static final int MIN_PITCH = 62;
    private static final int VELOCITY_DROP = 14;

    private Harmonizer() {
    }

    public static List<MelodyNote> thirdsBelow(List<MelodyNote> melody, List<Section> sections, Scale scale,
                                               int beatsPerBar) {
        List<MelodyNote> out = new ArrayList<>();
        for (MelodyNote n : melody) {
            Section section = sectionAt(sections, n.startTick());
            if (section == null) {
                continue;
            }
            Chord chord = MelodyPlanner.chordAt(section, n.startTick());
            int pitch = scale.forChord(chord).step(n.pitch(), -2);
            boolean structural = n.durationTicks() >= MidiWriter.PPQ
                    || MelodyPlanner.isStrongBeat(n.startTick() - section.startTick(), section.ticksPerBar(),
                    beatsPerBar);
            if (structural) {
                if (!chord.containsPc(pitch)) {
                    pitch = chordToneBelow(chord, n.pitch());
                }
            }
            if (pitch < MIN_PITCH || pitch >= n.pitch()) {
                continue; // too low (or degenerate): leave the melody alone here
            }
            out.add(new MelodyNote(pitch, n.startTick(), n.durationTicks(), Math.max(1, n.velocity() - VELOCITY_DROP)));
        }
        return out;
    }

    /** The highest chord tone a third to a sixth below the melody note. */
    private static int chordToneBelow(Chord chord, int melody) {
        for (int p = melody - 3; p >= melody - 9; p--) {
            if (chord.containsPc(p)) {
                return p;
            }
        }
        return melody - 12; // octave doubling as a last resort
    }

    private static Section sectionAt(List<Section> sections, long tick) {
        for (Section s : sections) {
            if (tick >= s.startTick() && tick < s.endTick()) {
                return s;
            }
        }
        // Grace notes may lead in just before the first section with a melody.
        return sections.stream().filter(s -> s.startTick() > tick).findFirst().orElse(null);
    }
}
