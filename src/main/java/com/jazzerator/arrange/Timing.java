package com.jazzerator.arrange;

import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Feel;
import javax.sound.midi.Track;

/**
 * Performance layer shared by every part: maps straight ticks to swung ticks and scales velocity by section
 * dynamics. Parts write notes in straight time and let this class make them groove.
 */
final class Timing {
    private static final long PPQ = MidiWriter.PPQ;

    private final Song song;
    private final long swingSplit; // where the off-beat 8th lands within a beat

    Timing(Song song) {
        this.song = song;
        double ratio = song.spec().feel() == Feel.SWING ? 0.64 : song.spec().feel() == Feel.SHUFFLE ? 0.67 : 0.5;
        this.swingSplit = Math.round(PPQ * ratio);
    }

    /** Piecewise-linear swing: 0 -> 0, half beat -> swing point, full beat -> full beat. Identity when straight. */
    long map(long tick) {
        long beat = Math.floorDiv(tick, PPQ);
        long frac = tick - beat * PPQ;
        long half = PPQ / 2;
        long mapped = frac <= half
                ? frac * swingSplit / half
                : swingSplit + (frac - half) * (PPQ - swingSplit) / (PPQ - half);
        return beat * PPQ + mapped;
    }

    /**
     * Section dynamics: a slightly quieter intro, a gentle lift for contrasting sections, and an outro that fades.
     * Kept subtle on purpose: sampled instruments (piano especially) switch to brighter velocity layers, so a large
     * jump makes the accompaniment lurch forward instead of just getting a little louder.
     */
    double dynamics(long tick) {
        for (Section s : song.sections()) {
            if (tick >= s.startTick() && tick < s.endTick()) {
                return switch (s.label()) {
                    case "Intro" -> 0.88;
                    case "B", "Chorus", "Bridge" -> 1.0;
                    case "C" -> 0.97;
                    case "Outro" -> 0.95 - 0.25 * (tick - s.startTick()) / (double) (s.endTick() - s.startTick());
                    default -> 0.95;
                };
            }
        }
        return 0.9;
    }

    /** Writes a note given in straight time, applying swing and dynamics. */
    void note(Track track, int channel, int pitch, int velocity, long start, long duration) {
        long s = map(start);
        long e = Math.max(s + 1, map(start + duration));
        int v = (int) Math.round(velocity * dynamics(start));
        MidiWriter.note(track, channel, pitch, Math.max(1, Math.min(127, v)), s, e - s);
    }
}
