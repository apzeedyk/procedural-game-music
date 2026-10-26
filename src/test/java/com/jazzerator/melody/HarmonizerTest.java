package com.jazzerator.melody;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HarmonizerTest {
    @Test
    void harmonyShadowsTheMelodyBelowAndFitsTheChords() {
        int harmonized = 0;
        for (long seed = 0; seed < 300; seed++) {
            Song song = SongGenerator.generate(seed, null);
            int beats = song.spec().beatsPerBar();
            List<MelodyNote> harmony = Harmonizer.thirdsBelow(song.melody(), song.sections(),
                    song.spec().key().scale(), beats);
            Map<Long, MelodyNote> melodyByStart = song.melody().stream()
                    .collect(Collectors.toMap(MelodyNote::startTick, Function.identity(), (a, b) -> a));
            for (MelodyNote h : harmony) {
                MelodyNote m = melodyByStart.get(h.startTick());
                assertTrue(m != null, "harmony note without a melody note, seed " + seed);
                assertEquals(m.durationTicks(), h.durationTicks());
                int gap = m.pitch() - h.pitch();
                assertTrue(gap >= 1 && gap <= 12, "seed " + seed + " gap " + gap);
                assertTrue(h.pitch() >= Harmonizer.MIN_PITCH);
                Section s = song.sections().stream()
                        .filter(x -> h.startTick() >= x.startTick() && h.startTick() < x.endTick())
                        .findFirst().orElse(null);
                boolean structural = s != null && (h.durationTicks() >= MidiWriter.PPQ
                        || MelodyPlanner.isStrongBeat(h.startTick() - s.startTick(), s.ticksPerBar(), beats));
                if (structural) {
                    assertTrue(MelodyPlanner.chordAt(s, h.startTick()).containsPc(h.pitch())
                                    || gap == 12,
                            "seed " + seed + " harmony clashes at tick " + h.startTick());
                }
            }
            harmonized += harmony.size();
        }
        assertTrue(harmonized > 0);
    }

    @Test
    void mostlyThirdsBelow() {
        Song song = SongGenerator.generate(20, com.jazzerator.preset.Presets.byId("overworld").orElseThrow());
        List<MelodyNote> harmony = Harmonizer.thirdsBelow(song.melody(), song.sections(), song.spec().key().scale(),
                song.spec().beatsPerBar());
        assertFalse(harmony.isEmpty());
        Map<Long, Integer> melody = song.melody().stream()
                .collect(Collectors.toMap(MelodyNote::startTick, MelodyNote::pitch, (a, b) -> a));
        long thirds = harmony.stream().filter(h -> {
            int gap = melody.get(h.startTick()) - h.pitch();
            return gap == 3 || gap == 4;
        }).count();
        assertTrue(thirds * 2 > harmony.size(), "only " + thirds + " of " + harmony.size() + " are thirds");
    }
}
