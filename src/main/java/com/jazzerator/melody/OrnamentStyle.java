package com.jazzerator.melody;

/**
 * Per-pairing probabilities for each melodic ornament, rolled per eligible note. {@code chromatic} is the chance a
 * passing tone between notes a whole step apart is the chromatic note between them.
 */
public record OrnamentStyle(
        double run,
        double trill,
        double passing,
        double neighbor,
        double grace,
        double split,
        double merge,
        double staccato,
        double chromatic) {

    public static final OrnamentStyle NONE = new OrnamentStyle(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final OrnamentStyle DEFAULT = new OrnamentStyle(0.08, 0.05, 0.2, 0.1, 0.08, 0.05, 0.05, 0.05, 0);
    /** Boss and racing: fast scale runs, repeated-note drive, punchy staccato. */
    public static final OrnamentStyle SHRED = new OrnamentStyle(0.3, 0.1, 0.15, 0.1, 0.05, 0.15, 0.02, 0.1, 0.2);
    /** Jazz and lo-fi: grace notes, chromatic passing tones, laid-back long notes. */
    public static final OrnamentStyle JAZZY = new OrnamentStyle(0.1, 0.02, 0.3, 0.15, 0.2, 0.02, 0.08, 0.1, 0.6);
    /** Chiptune: arpeggio-like runs, fast trills, repeated notes. */
    public static final OrnamentStyle CHIP = new OrnamentStyle(0.2, 0.15, 0.1, 0.1, 0.1, 0.2, 0.02, 0.15, 0);
    /** Pastoral and folk: trills, grace notes, gentle neighbor tones. */
    public static final OrnamentStyle PASTORAL = new OrnamentStyle(0.1, 0.15, 0.2, 0.15, 0.15, 0.02, 0.08, 0.03, 0);
    /** Dungeon and mystery: few ornaments, longer held notes, space. */
    public static final OrnamentStyle SPARSE = new OrnamentStyle(0.03, 0.03, 0.1, 0.05, 0.1, 0, 0.2, 0.1, 0.1);
}
