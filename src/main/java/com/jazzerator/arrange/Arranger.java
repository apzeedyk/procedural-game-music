package com.jazzerator.arrange;

import com.jazzerator.form.Song;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.util.Rng;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

/** Renders a Song to MIDI. Channels: 0 lead, 1 comp, 2 bass, 9 drums. */
public final class Arranger {
    public static final int LEAD = 0;
    public static final int COMP = 1;
    public static final int BASS = 2;
    public static final int DRUMS = DrumPart.CHANNEL;

    private static final double LEGATO = 0.92;
    private static final long PERFORMANCE_SALT = 0x5EED_CAFEL;

    private Arranger() {
    }

    public static Sequence render(Song song) {
        SongSpec spec = song.spec();
        Groove groove = spec.pairing().groove();
        Timing timing = new Timing(song);
        // Separate stream so humanization never changes the composition for a given seed.
        Rng rng = new Rng(spec.seed() ^ PERFORMANCE_SALT);

        Sequence sequence = MidiWriter.newSequence();
        Track conductor = sequence.createTrack();
        MidiWriter.setTempo(conductor, spec.tempo());
        MidiWriter.setTimeSignature(conductor, spec.beatsPerBar(), 4);

        Track lead = sequence.createTrack();
        Track comp = sequence.createTrack();
        Track bass = sequence.createTrack();
        Track drums = sequence.createTrack();
        MidiWriter.programChange(lead, LEAD, spec.instruments().lead());
        MidiWriter.programChange(comp, COMP, spec.instruments().comp());
        MidiWriter.programChange(bass, BASS, spec.instruments().bass());

        for (MelodyNote n : song.melody()) {
            long sounding = Math.max(MidiWriter.PPQ / 8, Math.round(n.durationTicks() * LEGATO));
            timing.note(lead, LEAD, n.pitch(), n.velocity(), n.startTick(), sounding);
        }
        CompPart.render(song, groove, comp, COMP, timing, rng);
        BassPart.render(song, groove, bass, BASS, timing, rng);
        DrumPart.render(song, groove, drums, timing, rng);
        return sequence;
    }
}
