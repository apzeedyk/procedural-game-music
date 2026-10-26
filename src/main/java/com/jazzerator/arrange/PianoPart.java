package com.jazzerator.arrange;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Note;
import com.jazzerator.theory.Scale;
import com.jazzerator.util.Rng;
import com.jazzerator.util.Weighted;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Track;

/**
 * A piano with a job. Rather than filling the middle register with chords (where it competes with any harmony
 * voice), each section gives the piano one role, the way an arranger would:
 * <ul>
 *   <li>BASS: left-hand octaves locked to the kick drum, walking into each chord change by step;</li>
 *   <li>RHYTHM: compact, percussive chords driving the groove;</li>
 *   <li>LEAD: the right hand doubles the melody an octave up, over light left-hand roots;</li>
 *   <li>CHORDS: sustained harmony, only when no harmony voice already covers the middle.</li>
 * </ul>
 * Home sections lean toward bass or rhythm and contrasting sections toward lead, so the arrangement changes
 * texture between sections instead of only volume.
 */
final class PianoPart {
    enum Role { BASS, RHYTHM, LEAD, CHORDS }

    private static final long PPQ = MidiWriter.PPQ;
    private static final int LH_FLOOR = 36;  // C2: lowest left-hand root
    private static final int LEAD_CEILING = 96;
    private static final int VELOCITY = 60;

    private PianoPart() {
    }

    /**
     * The piano's role for a section label: chosen once per label, so repeats keep their role. It's a composition
     * choice, so it comes from the song's seed, not from the performance (humanization) stream.
     */
    static Role roleFor(Song song, String label) {
        boolean harmonyVoice = song.spec().instruments().hasHarmony();
        boolean contrast = switch (label) {
            case "B", "C", "Chorus", "Bridge" -> true;
            default -> false;
        };
        Weighted<Role> options = contrast
                ? Weighted.of(Role.LEAD, 2.0).and(Role.RHYTHM, 1.0).and(Role.CHORDS, harmonyVoice ? 0.0 : 1.0)
                : Weighted.of(Role.BASS, 2.0).and(Role.RHYTHM, 2.0).and(Role.CHORDS, harmonyVoice ? 0.0 : 1.0);
        return options.pick(new Rng(song.spec().seed()).fork("piano-role", label));
    }

    static void render(Song song, Groove groove, Track track, int channel, Timing timing, Rng rng,
                       List<int[]> voicings) {
        int beatsPerBar = song.spec().beatsPerBar();
        List<ChordSlot> all = new ArrayList<>();
        List<Section> owners = new ArrayList<>();
        for (Section s : song.sections()) {
            for (ChordSlot slot : s.chords()) {
                all.add(slot);
                owners.add(s);
            }
        }
        Scale scale = song.spec().key().scale();
        for (int i = 0; i < all.size(); i++) {
            ChordSlot slot = all.get(i);
            Section section = owners.get(i);
            Chord next = i + 1 < all.size() ? all.get(i + 1).chord() : null;
            int[] voicing = voicings.get(i);
            switch (roleFor(song, section.label())) {
                case BASS -> bass(track, channel, timing, rng, slot, section, next, voicing, groove, beatsPerBar,
                        scale);
                case RHYTHM -> rhythm(track, channel, timing, rng, slot, section, voicing, groove, beatsPerBar);
                case LEAD -> lead(track, channel, timing, rng, slot, song.melody());
                case CHORDS -> chords(track, channel, timing, rng, slot, section, voicing, beatsPerBar);
            }
        }
    }

    /** Left-hand octaves on the kick, a walk into the next chord, and a light right-hand chord on the change. */
    private static void bass(Track track, int channel, Timing timing, Rng rng, ChordSlot slot, Section section,
                             Chord next, int[] voicing, Groove groove, int beatsPerBar, Scale scale) {
        int root = lowRoot(slot.chord());
        List<CompPart.Hit> kick = new ArrayList<>();
        for (DrumPart.Hit h : DrumPart.pattern(groove, beatsPerBar)) {
            if (h.note() == DrumPart.KICK) {
                kick.add(new CompPart.Hit(h.beat(), 1.0));
            }
        }
        boolean walk = next != null && next.root() != slot.chord().root() && slot.durationTicks() >= 2 * PPQ;
        long walkTick = slot.endTick() - PPQ / 2;
        for (long[] span : CompPart.spans(slot, section, kick)) {
            if (walk && span[0] >= walkTick) {
                continue; // the approach note owns the last eighth
            }
            long length = Math.min(span[1], (walk ? walkTick : slot.endTick()) - span[0]);
            int hit = VELOCITY + 2 + rng.fork("piano-bass", span[0]).range(-3, 3);
            timing.note(track, channel, root, hit, span[0], length);
            timing.note(track, channel, root + 12, hit - 6, span[0], length);
        }
        if (walk) {
            // Step into the next root from below or above, by scale step: E-F#-(G).
            int target = Note.atOrAbove(next.root(), root - 6);
            int approach = scale.step(target, target > root ? -1 : 1);
            timing.note(track, channel, approach, VELOCITY - 4, walkTick, PPQ / 2);
            timing.note(track, channel, approach + 12, VELOCITY - 10, walkTick, PPQ / 2);
        }
        // Right hand: just the top of the chord, once, so the harmony is there without filling the middle.
        int hit = VELOCITY - 8 + rng.fork("piano-rh", slot.startTick()).range(-2, 2);
        for (int k = Math.max(0, voicing.length - 2); k < voicing.length; k++) {
            timing.note(track, channel, voicing[k], hit, slot.startTick(), Math.min(PPQ, slot.durationTicks()));
        }
    }

    /** Compact percussive chords on the groove's pulse: 8ths for rock and driving grooves, quarters otherwise. */
    private static void rhythm(Track track, int channel, Timing timing, Rng rng, ChordSlot slot, Section section,
                               int[] voicing, Groove groove, int beatsPerBar) {
        double step = switch (groove) {
            case ROCK, DRIVING, CHIP -> 0.5;
            default -> 1.0;
        };
        List<CompPart.Hit> pulse = new ArrayList<>();
        for (double b = 0; b < beatsPerBar; b += step) {
            pulse.add(new CompPart.Hit(b, step * 0.6));
        }
        int[] compact = java.util.Arrays.copyOfRange(voicing, Math.max(0, voicing.length - 3), voicing.length);
        for (long[] span : CompPart.spans(slot, section, pulse)) {
            boolean onBeat = Math.floorMod(span[0] - section.startTick(), PPQ) == 0;
            int hit = VELOCITY + (onBeat ? 4 : -6) + rng.fork("piano-rhythm", span[0]).range(-2, 2);
            for (int pitch : compact) {
                timing.note(track, channel, pitch, hit, span[0], span[1]);
            }
        }
    }

    /** Right hand doubles the melody an octave up; left hand holds the root under each chord. */
    private static void lead(Track track, int channel, Timing timing, Rng rng, ChordSlot slot,
                             List<MelodyNote> melody) {
        for (MelodyNote n : melody) {
            if (n.startTick() < slot.startTick() || n.startTick() >= slot.endTick() || n.durationTicks() <= PPQ / 8) {
                continue;
            }
            int pitch = n.pitch() + 12;
            if (pitch > LEAD_CEILING) {
                pitch = n.pitch();
            }
            long length = Math.min(Math.round(n.durationTicks() * 0.9), slot.endTick() - n.startTick());
            timing.note(track, channel, pitch, Math.max(30, n.velocity() - 22), n.startTick(), length);
        }
        int root = lowRoot(slot.chord());
        timing.note(track, channel, root, VELOCITY - 6 + rng.fork("piano-lh", slot.startTick()).range(-2, 2),
                slot.startTick(), slot.durationTicks());
        timing.note(track, channel, root + 12, VELOCITY - 12, slot.startTick(), slot.durationTicks());
    }

    /** Sustained chords on the first beat of each bar the chord covers (only when nothing else holds the middle). */
    private static void chords(Track track, int channel, Timing timing, Rng rng, ChordSlot slot, Section section,
                               int[] voicing, int beatsPerBar) {
        for (long[] span : CompPart.spans(slot, section, List.of(new CompPart.Hit(0, beatsPerBar)))) {
            int hit = VELOCITY - 4 + rng.fork("piano-chords", span[0]).range(-2, 2);
            for (int pitch : voicing) {
                timing.note(track, channel, pitch, hit, span[0], span[1]);
            }
        }
    }

    /** The chord root in the left hand's register (C2-B2), an octave around the bass instrument. */
    private static int lowRoot(Chord chord) {
        return Note.atOrAbove(chord.root(), LH_FLOOR);
    }
}
