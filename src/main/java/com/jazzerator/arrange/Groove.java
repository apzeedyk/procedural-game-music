package com.jazzerator.arrange;

/**
 * The rhythm section's style for a pairing. One choice sets the drums, bass, and comping together, so the
 * parts always agree with each other.
 */
public enum Groove {
    MARCH(Fill.SNARE, true, BassStyle.ROOT_FIFTH, CompStyle.BLOCK_QUARTERS),
    LIGHT(Fill.NONE, false, BassStyle.ROOT_FIFTH, CompStyle.BROKEN),
    ROCK(Fill.TOMS, true, BassStyle.EIGHTHS, CompStyle.PUSH),
    TRIBAL(Fill.TOMS, false, BassStyle.HELD, CompStyle.SUSTAIN),
    DRIVING(Fill.TOMS, true, BassStyle.OCTAVE_EIGHTHS, CompStyle.STABS),
    SWING(Fill.NONE, true, BassStyle.WALKING, CompStyle.CHARLESTON),
    CHIP(Fill.SNARE, true, BassStyle.OCTAVE_EIGHTHS, CompStyle.CHIP_ARPEGGIO),
    LOFI(Fill.NONE, false, BassStyle.SYNCOPATED, CompStyle.STRUM);

    public enum Fill { NONE, SNARE, TOMS }

    public enum BassStyle { HELD, ROOT_FIFTH, EIGHTHS, OCTAVE_EIGHTHS, WALKING, SYNCOPATED }

    public enum CompStyle { SUSTAIN, BLOCK_QUARTERS, PUSH, STABS, CHARLESTON, BROKEN, CHIP_ARPEGGIO, STRUM }

    private final Fill fill;
    private final boolean crash;
    private final BassStyle bass;
    private final CompStyle comp;

    Groove(Fill fill, boolean crash, BassStyle bass, CompStyle comp) {
        this.fill = fill;
        this.crash = crash;
        this.bass = bass;
        this.comp = comp;
    }

    public String displayName() {
        String n = name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public Fill fill() {
        return fill;
    }

    /** Whether section downbeats get a crash cymbal. */
    public boolean crash() {
        return crash;
    }

    public BassStyle bass() {
        return bass;
    }

    public CompStyle comp() {
        return comp;
    }
}
