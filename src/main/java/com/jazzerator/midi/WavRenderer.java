package com.jazzerator.midi;

import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.SoftSynthesizer;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Renders a Sequence to audio offline (faster than real time) with the JDK's Gervill synthesizer, optionally
 * using a SoundFont. Assumes a single tempo, which is true for every generated song.
 */
public final class WavRenderer {
    public static final AudioFormat FORMAT = new AudioFormat(44_100f, 16, 2, true, false);
    /** Silence after the last event so reverb and release tails ring out. */
    public static final double TAIL_SECONDS = 2.0;

    private WavRenderer() {
    }

    public static void write(Sequence sequence, int bpm, File soundfont, File out) throws IOException {
        try (AudioInputStream audio = render(sequence, bpm, soundfont, 0)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, out);
        }
    }

    /** Length of the rendered audio in seconds, including the tail and any extra padding. */
    public static double seconds(Sequence sequence, int bpm, double padSeconds) {
        return sequence.getTickLength() * microsPerTick(sequence, bpm) / 1e6 + TAIL_SECONDS + padSeconds;
    }

    /**
     * An audio stream of exactly {@link #seconds} length. Closing the stream closes the synthesizer.
     *
     * @param padSeconds extra silence appended after the tail (used for gaps in compilations)
     */
    public static AudioInputStream render(Sequence sequence, int bpm, File soundfont, double padSeconds)
            throws IOException {
        AudioSynthesizer synth = new SoftSynthesizer();
        try {
            AudioInputStream stream = synth.openStream(FORMAT, null);
            if (soundfont != null) {
                Soundbank bank = MidiSystem.getSoundbank(soundfont);
                synth.loadAllInstruments(bank);
            }
            Receiver receiver = synth.getReceiver();
            double usPerTick = microsPerTick(sequence, bpm);
            for (MidiEvent e : sortedShortEvents(sequence)) {
                receiver.send(e.getMessage(), Math.round(e.getTick() * usPerTick));
            }
            long frames = Math.round(FORMAT.getFrameRate() * seconds(sequence, bpm, padSeconds));
            return new AudioInputStream(new FilterInputStream(stream) {
                @Override
                public void close() throws IOException {
                    super.close();
                    synth.close();
                }
            }, FORMAT, frames);
        } catch (MidiUnavailableException | InvalidMidiDataException e) {
            synth.close();
            throw new IOException("Could not render audio: " + e.getMessage(), e);
        }
    }

    private static double microsPerTick(Sequence sequence, int bpm) {
        return 60_000_000.0 / (bpm * (double) sequence.getResolution());
    }

    /**
     * All channel messages from every track in time order. At equal ticks, program changes come first and
     * note-offs precede note-ons so a repeated pitch is not cut off.
     */
    static List<MidiEvent> sortedShortEvents(Sequence sequence) {
        List<MidiEvent> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof ShortMessage) {
                    events.add(track.get(i));
                }
            }
        }
        events.sort(Comparator.comparingLong(MidiEvent::getTick).thenComparingInt(WavRenderer::priority));
        return events;
    }

    private static int priority(MidiEvent e) {
        ShortMessage m = (ShortMessage) e.getMessage();
        return switch (m.getCommand()) {
            case ShortMessage.PROGRAM_CHANGE, ShortMessage.CONTROL_CHANGE -> 0;
            case ShortMessage.NOTE_OFF -> 1;
            case ShortMessage.NOTE_ON -> m.getData2() == 0 ? 1 : 2;
            default -> 1;
        };
    }
}
