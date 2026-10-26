package com.jazzerator.arrange;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Note;
import com.jazzerator.util.Rng;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Track;

/** Bass line patterns per groove, built from each chord's bass note, fifth, and octave. */
final class BassPart {
    private static final long PPQ = MidiWriter.PPQ;
    static final int FLOOR = 36; // C2
    static final int MIN = 28;   // E1
    static final int MAX = 55;   // G3
    private static final int VELOCITY = 88;

    enum Tone { BASS, FIFTH, OCTAVE }

    record Hit(double beat, double length, Tone tone) {
    }

    private BassPart() {
    }

    static void render(Song song, Groove groove, Track track, int channel, Timing timing, Rng rng) {
        int beatsPerBar = song.spec().beatsPerBar();
        List<ChordSlot> all = new ArrayList<>();
        List<Section> owners = new ArrayList<>();
        for (Section s : song.sections()) {
            for (ChordSlot slot : s.chords()) {
                all.add(slot);
                owners.add(s);
            }
        }
        for (int i = 0; i < all.size(); i++) {
            ChordSlot slot = all.get(i);
            Section section = owners.get(i);
            Chord chord = slot.chord();
            boolean last = i == all.size() - 1;
            if (last) { // final chord: one long root
                timing.note(track, channel, bassPitch(chord), VELOCITY + 8, slot.startTick(), slot.durationTicks());
                continue;
            }
            if (groove.bass() == Groove.BassStyle.WALKING) {
                walk(track, channel, timing, slot, all.get(i + 1).chord(), rng);
                continue;
            }
            List<Hit> pattern = hits(groove.bass(), beatsPerBar);
            List<CompPart.Hit> asCompHits = pattern.stream().map(h -> new CompPart.Hit(h.beat(), h.length())).toList();
            List<long[]> spans = CompPart.spans(slot, section, asCompHits);
            for (long[] span : spans) {
                Tone tone = toneAt(pattern, span[0] - section.startTick(), section.ticksPerBar());
                int pitch = switch (tone) {
                    case BASS -> bassPitch(chord);
                    case FIFTH -> rootPitch(chord) + 7;
                    case OCTAVE -> rootPitch(chord) + 12;
                };
                boolean downbeat = Math.floorMod(span[0] - section.startTick(), section.ticksPerBar()) == 0;
                timing.note(track, channel, fit(pitch), VELOCITY + (downbeat ? 8 : 0) + rng.fork("bass", span[0]).range(-3, 3), span[0],
                        Math.max(PPQ / 8, span[1] - PPQ / 16));
            }
        }
    }

    static List<Hit> hits(Groove.BassStyle style, int beatsPerBar) {
        List<Hit> hits = new ArrayList<>();
        boolean waltz = beatsPerBar == 3;
        switch (style) {
            case HELD -> hits.add(new Hit(0, beatsPerBar, Tone.BASS));
            case ROOT_FIFTH -> {
                hits.add(new Hit(0, waltz ? 1 : 2, Tone.BASS));
                hits.add(new Hit(waltz ? 1 : 2, waltz ? 2 : 2, Tone.FIFTH));
            }
            case EIGHTHS -> {
                for (double b = 0; b < beatsPerBar; b += 0.5) {
                    hits.add(new Hit(b, 0.5, Tone.BASS));
                }
            }
            case OCTAVE_EIGHTHS -> {
                for (double b = 0; b < beatsPerBar; b += 0.5) {
                    hits.add(new Hit(b, 0.5, b % 1 == 0 ? Tone.BASS : Tone.OCTAVE));
                }
            }
            case SYNCOPATED -> {
                hits.add(new Hit(0, 1.5, Tone.BASS));
                if (waltz) {
                    hits.add(new Hit(2, 1, Tone.FIFTH));
                } else {
                    hits.add(new Hit(2.5, 0.5, Tone.BASS));
                    hits.add(new Hit(3, 1, Tone.FIFTH));
                }
            }
            default -> hits.add(new Hit(0, beatsPerBar, Tone.BASS));
        }
        return hits;
    }

    private static Tone toneAt(List<Hit> pattern, long tickInSection, long ticksPerBar) {
        long inBar = Math.floorMod(tickInSection, ticksPerBar);
        for (Hit h : pattern) {
            if (Math.round(h.beat() * PPQ) == inBar) {
                return h.tone();
            }
        }
        return Tone.BASS; // the forced attack at a mid-pattern chord change
    }

    /**
     * Walking bass in quarter notes: chord bass note first, then chord tones climbing, and the final beat a
     * half-step approach into the next chord's bass note.
     */
    private static void walk(Track track, int channel, Timing timing, ChordSlot slot, Chord next, Rng rng) {
        int beats = (int) (slot.durationTicks() / PPQ);
        int start = bassPitch(slot.chord());
        int target = bassPitch(next);
        List<Integer> chordTones = slot.chord().pitchClasses();
        int pitch = start;
        for (int b = 0; b < beats; b++) {
            if (b == 0) {
                pitch = start;
            } else if (b == beats - 1) {
                pitch = target > pitch ? target - 1 : target + 1; // chromatic approach
            } else {
                int pc = chordTones.get(b % chordTones.size());
                pitch = Note.atOrAbove(pc, pitch + 1);
                if (pitch > MAX) {
                    pitch -= 12;
                }
            }
            timing.note(track, channel, fit(pitch), VELOCITY + (b == 0 ? 6 : 0) + rng.fork("walk", slot.startTick() + b * PPQ).range(-3, 3),
                    slot.startTick() + b * PPQ, PPQ - PPQ / 10);
        }
        long remainder = slot.durationTicks() - beats * PPQ; // odd-length slots
        if (remainder > 0) {
            timing.note(track, channel, fit(target > pitch ? target - 1 : target + 1), VELOCITY,
                    slot.startTick() + beats * PPQ, remainder);
        }
    }

    private static int bassPitch(Chord chord) {
        return Note.atOrAbove(chord.bassPc(), FLOOR);
    }

    private static int rootPitch(Chord chord) {
        return Note.atOrAbove(chord.root(), FLOOR);
    }

    private static int fit(int pitch) {
        while (pitch > MAX) {
            pitch -= 12;
        }
        while (pitch < MIN) {
            pitch += 12;
        }
        return pitch;
    }
}
