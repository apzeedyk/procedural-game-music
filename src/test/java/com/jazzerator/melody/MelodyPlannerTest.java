package com.jazzerator.melody;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.harmony.HarmonyPlanner;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Feel;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.util.Rng;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MelodyPlannerTest {
    private static final int SEEDS = 500;

    @Test
    void downbeatsAreChordTones() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Song song = SongGenerator.generate(seed, null);
            for (MelodyNote n : song.melody()) {
                Section s = sectionAt(song, n.startTick());
                if (Math.floorMod(n.startTick() - s.startTick(), s.ticksPerBar()) == 0) {
                    assertTrue(MelodyPlanner.chordAt(s, n.startTick()).containsPc(n.pitch()),
                            "seed " + seed + " tick " + n.startTick());
                }
            }
        }
    }

    @Test
    void notesAreOrderedNonOverlappingAndInRange() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Song song = SongGenerator.generate(seed, null);
            assertFalse(song.melody().isEmpty());
            long lastEnd = 0;
            for (MelodyNote n : song.melody()) {
                assertTrue(n.startTick() >= lastEnd, "overlap at seed " + seed);
                assertTrue(n.durationTicks() > 0);
                assertTrue(n.pitch() >= MelodyPlanner.MIN_PITCH && n.pitch() <= MelodyPlanner.MAX_PITCH);
                lastEnd = n.endTick();
            }
            assertTrue(lastEnd <= song.endTick());
        }
    }

    /** Repeats share the plain melody; ornaments are allowed to vary between repeats. */
    @Test
    void repeatedLabelsRepeatTheirPlainMelody() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Rng rng = new Rng(seed);
            SongSpec spec = SongSpec.random(rng, null);
            List<Section> sections = HarmonyPlanner.plan(spec, rng);
            List<MelodyNote> plain = MelodyPlanner.plan(spec, sections, rng);
            Map<String, List<String>> firstByLabel = new HashMap<>();
            for (int i = 0; i < sections.size() - 1; i++) { // the final section's cadence differs
                Section s = sections.get(i);
                List<String> shape = melodyShape(plain, s);
                List<String> first = firstByLabel.putIfAbsent(s.label(), shape);
                if (first != null) {
                    assertEquals(first, shape, "seed " + seed + " label " + s.label());
                }
            }
        }
    }

    @Test
    void introHasNoMelody() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Section first = song.sections().get(0);
            if (first.label().equals("Intro")) {
                // A pickup (e.g. a grace note) may lead into the first section, but nothing earlier.
                long pickupStart = first.endTick() - MidiWriter.PPQ;
                assertTrue(song.melody().stream().noneMatch(n -> n.startTick() < pickupStart));
            }
        }
    }

    @Test
    void motifsFillWholeBars() {
        Rng rng = new Rng(3);
        for (Feel feel : Feel.values()) {
            for (int beats : new int[]{3, 4}) {
                for (int i = 0; i < 100; i++) {
                    Motif m = MotifGenerator.generate(feel, beats, 2, rng);
                    assertEquals(beats * 2, m.beats(), 1e-9);
                    assertEquals(m.rhythm().stream().filter(d -> d > 0).count(), m.steps().size());
                    Motif c = m.withCadence(2);
                    assertEquals(m.beats(), c.beats(), 1e-9);
                    assertEquals(c.rhythm().stream().filter(d -> d > 0).count(), c.steps().size());
                }
            }
        }
    }

    private static Section sectionAt(Song song, long tick) {
        return song.sections().stream().filter(s -> tick >= s.startTick() && tick < s.endTick()).findFirst()
                .orElseThrow();
    }

    /** Pitches and section-relative timing of the melody inside a section. */
    private static List<String> melodyShape(List<MelodyNote> melody, Section s) {
        return melody.stream().filter(n -> n.startTick() >= s.startTick() && n.startTick() < s.endTick())
                .map(n -> n.pitch() + "@" + (n.startTick() - s.startTick()) + ":" + n.durationTicks()).toList();
    }
}
