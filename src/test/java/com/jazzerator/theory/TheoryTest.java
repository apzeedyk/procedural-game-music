package com.jazzerator.theory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.theory.Chord.Quality;
import java.util.List;
import org.junit.jupiter.api.Test;

class TheoryTest {
    private static List<Integer> pcs(String... names) {
        return java.util.Arrays.stream(names).map(Note::parse).toList();
    }

    @Test
    void noteParsingAndNaming() {
        assertEquals(0, Note.parse("C"));
        assertEquals(10, Note.parse("Bb"));
        assertEquals(6, Note.parse("F#"));
        assertEquals(11, Note.parse("Cb"));
        assertEquals("Eb", Note.name(3, true));
        assertEquals("D#", Note.name(3, false));
    }

    @Test
    void dDorianScale() {
        Scale s = Key.of("D", Mode.DORIAN).scale();
        assertArrayEquals(pcs("D", "E", "F", "G", "A", "B", "C").stream().mapToInt(i -> i).toArray(), s.pitchClasses());
    }

    @Test
    void harmonicMinorHasRaisedSeventh() {
        Scale s = Key.of("A", Mode.HARMONIC_MINOR).scale();
        assertTrue(s.contains(Note.parse("G#")));
        assertFalse(s.contains(Note.parse("G")));
    }

    @Test
    void scaleStepAndSnap() {
        Scale c = Key.of("C", Mode.IONIAN).scale();
        assertEquals(64, c.step(60, 2));  // C -> E
        assertEquals(59, c.step(60, -1)); // C -> B
        assertEquals(72, c.step(60, 7));  // octave
        assertEquals(60, c.snap(61));     // C# snaps down to C
    }

    @Test
    void keySignatureSpelling() {
        assertTrue(Key.of("D", Mode.AEOLIAN).prefersFlats());   // D minor = 1 flat
        assertFalse(Key.of("E", Mode.AEOLIAN).prefersFlats());  // E minor = 1 sharp
        assertEquals("Bb Ionian", Key.of("Bb", Mode.IONIAN).name());
    }

    @Test
    void iiSevenInF() {
        Chord c = Key.of("F", Mode.IONIAN).chord("ii7");
        assertEquals(Quality.MIN7, c.quality());
        assertEquals(pcs("G", "Bb", "D", "F"), c.pitchClasses());
        assertEquals("Gm7", c.name(true));
    }

    @Test
    void flatSevenAndMinorNumerals() {
        Key c = Key.of("C", Mode.AEOLIAN);
        assertEquals(pcs("Bb", "D", "F"), c.chord("bVII").pitchClasses());
        assertEquals(pcs("Ab", "C", "Eb"), c.chord("bVI").pitchClasses());
        assertEquals(pcs("C", "Eb", "G"), c.chord("i").pitchClasses());
    }

    @Test
    void secondaryDominant() {
        Chord c = Key.of("C", Mode.IONIAN).chord("V7/V");
        assertEquals(pcs("D", "F#", "A", "C"), c.pitchClasses());
        Chord d = Key.of("C", Mode.IONIAN).chord("viio7/ii");
        assertEquals(pcs("C#", "E", "G", "Bb"), d.pitchClasses());
    }

    @Test
    void suffixes() {
        Key c = Key.of("C", Mode.IONIAN);
        assertEquals(Quality.HALF_DIM7, c.chord("viiø7").quality());
        assertEquals(Quality.DIM, c.chord("vii°").quality());
        assertEquals(Quality.SUS4, c.chord("Vsus4").quality());
        assertEquals(Quality.MAJ7, c.chord("IVmaj7").quality());
        assertTrue(c.chord("Iadd9").add9());
        assertThrows(IllegalArgumentException.class, () -> c.chord("Vx"));
        assertThrows(IllegalArgumentException.class, () -> c.chord("VIII"));
    }

    @Test
    void inversionsPutCorrectNoteInBass() {
        Chord c = new Chord(Note.parse("C"), Quality.MAJ);
        assertArrayEquals(new int[]{48, 52, 55}, c.voicing(48));
        int[] first = c.withInversion(1).voicing(48);
        assertEquals(Note.parse("E"), Note.pc(first[0]));
        assertTrue(first[0] >= 48 && first[0] < 60);
        int[] second = c.withInversion(2).voicing(48);
        assertEquals(Note.parse("G"), Note.pc(second[0]));
        assertEquals("C/G", c.withInversion(2).name(false));
    }

    @Test
    void voicingIsAscending() {
        Chord c = Key.of("A", Mode.IONIAN).chord("V9").withInversion(3);
        int[] v = c.voicing(40);
        for (int i = 1; i < v.length; i++) {
            assertTrue(v[i] > v[i - 1]);
        }
    }
}
