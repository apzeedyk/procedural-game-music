package com.jazzerator.midi;

import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** Plays rendered audio through the default output device and blocks until it finishes. */
public final class AudioPlayer {
    private AudioPlayer() {
    }

    public static void play(AudioInputStream audio) throws IOException, LineUnavailableException {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(audio.getFormat())) {
            line.open(audio.getFormat());
            line.start();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = audio.read(buffer)) > 0) {
                line.write(buffer, 0, n);
            }
            line.drain();
        }
    }
}
