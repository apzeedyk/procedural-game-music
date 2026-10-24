package com.jazzerator.harmony;

/** Per-pairing probabilities for each harmonic decoration (0 = never, 1 = wherever it applies). */
public record HarmonyStyle(
        double seventh,
        double ninth,
        double suspension,
        double secondaryDominant,
        double tritoneSub,
        double borrowed,
        double deceptive,
        double inversion) {

    public static final HarmonyStyle NONE = new HarmonyStyle(0, 0, 0, 0, 0, 0, 0, 0);
    public static final HarmonyStyle DEFAULT = new HarmonyStyle(0.25, 0.1, 0.15, 0.15, 0.0, 0.15, 0.3, 0.3);
    /** Jazz and lo-fi: rich extensions, ii-V motion, tritone substitutions. */
    public static final HarmonyStyle JAZZY = new HarmonyStyle(0.9, 0.4, 0.1, 0.4, 0.5, 0.2, 0.4, 0.2);
    /** Battle, racing, chiptune: mostly plain triads and punchy sus chords. */
    public static final HarmonyStyle POWER = new HarmonyStyle(0.05, 0.0, 0.2, 0.1, 0.0, 0.1, 0.2, 0.15);
    /** Pastoral, puzzle, ambient: colorful add9s and suspensions, flowing bass. */
    public static final HarmonyStyle LUSH = new HarmonyStyle(0.4, 0.3, 0.25, 0.1, 0.0, 0.2, 0.3, 0.35);
}
