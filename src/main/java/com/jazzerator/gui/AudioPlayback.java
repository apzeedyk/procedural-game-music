package com.jazzerator.gui;

import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/** Plays one rendered song at a time and reports its position, so the piano roll can follow along. */
final class AudioPlayback {
    private Clip clip;

    synchronized void play(AudioInputStream audio) throws IOException, LineUnavailableException {
        stop();
        clip = AudioSystem.getClip();
        clip.open(audio);
        clip.start();
    }

    synchronized void stop() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    synchronized boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    /** Seconds since the start of the song, or -1 when nothing is loaded. */
    synchronized double positionSeconds() {
        return clip == null ? -1 : clip.getMicrosecondPosition() / 1e6;
    }
}
