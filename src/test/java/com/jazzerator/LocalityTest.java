package com.jazzerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.harmony.HarmonyDecorator;
import com.jazzerator.harmony.HarmonyStyle;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.melody.OrnamentStyle;
import com.jazzerator.melody.Ornamenter;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Feel;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Mode;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Random choices are keyed by what is decided and where, so a change in one place can't reshuffle the rest of a
 * song. These tests change one input and check that everything not musically connected to it stays identical.
 */
class LocalityTest {
    private static final long BAR = MidiWriter.PPQ * 4L;
    private static final Key C = Key.of("C", Mode.IONIAN);

    @Test
    void streamsAreStableAndIndependent() {
        Rng a = new Rng(42);
        assertEquals(a.fork("x", 1).range(0, 1_000_000), new Rng(42).fork("x", 1).range(0, 1_000_000));
        assertNotEquals(a.fork("x", 1).range(0, 1_000_000), a.fork("x", 2).range(0, 1_000_000));
        assertNotEquals(a.fork("x", 1).range(0, 1_000_000), new Rng(43).fork("x", 1).range(0, 1_000_000));
    }

    /** Changing bar 3 may affect its neighbors (bars 2-5) but never bar 1 or bars 6-8. */
    @Test
    void changingOneChordOnlyAffectsItsNeighbors() {
        String[] original = {"I", "V", "vi", "IV", "I", "V", "vi", "IV"};
        String[] changed = original.clone();
        changed[2] = "ii";
        for (long seed = 0; seed < 200; seed++) {
            List<ChordSlot> a = HarmonyDecorator.decorate(bars(original), C, HarmonyStyle.JAZZY, new Rng(seed));
            List<ChordSlot> b = HarmonyDecorator.decorate(bars(changed), C, HarmonyStyle.JAZZY, new Rng(seed));
            assertEquals(inBars(a, 0, 1), inBars(b, 0, 1), "seed " + seed + ": bar 1 changed");
            assertEquals(inBars(a, 5, 8), inBars(b, 5, 8), "seed " + seed + ": bars 6-8 changed");
        }
    }

    /** Changing one melody note only affects the ornaments on that note and the note before it. */
    @Test
    void changingOneNoteOnlyAffectsNearbyOrnaments() {
        long q = MidiWriter.PPQ;
        int[] pitches = {72, 74, 76, 77, 79, 77, 76, 74, 72, 74, 76, 79, 81, 79, 77, 76};
        List<MelodyNote> original = new ArrayList<>();
        for (int i = 0; i < pitches.length; i++) {
            original.add(new MelodyNote(pitches[i], i * q, q, 90));
        }
        List<MelodyNote> changed = new ArrayList<>(original);
        changed.set(5, new MelodyNote(72, 5 * q, q, 90));
        OrnamentStyle busy = new OrnamentStyle(0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.1, 0.2, 0.3);
        for (long seed = 0; seed < 200; seed++) {
            List<MelodyNote> a = Ornamenter.ornament(original, busy, C, 120, 4, Feel.STRAIGHT, new Rng(seed), null);
            List<MelodyNote> b = Ornamenter.ornament(changed, busy, C, 120, 4, Feel.STRAIGHT, new Rng(seed), null);
            // Notes 0-3 (before the change and its predecessor) and 7+ (after the next note) are unaffected.
            assertEquals(between(a, 0, 4 * q), between(b, 0, 4 * q), "seed " + seed + ": early notes changed");
            assertEquals(between(a, 7 * q, 16 * q), between(b, 7 * q, 16 * q), "seed " + seed + ": late notes changed");
        }
    }

    private static List<ChordSlot> bars(String[] numerals) {
        List<ChordSlot> slots = new ArrayList<>();
        for (int i = 0; i < numerals.length; i++) {
            slots.add(new ChordSlot(C.chord(numerals[i]), i * BAR, BAR));
        }
        return slots;
    }

    private static List<ChordSlot> inBars(List<ChordSlot> slots, int fromBar, int toBar) {
        return slots.stream().filter(s -> s.startTick() >= fromBar * BAR && s.startTick() < toBar * BAR).toList();
    }

    private static List<MelodyNote> between(List<MelodyNote> notes, long from, long to) {
        return notes.stream().filter(n -> n.startTick() >= from - MidiWriter.PPQ / 8 && n.startTick() < to).toList();
    }
}
