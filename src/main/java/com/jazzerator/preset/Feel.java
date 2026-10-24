package com.jazzerator.preset;

/** Rhythmic feel: drives subdivision, swing offset, and (later) drum/bass patterns. */
public enum Feel {
    STRAIGHT("Straight 8ths"),
    SWING("Swing"),
    SHUFFLE("Shuffle"),
    DRIVING("Driving 16ths");

    private final String displayName;

    Feel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
