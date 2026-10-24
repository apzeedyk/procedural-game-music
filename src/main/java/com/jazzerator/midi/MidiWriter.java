package com.jazzerator.midi;

import java.io.File;
import java.io.IOException;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/** Thin helpers for building and saving a MIDI Sequence. */
public final class MidiWriter {
    public static final int PPQ = 480;

    private MidiWriter() {
    }

    public static Sequence newSequence() {
        try {
            return new Sequence(Sequence.PPQ, PPQ);
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setTempo(Track track, int bpm) {
        int mpq = 60_000_000 / bpm;
        byte[] data = {(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq};
        try {
            track.add(new MidiEvent(new MetaMessage(0x51, data, 3), 0));
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setTimeSignature(Track track, int numerator, int denominator) {
        int denomPow = Integer.numberOfTrailingZeros(denominator);
        byte[] data = {(byte) numerator, (byte) denomPow, 24, 8};
        try {
            track.add(new MidiEvent(new MetaMessage(0x58, data, 4), 0));
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void programChange(Track track, int channel, int program) {
        track.add(event(ShortMessage.PROGRAM_CHANGE, channel, program, 0, 0));
    }

    public static void note(Track track, int channel, int pitch, int velocity, long startTick, long durationTicks) {
        track.add(event(ShortMessage.NOTE_ON, channel, pitch, velocity, startTick));
        track.add(event(ShortMessage.NOTE_OFF, channel, pitch, 0, startTick + durationTicks));
    }

    public static void save(Sequence sequence, File file) throws IOException {
        MidiSystem.write(sequence, 1, file);
    }

    private static MidiEvent event(int command, int channel, int data1, int data2, long tick) {
        try {
            return new MidiEvent(new ShortMessage(command, channel, data1, data2), tick);
        } catch (InvalidMidiDataException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
