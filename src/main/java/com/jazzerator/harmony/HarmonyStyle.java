package com.jazzerator.harmony;

/**
 * Per-pairing probabilities for each harmonic decoration (0 = never, 1 = wherever it applies).
 *
 * @param majorSevenths whether 7th extensions may be major 7ths, a soft, jazzy color that suits lo-fi and
 *                      pastoral music but sounds out of character in rock, battle, or march music
 */
public record HarmonyStyle(
        double seventh,
        double ninth,
        double suspension,
        double secondaryDominant,
        double tritoneSub,
        double borrowed,
        double deceptive,
        double inversion,
        boolean majorSevenths) {

    public HarmonyStyle(double seventh, double ninth, double suspension, double secondaryDominant, double tritoneSub,
                        double borrowed, double deceptive, double inversion) {
        this(seventh, ninth, suspension, secondaryDominant, tritoneSub, borrowed, deceptive, inversion, true);
    }

    public static final HarmonyStyle NONE = new HarmonyStyle(0, 0, 0, 0, 0, 0, 0, 0);

    /**
     * This style for a modal key: secondary dominants, tritone substitutions and deceptive cadences are tonal
     * devices that make the music "collapse toward the parent major scale" (V7 -> I of another key), so they're off.
     */
    public HarmonyStyle forModalKey() {
        return new HarmonyStyle(seventh, ninth, suspension, 0, 0, borrowed, 0, inversion, majorSevenths);
    }
    public static final HarmonyStyle DEFAULT = new HarmonyStyle(0.25, 0.1, 0.15, 0.15, 0.0, 0.15, 0.3, 0.3);
    /** Jazz and lo-fi: rich extensions, ii-V motion, tritone substitutions. */
    public static final HarmonyStyle JAZZY = new HarmonyStyle(0.9, 0.4, 0.1, 0.4, 0.5, 0.2, 0.4, 0.2);
    /** Battle, racing, chiptune: mostly plain triads and punchy sus chords. */
    public static final HarmonyStyle POWER = new HarmonyStyle(0.05, 0.0, 0.2, 0.1, 0.0, 0.1, 0.2, 0.15, false);
    /** Rock and adventure: triads and bluesy dominant 7ths, roots in the bass. */
    public static final HarmonyStyle ROCK = new HarmonyStyle(0.3, 0.0, 0.2, 0.1, 0.0, 0.1, 0.2, 0.05, false);
    /** Marches and fanfares: strong triads, the odd dominant 7th, mostly root-position bass. */
    public static final HarmonyStyle HEROIC = new HarmonyStyle(0.2, 0.05, 0.15, 0.15, 0.0, 0.15, 0.3, 0.12, false);
    /**
     * Modal rock and adventure (Mixolydian): a small set of plain triads that keeps returning to I, an occasional
     * I7 or v7 for color, roots in the bass, and no tonal devices that would pull toward the parent major key.
     */
    public static final HarmonyStyle MODAL = new HarmonyStyle(0.3, 0.0, 0.15, 0.0, 0.0, 0.05, 0.0, 0.05, false);
    /** Pastoral, puzzle, ambient: colorful add9s and suspensions, flowing bass. */
    public static final HarmonyStyle LUSH = new HarmonyStyle(0.4, 0.3, 0.25, 0.1, 0.0, 0.2, 0.3, 0.35);
}
