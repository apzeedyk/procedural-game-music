package com.jazzerator.theory;

public enum Mode {
    IONIAN("Ionian", 0, 0, 2, 4, 5, 7, 9, 11),
    DORIAN("Dorian", 2, 0, 2, 3, 5, 7, 9, 10),
    PHRYGIAN("Phrygian", 4, 0, 1, 3, 5, 7, 8, 10),
    LYDIAN("Lydian", 5, 0, 2, 4, 6, 7, 9, 11),
    MIXOLYDIAN("Mixolydian", 7, 0, 2, 4, 5, 7, 9, 10),
    AEOLIAN("Aeolian", 9, 0, 2, 3, 5, 7, 8, 10),
    LOCRIAN("Locrian", 11, 0, 1, 3, 5, 6, 8, 10),
    HARMONIC_MINOR("Harmonic Minor", 9, 0, 2, 3, 5, 7, 8, 11);

    private final String displayName;
    private final int offsetFromIonian;
    private final int[] intervals;

    Mode(String displayName, int offsetFromIonian, int... intervals) {
        this.displayName = displayName;
        this.offsetFromIonian = offsetFromIonian;
        this.intervals = intervals;
    }

    /** Semitones above the tonic for each of the 7 scale degrees. */
    public int[] intervals() {
        return intervals.clone();
    }

    /** Semitones from this mode's tonic back up to its parent major scale's tonic. Used for key-signature spelling. */
    int offsetFromIonian() {
        return offsetFromIonian;
    }

    /** True if the mode has a minor third (used for picking i vs I in generic progressions). */
    public boolean isMinor() {
        return intervals[2] == 3;
    }

    public String displayName() {
        return displayName;
    }
}
