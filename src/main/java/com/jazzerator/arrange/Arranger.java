package com.jazzerator.arrange;

import com.jazzerator.form.Song;
import com.jazzerator.melody.Harmonizer;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Instruments;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.util.Rng;
import com.jazzerator.midi.AudioRenderer;
import com.jazzerator.midi.ChipRenderer;
import com.jazzerator.midi.MixBalancer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

/** Renders a Song to MIDI. Channels: 0 lead, 1 comp, 2 bass, 3 lead harmony, 9 drums. */
public final class Arranger {
    public static final int LEAD = 0;
    public static final int COMP = 1;
    public static final int BASS = 2;
    public static final int HARMONY = 3;
    public static final int DRUMS = DrumPart.CHANNEL;

    private static final double LEGATO = 0.92;
    /** Louder brass samples are brighter with a harder attack, which is what makes brass punch. */
    private static final int BRASS_PUNCH = 14;
    private static final int REVERB = 91; // GM effect send controllers
    private static final int CHORUS = 93;
    private static final long PERFORMANCE_SALT = 0x5EED_CAFEL;

    private Arranger() {
    }

    /**
     * Mix levels relative to the lead, in dB: accompaniment sits well under the tune, harmony just under it,
     * and the rhythm section supports without competing.
     */
    public static Map<Integer, Double> mixTargets() {
        Map<Integer, Double> targets = new LinkedHashMap<>();
        targets.put(HARMONY, -5.0);
        targets.put(COMP, -7.0);
        targets.put(BASS, -3.0);
        targets.put(DRUMS, -3.0);
        return targets;
    }

    /**
     * Mix levels for a given sound source. Chip music balances its few voices nearly evenly, the way NES channels
     * share the spotlight: a thin pulse wave 7 dB under a square lead sits in the same range and disappears.
     */
    public static Map<Integer, Double> mixTargets(AudioRenderer renderer) {
        if (!(renderer instanceof ChipRenderer)) {
            return mixTargets();
        }
        Map<Integer, Double> targets = new LinkedHashMap<>();
        targets.put(HARMONY, -3.0);
        targets.put(COMP, -3.0);
        targets.put(BASS, -1.0);
        targets.put(DRUMS, -2.0);
        return targets;
    }

    /** Renders and auto-balances the mix for the synthesizer and SoundFont it will be heard through. */
    public static Sequence renderMixed(Song song, AudioRenderer renderer) throws IOException {
        Sequence sequence = render(song);
        MixBalancer.balance(sequence, song.spec().tempo(), renderer, LEAD, mixTargets(renderer));
        return sequence;
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
        // Room ambience: wetter lead and chords, a drier bass so the low end stays tight.
        effects(lead, LEAD, leadReverb(spec.instruments().lead()), leadChorus(spec.instruments().lead()));
        effects(comp, COMP, 56, 24);
        effects(bass, BASS, 18, 0);
        effects(drums, DRUMS, 40, 0);

        int leadBoost = Instruments.isBrass(spec.instruments().lead()) ? BRASS_PUNCH : 0;
        for (MelodyNote n : song.melody()) {
            long sounding = Math.max(MidiWriter.PPQ / 8, Math.round(n.durationTicks() * LEGATO));
            timing.note(lead, LEAD, n.pitch(), n.velocity() + leadBoost, n.startTick(), sounding);
        }
        List<MelodyNote> upperVoices = new ArrayList<>(song.melody());
        if (spec.instruments().hasHarmony()) {
            Track harmony = sequence.createTrack();
            MidiWriter.programChange(harmony, HARMONY, spec.instruments().harmony());
            effects(harmony, HARMONY, leadReverb(spec.instruments().harmony()),
                    leadChorus(spec.instruments().harmony()));
            List<MelodyNote> harmonyLine = Harmonizer.thirdsBelow(song.melody(), song.sections(), spec.key().scale(),
                    spec.beatsPerBar());
            upperVoices.addAll(harmonyLine);
            for (MelodyNote n : harmonyLine) {
                long sounding = Math.max(MidiWriter.PPQ / 8, Math.round(n.durationTicks() * LEGATO));
                // No punch boost: the harmony must stay under the lead, or it takes over on held notes.
                timing.note(harmony, HARMONY, n.pitch(), n.velocity(), n.startTick(), sounding);
            }
        }
        CompPart.render(song, groove, comp, COMP, timing, rng, upperVoices);
        BassPart.render(song, groove, bass, BASS, timing, rng);
        DrumPart.render(song, groove, drums, timing, rng);
        return sequence;
    }

    /** Brass samples carry their own room sound and blur under heavy reverb, so they stay much drier. */
    private static int leadReverb(int program) {
        return Instruments.isBrass(program) ? 24 : 40;
    }

    private static int leadChorus(int program) {
        return Instruments.isBrass(program) ? 0 : 8;
    }

    private static void effects(Track track, int channel, int reverb, int chorus) {
        MidiWriter.controlChange(track, channel, REVERB, reverb);
        MidiWriter.controlChange(track, channel, CHORUS, chorus);
    }
}
