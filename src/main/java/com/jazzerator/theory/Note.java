package com.jazzerator.theory;

/** Pitch-class and MIDI note helpers. Pitch classes are 0..11 with C = 0. */
public final class Note {
    private static final String[] SHARP_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] FLAT_NAMES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    private static final int[] LETTER_PCS = {9, 11, 0, 2, 4, 5, 7}; // A B C D E F G

    private Note() {
    }

    public static int pc(int midiOrPc) {
        return Math.floorMod(midiOrPc, 12);
    }

    /** Parses names like "C", "F#", "Bb". */
    public static int parse(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Empty note name");
        }
        char letter = Character.toUpperCase(name.charAt(0));
        if (letter < 'A' || letter > 'G') {
            throw new IllegalArgumentException("Bad note name: " + name);
        }
        int pc = LETTER_PCS[letter - 'A'];
        for (int i = 1; i < name.length(); i++) {
            switch (name.charAt(i)) {
                case '#' -> pc++;
                case 'b' -> pc--;
                default -> throw new IllegalArgumentException("Bad note name: " + name);
            }
        }
        return pc(pc);
    }

    public static String name(int pc, boolean flats) {
        return (flats ? FLAT_NAMES : SHARP_NAMES)[pc(pc)];
    }

    /** The lowest MIDI note >= floor whose pitch class is pc. */
    public static int atOrAbove(int pc, int floor) {
        return floor + Math.floorMod(pc - floor, 12);
    }
}
