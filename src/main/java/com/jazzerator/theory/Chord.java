package com.jazzerator.theory;

import java.util.ArrayList;
import java.util.List;

/**
 * A chord: root pitch class, quality, optional added 9th, and inversion (0 = root position).
 */
public record Chord(int root, Quality quality, boolean add9, int inversion) {

    public enum Quality {
        MAJ("", 0, 4, 7),
        MIN("m", 0, 3, 7),
        DIM("dim", 0, 3, 6),
        AUG("aug", 0, 4, 8),
        SUS2("sus2", 0, 2, 7),
        SUS4("sus4", 0, 5, 7),
        DOM7("7", 0, 4, 7, 10),
        MAJ7("maj7", 0, 4, 7, 11),
        MIN7("m7", 0, 3, 7, 10),
        HALF_DIM7("m7b5", 0, 3, 6, 10),
        DIM7("dim7", 0, 3, 6, 9);

        private final String suffix;
        private final int[] intervals;

        Quality(String suffix, int... intervals) {
            this.suffix = suffix;
            this.intervals = intervals;
        }

        public int[] intervals() {
            return intervals.clone();
        }

        public String suffix() {
            return suffix;
        }
    }

    public Chord {
        root = Note.pc(root);
    }

    public Chord(int root, Quality quality) {
        this(root, quality, false, 0);
    }

    /** Number of distinct notes in the voicing. */
    public int size() {
        return quality.intervals.length + (add9 ? 1 : 0);
    }

    /** Pitch classes of all chord tones, root first. */
    public List<Integer> pitchClasses() {
        List<Integer> pcs = new ArrayList<>();
        for (int interval : quality.intervals) {
            pcs.add(Note.pc(root + interval));
        }
        if (add9) {
            pcs.add(Note.pc(root + 2));
        }
        return pcs;
    }

    public boolean containsPc(int midiOrPc) {
        return pitchClasses().contains(Note.pc(midiOrPc));
    }

    /** The pitch class sounding in the bass given the inversion. */
    public int bassPc() {
        List<Integer> pcs = pitchClasses();
        return pcs.get(Math.floorMod(inversion, pcs.size()));
    }

    /**
     * Close voicing whose lowest note is the first bass pitch at or above {@code floor}.
     * Inversions rotate the lowest notes up an octave.
     */
    public int[] voicing(int floor) {
        List<Integer> notes = new ArrayList<>();
        int rootMidi = Note.atOrAbove(root, floor);
        for (int interval : quality.intervals) {
            notes.add(rootMidi + interval);
        }
        if (add9) {
            notes.add(rootMidi + 14);
        }
        // Raise every other tone above the inversion's bass note, then sort into a close stack.
        int bass = notes.get(Math.floorMod(inversion, notes.size()));
        for (int i = 0; i < notes.size(); i++) {
            int n = notes.get(i);
            while (n < bass) {
                n += 12;
            }
            notes.set(i, n);
        }
        notes.sort(null);
        // Shift so the bass sits at or just above the floor.
        int shift = bass - Note.atOrAbove(bass, floor);
        return notes.stream().mapToInt(n -> n - shift).toArray();
    }

    public Chord withInversion(int newInversion) {
        return new Chord(root, quality, add9, newInversion);
    }

    public Chord withQuality(Quality newQuality) {
        return new Chord(root, newQuality, add9, inversion);
    }

    public Chord withAdd9(boolean newAdd9) {
        return new Chord(root, quality, newAdd9, inversion);
    }

    public String name(boolean flats) {
        StringBuilder sb = new StringBuilder(Note.name(root, flats)).append(quality.suffix);
        if (add9) {
            sb.append(quality.intervals.length == 4 ? "(9)" : "add9");
        }
        if (Math.floorMod(inversion, size()) != 0) {
            sb.append('/').append(Note.name(bassPc(), flats));
        }
        return sb.toString();
    }
}
