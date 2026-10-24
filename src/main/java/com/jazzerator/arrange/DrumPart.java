package com.jazzerator.arrange;

import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Track;

/** General MIDI drum kit (channel 10) patterns per groove, with section fills, crashes, and an ending hit. */
final class DrumPart {
    static final int CHANNEL = 9;

    // GM percussion keys
    static final int KICK = 36;
    static final int SNARE = 38;
    static final int CLOSED_HAT = 42;
    static final int PEDAL_HAT = 44;
    static final int LOW_TOM = 45;
    static final int MID_TOM = 47;
    static final int HIGH_MID_TOM = 48;
    static final int HIGH_TOM = 50;
    static final int CRASH = 49;
    static final int RIDE = 51;
    static final int TAMBOURINE = 54;
    static final int SHAKER = 70; // maracas

    private static final long PPQ = MidiWriter.PPQ;
    private static final long HIT = PPQ / 4;

    record Hit(int note, double beat, int velocity) {
    }

    private DrumPart() {
    }

    static void render(Song song, Groove groove, Track track, Timing timing, Rng rng) {
        int beatsPerBar = song.spec().beatsPerBar();
        List<Hit> pattern = pattern(groove, beatsPerBar);
        List<Section> sections = song.sections();
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            boolean lastSection = i == sections.size() - 1;
            for (int bar = 0; bar < s.bars(); bar++) {
                long barStart = s.startTick() + bar * s.ticksPerBar();
                if (lastSection && bar == s.bars() - 1) {
                    hit(track, timing, CRASH, 110, barStart, rng); // ending: one big hit and let it ring
                    hit(track, timing, KICK, 110, barStart, rng);
                    continue;
                }
                if (bar == 0 && i > 0 && groove.crash()) {
                    hit(track, timing, CRASH, 100, barStart, rng);
                }
                boolean sectionEnd = bar == s.bars() - 1;
                boolean midFill = bar == s.bars() / 2 - 1 && s.bars() >= 8 && rng.chance(0.35);
                double fillBeats = groove.fill() == Groove.Fill.NONE ? 0
                        : sectionEnd ? (groove == Groove.DRIVING || groove == Groove.ROCK ? 2 : 1)
                        : midFill ? 1 : 0;
                double fillStart = beatsPerBar - fillBeats;
                for (Hit h : pattern) {
                    if (h.beat() < fillStart) {
                        hit(track, timing, h.note(), h.velocity(), barStart + Math.round(h.beat() * PPQ), rng);
                    }
                }
                if (fillBeats > 0) {
                    fill(track, timing, groove.fill(), barStart + Math.round(fillStart * PPQ), fillBeats, rng);
                }
            }
        }
    }

    /** 16th-note fill: descending toms, or a crescendo snare roll. */
    private static void fill(Track track, Timing timing, Groove.Fill fill, long start, double beats, Rng rng) {
        int[] toms = {HIGH_TOM, HIGH_MID_TOM, MID_TOM, LOW_TOM};
        int steps = (int) Math.round(beats * 4);
        for (int k = 0; k < steps; k++) {
            int note = fill == Groove.Fill.TOMS ? toms[k * toms.length / steps] : SNARE;
            int velocity = 70 + 40 * k / Math.max(1, steps - 1);
            hit(track, timing, note, velocity, start + k * HIT, rng);
        }
        hit(track, timing, KICK, 90, start, rng);
    }

    private static void hit(Track track, Timing timing, int note, int velocity, long tick, Rng rng) {
        timing.note(track, CHANNEL, note, velocity + rng.range(-6, 6), tick, HIT);
    }

    static List<Hit> pattern(Groove groove, int beatsPerBar) {
        List<Hit> p = new ArrayList<>();
        boolean waltz = beatsPerBar == 3;
        switch (groove) {
            case MARCH -> {
                p.add(new Hit(KICK, 0, 95));
                if (!waltz) {
                    p.add(new Hit(KICK, 2, 85));
                }
                for (double b = 0.5; b < beatsPerBar; b += 0.5) {
                    boolean backbeat = b == Math.floor(b);
                    p.add(new Hit(SNARE, b, backbeat ? 95 : 50));
                }
                p.add(new Hit(SNARE, beatsPerBar - 0.25, 60)); // pickup into the next bar
            }
            case LIGHT -> {
                p.add(new Hit(KICK, 0, 70));
                if (!waltz) {
                    p.add(new Hit(KICK, 2, 60));
                }
                eighths(p, SHAKER, beatsPerBar, 50, 38);
                for (int b = 1; b < beatsPerBar; b += waltz ? 1 : 2) {
                    p.add(new Hit(TAMBOURINE, b, 55));
                }
            }
            case ROCK -> {
                p.add(new Hit(KICK, 0, 100));
                if (waltz) {
                    p.add(new Hit(SNARE, 2, 95));
                } else {
                    p.add(new Hit(KICK, 2, 90));
                    p.add(new Hit(KICK, 2.5, 75));
                    p.add(new Hit(SNARE, 1, 100));
                    p.add(new Hit(SNARE, 3, 100));
                }
                eighths(p, CLOSED_HAT, beatsPerBar, 75, 55);
            }
            case TRIBAL -> {
                p.add(new Hit(KICK, 0, 80));
                p.add(new Hit(LOW_TOM, 0, 90));
                p.add(new Hit(LOW_TOM, 1.5, 70));
                p.add(new Hit(LOW_TOM, 2, 85));
                if (!waltz) {
                    p.add(new Hit(MID_TOM, 3, 75));
                    p.add(new Hit(MID_TOM, 3.5, 60));
                }
            }
            case DRIVING -> {
                for (double b : waltz ? new double[]{0, 0.5, 1.5} : new double[]{0, 0.5, 1.75, 2, 2.5}) {
                    p.add(new Hit(KICK, b, 105));
                }
                for (int b = 1; b < beatsPerBar; b += 2) {
                    p.add(new Hit(SNARE, b, 110));
                }
                if (waltz) {
                    p.add(new Hit(SNARE, 2, 105));
                }
                for (double b = 0; b < beatsPerBar; b += 0.25) {
                    p.add(new Hit(CLOSED_HAT, b, b == Math.floor(b) ? 80 : b % 0.5 == 0 ? 65 : 45));
                }
            }
            case SWING -> {
                p.add(new Hit(KICK, 0, 40)); // feathered
                for (int b = 0; b < beatsPerBar; b++) {
                    p.add(new Hit(RIDE, b, b % 2 == 1 ? 75 : 65));
                    if (b % 2 == 1 || waltz && b > 0) {
                        p.add(new Hit(PEDAL_HAT, b, 60));
                        p.add(new Hit(RIDE, b + 0.5, 50)); // the "ding-da" skip, swung by Timing
                    }
                }
            }
            case CHIP -> {
                p.add(new Hit(KICK, 0, 100));
                if (!waltz) {
                    p.add(new Hit(KICK, 2, 90));
                }
                for (int b = 1; b < beatsPerBar; b += waltz ? 1 : 2) {
                    p.add(new Hit(SNARE, b, 95));
                }
                eighths(p, CLOSED_HAT, beatsPerBar, 60, 45);
            }
            case LOFI -> {
                p.add(new Hit(KICK, 0, 85));
                p.add(new Hit(KICK, waltz ? 1.5 : 2.5, 70));
                if (!waltz) {
                    p.add(new Hit(KICK, 1.75, 55));
                }
                for (int b = 1; b < beatsPerBar; b += waltz ? 1 : 2) {
                    p.add(new Hit(SNARE, b, 75));
                }
                eighths(p, CLOSED_HAT, beatsPerBar, 45, 32);
            }
        }
        return p;
    }

    private static void eighths(List<Hit> p, int note, int beatsPerBar, int onBeat, int offBeat) {
        for (double b = 0; b < beatsPerBar; b += 0.5) {
            p.add(new Hit(note, b, b == Math.floor(b) ? onBeat : offBeat));
        }
    }
}
