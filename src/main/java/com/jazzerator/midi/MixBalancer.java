package com.jazzerator.midi;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioInputStream;

/**
 * Automatic mix: renders each channel on its own, measures its loudness, and sets channel volume (CC7) so every
 * part sits at its target level relative to the lead. Volume is balanced with the fader rather than velocity,
 * because velocity on sampled instruments changes the tone (a soft piano is darker, not just quieter).
 */
public final class MixBalancer {
    private static final int VOLUME = 7;
    /** GM default channel volume; levels are measured here and adjusted from here. */
    private static final int REFERENCE = 100;
    private static final int MIN_VOLUME = 20;
    private static final int MAX_VOLUME = 127;
    private static final double SILENT_DB = -80;

    private MixBalancer() {
    }

    /**
     * @param targets  dB relative to the reference channel, per channel (e.g. comp -7 means 7 dB under the lead)
     * @param reference the channel the others are balanced against (the lead)
     */
    public static void balance(Sequence sequence, int bpm, AudioRenderer renderer, int reference,
                               Map<Integer, Double> targets) throws IOException {
        Map<Integer, Double> levels = new LinkedHashMap<>();
        levels.put(reference, loudness(sequence, bpm, renderer, reference));
        for (int channel : targets.keySet()) {
            levels.put(channel, loudness(sequence, bpm, renderer, channel));
        }
        double referenceDb = levels.get(reference);
        if (referenceDb <= SILENT_DB) {
            return;
        }
        for (Map.Entry<Integer, Double> t : targets.entrySet()) {
            double measured = levels.get(t.getKey());
            if (measured <= SILENT_DB) {
                continue; // the part doesn't play in this song
            }
            double adjustDb = referenceDb + t.getValue() - measured;
            setVolume(sequence, t.getKey(), volumeFor(adjustDb));
        }
    }

    /** CC7 value for a gain change in dB from the reference volume (GM volume curve: 40 * log10(cc / 127)). */
    static int volumeFor(double adjustDb) {
        int cc = (int) Math.round(REFERENCE * Math.pow(10, adjustDb / 40));
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, cc));
    }

    /** RMS loudness in dBFS of one channel rendered alone. */
    static double loudness(Sequence sequence, int bpm, AudioRenderer renderer, int channel) throws IOException {
        try (AudioInputStream in = renderer.render(solo(sequence, channel), bpm, 0)) {
            byte[] bytes = in.readAllBytes();
            double sum = 0;
            int n = 0;
            for (int i = 0; i + 1 < bytes.length; i += 2) {
                double v = (short) ((bytes[i] & 0xff) | (bytes[i + 1] << 8));
                sum += v * v;
                n++;
            }
            double rms = n == 0 ? 0 : Math.sqrt(sum / n);
            return rms <= 0 ? -120 : 20 * Math.log10(rms / 32768.0);
        }
    }

    /** A copy keeping only one channel's notes (plus all setup messages). */
    static Sequence solo(Sequence sequence, int channel) {
        try {
            Sequence out = new Sequence(sequence.getDivisionType(), sequence.getResolution());
            for (Track track : sequence.getTracks()) {
                Track copy = out.createTrack();
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent e = track.get(i);
                    boolean keep = !(e.getMessage() instanceof ShortMessage m)
                            || m.getChannel() == channel
                            || m.getCommand() == ShortMessage.PROGRAM_CHANGE
                            || m.getCommand() == ShortMessage.CONTROL_CHANGE;
                    if (keep) {
                        copy.add(e);
                    }
                }
            }
            return out;
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setVolume(Sequence sequence, int channel, int value) {
        try {
            sequence.getTracks()[0].add(new MidiEvent(
                    new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, VOLUME, value), 0));
        } catch (InvalidMidiDataException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
