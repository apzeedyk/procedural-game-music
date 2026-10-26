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

    /**
     * Semitones above the tonic that would erase this mode's character if a chord used them: Mixolydian loses its
     * flat 7th to a leading tone, Lydian its #4 to a plain 4th, Dorian its natural 6th and flat 7th to b6 or a
     * leading tone, Phrygian its b2 to a plain 2nd. Major and minor keys have none (V7 is idiomatic there).
     */
    public int[] avoid() {
        return switch (this) {
            case MIXOLYDIAN -> new int[]{11};
            case DORIAN -> new int[]{8, 11};
            case LYDIAN -> new int[]{5};
            case PHRYGIAN -> new int[]{2};
            default -> new int[0];
        };
    }

    /** True if the mode has a minor third (used for picking i vs I in generic progressions). */
    public boolean isMinor() {
        return intervals[2] == 3;
    }

    public String displayName() {
        return displayName;
    }
}
