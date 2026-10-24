package com.jazzerator.harmony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HarmonyPlannerTest {
    @Test
    void totalBarsMatchSpec() {
        for (long seed = 0; seed < 500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            assertEquals(song.spec().totalBars(), song.totalBars(), "seed " + seed);
        }
    }

    @Test
    void chordsTileEachSectionWithoutGaps() {
        for (long seed = 0; seed < 500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            long tick = 0;
            for (Section s : song.sections()) {
                assertEquals(tick, s.startTick());
                for (ChordSlot slot : s.chords()) {
                    assertEquals(tick, slot.startTick(), "seed " + seed);
                    tick = slot.endTick();
                }
                assertEquals(s.endTick(), tick);
            }
        }
    }

    @Test
    void repeatedLabelsShareHarmonyExceptFinalCadence() {
        for (long seed = 0; seed < 500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            List<Section> sections = song.sections();
            Map<String, Section> firstByLabel = new HashMap<>();
            for (int i = 0; i < sections.size() - 1; i++) { // the last section may end on an altered cadence
                Section s = sections.get(i);
                Section first = firstByLabel.putIfAbsent(s.label(), s);
                if (first != null) {
                    assertEquals(chords(first), chords(s), "seed " + seed + " label " + s.label());
                }
            }
        }
    }

    @Test
    void songEndsWithDominantToTonic() {
        for (long seed = 0; seed < 500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Section last = song.sections().get(song.sections().size() - 1);
            int tonic = song.spec().key().tonic();
            List<ChordSlot> chords = last.chords();
            assertEquals(tonic, chords.get(chords.size() - 1).chord().root(), "seed " + seed);
            assertEquals(last.endTick(), chords.get(chords.size() - 1).endTick());
            ChordSlot penultimateBar = slotAt(last, last.endTick() - 2 * last.ticksPerBar());
            assertNotEquals(tonic, penultimateBar.chord().root(), "seed " + seed + " has no approach to the tonic");
        }
    }

    @Test
    void sectionsEndOnTheirCadence() {
        for (long seed = 0; seed < 500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            int tonic = song.spec().key().tonic();
            List<Section> sections = song.sections();
            for (int i = 0; i < sections.size() - 1; i++) {
                Section s = sections.get(i);
                int lastRoot = s.chords().get(s.chords().size() - 1).chord().root();
                if (HarmonyPlanner.cadenceFor(s.label()) == HarmonyPlanner.Cadence.AUTHENTIC) {
                    assertEquals(tonic, lastRoot, "seed " + seed + " " + s.label() + " should resolve");
                } else {
                    assertNotEquals(tonic, lastRoot, "seed " + seed + " " + s.label() + " should end open");
                }
            }
        }
    }

    private static ChordSlot slotAt(Section s, long tick) {
        return s.chords().stream().filter(c -> tick >= c.startTick() && tick < c.endTick()).findFirst().orElseThrow();
    }

    private static List<?> chords(Section s) {
        return s.chords().stream().map(ChordSlot::chord).toList();
    }
}
