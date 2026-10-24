package com.jazzerator.form;

import com.jazzerator.theory.Chord;

/** A chord sounding over an absolute tick span. */
public record ChordSlot(Chord chord, long startTick, long durationTicks) {
    public long endTick() {
        return startTick + durationTicks;
    }
}
