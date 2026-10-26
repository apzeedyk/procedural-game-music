package com.jazzerator.melody;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Note;
import com.jazzerator.theory.Scale;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test for "out of key" clashes: a melody or harmony note from the key's scale sounding a half step
 * from a chromatic chord tone (G natural over an E7 that contains G#). Grace notes are exempt: a quick chromatic
 * lower neighbor (G into G#) is idiomatic.
 */
class CrossRelationTest {
    @Test
    void noKeyScaleNoteClashesWithAChromaticChordTone() {
        for (long seed = 0; seed < 500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Scale scale = song.spec().key().scale();
            List<MelodyNote> harmony = Harmonizer.thirdsBelow(song.melody(), song.sections(), scale,
                    song.spec().beatsPerBar());
            check(song, song.melody(), scale, seed, "lead");
            check(song, harmony, scale, seed, "harmony");
        }
    }

    private static void check(Song song, List<MelodyNote> notes, Scale scale, long seed, String voice) {
        for (MelodyNote n : notes) {
            if (n.durationTicks() <= MidiWriter.PPQ / 8) {
                continue;
            }
            Section section = song.sections().stream()
                    .filter(s -> n.startTick() >= s.startTick() && n.startTick() < s.endTick())
                    .findFirst().orElse(null);
            if (section == null) {
                continue;
            }
            Chord chord = MelodyPlanner.chordAt(section, n.startTick());
            int pc = Note.pc(n.pitch());
            if (chord.containsPc(pc)) {
                continue;
            }
            int[] intervals = chord.quality().intervals();
            for (int i = 0; i < intervals.length; i++) {
                int tone = Note.pc(chord.root() + intervals[i]);
                if (scale.contains(tone)) {
                    continue;
                }
                assertTrue(pc != unaltered(scale, chord, intervals[i]),
                        "seed " + seed + " " + voice + " " + Note.name(pc, false) + " against "
                                + Note.name(tone, false) + " in " + chord.name(false) + " at tick " + n.startTick());
            }
        }
    }

    /**
     * The key's own version of a chromatic chord tone, i.e. the same note letter: the 3rd of E7 in A Mixolydian
     * is G# and its unaltered version is G. Chromatic roots (bVI, bVII, tritone subs) are flats, so theirs is a
     * half step up.
     */
    private static int unaltered(Scale scale, Chord chord, int interval) {
        if (interval == 0) {
            return Note.pc(chord.root() + 1);
        }
        int steps = switch (interval) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            case 6, 7, 8 -> 4;
            case 9 -> chord.quality() == Chord.Quality.DIM7 ? 6 : 5;
            default -> 6;
        };
        return Note.pc(scale.step(chord.root() + 60, steps));
    }
}
