package com.jazzerator.theory;

import java.util.Arrays;

/** A 7-note scale rooted on a pitch class, with helpers for moving through it in MIDI space. */
public final class Scale {
    private final int tonic;
    private final Mode mode;
    private final int[] pitchClasses;

    public Scale(int tonic, Mode mode) {
        this.tonic = Note.pc(tonic);
        this.mode = mode;
        this.pitchClasses = Arrays.stream(mode.intervals()).map(i -> Note.pc(this.tonic + i)).toArray();
    }

    public int tonic() {
        return tonic;
    }

    public Mode mode() {
        return mode;
    }

    /** Pitch classes in degree order starting from the tonic. */
    public int[] pitchClasses() {
        return pitchClasses.clone();
    }

    public boolean contains(int midi) {
        return degreeOf(midi) >= 0;
    }

    /** 0-based scale degree of the note, or -1 if it is not in the scale. */
    public int degreeOf(int midi) {
        int pc = Note.pc(midi);
        for (int i = 0; i < pitchClasses.length; i++) {
            if (pitchClasses[i] == pc) {
                return i;
            }
        }
        return -1;
    }

    /** Nearest scale tone to the given note; ties resolve downward. */
    public int snap(int midi) {
        for (int d = 0; d < 12; d++) {
            if (contains(midi - d)) {
                return midi - d;
            }
            if (contains(midi + d)) {
                return midi + d;
            }
        }
        throw new IllegalStateException("Empty scale");
    }

    /** Moves the given note by a number of scale steps (positive = up). Off-scale notes are snapped first. */
    public int step(int midi, int steps) {
        int note = snap(midi);
        int direction = Integer.signum(steps);
        for (int i = 0; i < Math.abs(steps); i++) {
            do {
                note += direction;
            } while (!contains(note));
        }
        return note;
    }
}
