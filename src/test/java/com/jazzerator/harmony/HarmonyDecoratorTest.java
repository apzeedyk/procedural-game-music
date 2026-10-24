package com.jazzerator.harmony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Chord.Quality;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Mode;
import com.jazzerator.theory.Note;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarmonyDecoratorTest {
    private static final long BAR = MidiWriter.PPQ * 4L;
    private static final Key C = Key.of("C", Mode.IONIAN);
    private static final HarmonyStyle ALWAYS = new HarmonyStyle(1, 1, 1, 1, 1, 1, 1, 1);

    private static List<ChordSlot> bars(Key key, String... numerals) {
        List<ChordSlot> slots = new ArrayList<>();
        for (int i = 0; i < numerals.length; i++) {
            slots.add(new ChordSlot(key.chord(numerals[i]), i * BAR, BAR));
        }
        return slots;
    }

    private static HarmonyStyle only(String field) {
        return new HarmonyStyle(
                field.equals("seventh") ? 1 : 0, field.equals("ninth") ? 1 : 0, field.equals("sus") ? 1 : 0,
                field.equals("secdom") ? 1 : 0, field.equals("tritone") ? 1 : 0, field.equals("borrowed") ? 1 : 0,
                field.equals("deceptive") ? 1 : 0, field.equals("inversion") ? 1 : 0);
    }

    @Test
    void noneStyleChangesNothing() {
        List<ChordSlot> plain = bars(C, "I", "V", "vi", "IV");
        assertEquals(plain, HarmonyDecorator.decorate(plain, C, HarmonyStyle.NONE, new Rng(1)));
    }

    @Test
    void decorationsAlwaysTileTheOriginalSpan() {
        String[][] progs = {{"I", "V", "vi", "IV"}, {"ii7", "V7", "Imaj7", "Imaj7"}, {"i", "bVI", "bVII", "i"}};
        HarmonyStyle[] styles = {ALWAYS, HarmonyStyle.JAZZY, HarmonyStyle.LUSH, HarmonyStyle.DEFAULT};
        for (long seed = 0; seed < 200; seed++) {
            for (String[] prog : progs) {
                for (HarmonyStyle style : styles) {
                    List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, prog), C, style, new Rng(seed));
                    long tick = 0;
                    for (ChordSlot s : out) {
                        assertEquals(tick, s.startTick());
                        assertTrue(s.durationTicks() > 0);
                        tick = s.endTick();
                    }
                    assertEquals(prog.length * BAR, tick);
                }
            }
        }
    }

    @Test
    void secondaryDominantSplitsPreviousBar() {
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "I", "vi"), C, only("secdom"), new Rng(1));
        assertEquals(3, out.size());
        assertEquals(new Chord(Note.parse("E"), Quality.DOM7), out.get(1).chord()); // V7/vi
        assertEquals(BAR / 2, out.get(1).startTick());
    }

    @Test
    void tritoneSubstitution() {
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "ii7", "V7", "I"), C, only("tritone"), new Rng(1));
        assertEquals(new Chord(Note.parse("Db"), Quality.DOM7), out.get(1).chord());
    }

    @Test
    void deceptiveCadenceOnlyAtMidpoint() {
        // V -> I ending at the midpoint (bar 2 of 4) becomes V -> vi; the final V -> I is left alone.
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "V", "I", "V", "I"), C, only("deceptive"), new Rng(1));
        assertEquals(C.chord("vi"), out.get(1).chord());
        assertEquals(C.chord("I"), out.get(3).chord());
        Key am = Key.of("A", Mode.AEOLIAN);
        List<ChordSlot> minor = HarmonyDecorator.decorate(bars(am, "V", "i", "V", "i"), am, only("deceptive"), new Rng(1));
        assertEquals(am.chord("bVI"), minor.get(1).chord());
        assertEquals(am.chord("i"), minor.get(3).chord());
    }

    @Test
    void diatonicSevenths() {
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "I", "ii", "V", "vii°"), C, only("seventh"), new Rng(1));
        assertEquals(Quality.MAJ7, out.get(0).chord().quality());
        assertEquals(Quality.MIN7, out.get(1).chord().quality());
        assertEquals(Quality.DOM7, out.get(2).chord().quality());
        assertEquals(Quality.HALF_DIM7, out.get(3).chord().quality());
    }

    @Test
    void borrowedChordsFromParallelMinor() {
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "IV", "vi"), C, only("borrowed"), new Rng(1));
        assertEquals(C.chord("iv"), out.get(0).chord());
        assertEquals(C.chord("bVI"), out.get(1).chord());
    }

    @Test
    void suspensionResolves() {
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "I"), C, only("sus"), new Rng(1));
        assertEquals(Quality.SUS4, out.get(0).chord().quality());
        assertEquals(C.chord("I"), out.get(1).chord());
    }

    @Test
    void inversionMakesBassStepwise() {
        // C -> F: first inversion puts E in the bass, a half step below F.
        List<ChordSlot> out = HarmonyDecorator.decorate(bars(C, "I", "IV"), C, only("inversion"), new Rng(1));
        assertEquals(Note.parse("E"), out.get(0).chord().bassPc());
    }

    @Test
    void chromaticRootsSpelledWithFlats() {
        Key g = Key.of("G", Mode.IONIAN);
        assertEquals("Eb", g.spell(g.chord("bVI")));
        assertEquals("F#m", Key.of("D", Mode.IONIAN).spell(Key.of("D", Mode.IONIAN).chord("iii")));
    }
}
