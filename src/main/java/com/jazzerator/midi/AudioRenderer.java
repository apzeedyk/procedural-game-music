package com.jazzerator.midi;

import java.io.IOException;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioInputStream;

/** Turns a single-tempo Sequence into audio. Implementations: SoundFont (sampled) and chip (synthesized waves). */
public interface AudioRenderer {
    /** Audio of exactly {@link WavRenderer#seconds} length; the caller closes the stream. */
    AudioInputStream render(Sequence sequence, int bpm, double padSeconds) throws IOException;

    /** Sampled instruments through the JDK synthesizer; a null SoundFont means the JDK's built-in sounds. */
    static AudioRenderer soundFont(java.io.File soundfont) {
        return (sequence, bpm, pad) -> WavRenderer.render(sequence, bpm, soundfont, pad);
    }
}
