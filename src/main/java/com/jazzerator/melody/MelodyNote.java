package com.jazzerator.melody;

/** A single lead note. Duration is the full rhythmic value; the arranger decides articulation. */
public record MelodyNote(int pitch, long startTick, long durationTicks, int velocity) {
    public long endTick() {
        return startTick + durationTicks;
    }
}
