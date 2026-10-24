package com.jazzerator.form;

import java.util.List;

/** One section of the form, e.g. the second "A". Ticks are absolute from the start of the song. */
public record Section(String label, int occurrence, long startTick, int bars, long ticksPerBar, List<ChordSlot> chords) {
    public long endTick() {
        return startTick + bars * ticksPerBar;
    }
}
