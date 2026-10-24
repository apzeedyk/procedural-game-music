package com.jazzerator.midi;

import java.io.File;
import java.io.IOException;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

/** Plays a Sequence through the Java synthesizer (optionally with a SoundFont) and blocks until it finishes. */
public final class MidiPlayer {
    private MidiPlayer() {
    }

    public static void play(Sequence sequence, File soundfont)
            throws MidiUnavailableException, InvalidMidiDataException, InterruptedException, IOException {
        try (Synthesizer synth = MidiSystem.getSynthesizer();
             Sequencer sequencer = MidiSystem.getSequencer(false)) {
            synth.open();
            if (soundfont != null) {
                synth.loadAllInstruments(MidiSystem.getSoundbank(soundfont));
            }
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synth.getReceiver());
            sequencer.setSequence(sequence);
            sequencer.start();
            while (sequencer.isRunning()) {
                Thread.sleep(100);
            }
            Thread.sleep(1000); // let release tails ring out
        }
    }
}
