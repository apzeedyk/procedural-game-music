package com.jazzerator.midi;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Renders through FluidSynth, the reference open-source SoundFont engine, which implements the SoundFont format
 * (filters, envelopes, modulators, reverb, chorus) more fully than the JDK synthesizer. FluidSynth runs as a
 * separate program (like ffmpeg): the song is written to a temporary MIDI file and rendered to a WAV file.
 */
public final class FluidSynthRenderer implements AudioRenderer {
    private final String executable;
    private final File soundfont;

    public FluidSynthRenderer(String executable, File soundfont) {
        this.executable = executable;
        this.soundfont = soundfont;
    }

    @Override
    public AudioInputStream render(Sequence sequence, int bpm, double padSeconds) throws IOException {
        File midi = File.createTempFile("jazzerator-", ".mid");
        File wav = File.createTempFile("jazzerator-", ".wav");
        try {
            MidiSystem.write(sequence, 1, midi);
            Process p = new ProcessBuilder(command(executable, soundfont, midi, wav))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IOException("FluidSynth failed with exit code " + exit);
            }
            long frames = Math.round(WavRenderer.FORMAT.getFrameRate() * WavRenderer.seconds(sequence, bpm, padSeconds));
            byte[] pcm = exactLength(readPcm(wav), frames * WavRenderer.FORMAT.getFrameSize());
            return new AudioInputStream(new ByteArrayInputStream(pcm), WavRenderer.FORMAT, frames);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while rendering", e);
        } finally {
            Files.deleteIfExists(midi.toPath());
            Files.deleteIfExists(wav.toPath());
        }
    }

    /**
     * Fast offline render: no audio driver (-n), no shell (-i), quiet (-q), 44.1 kHz 16-bit WAV, matching the rest of
     * the pipeline. Gain 0.9 roughly matches the JDK synthesizer's level with headroom; the auto-mix sets the balance between parts.
     */
    static List<String> command(String executable, File soundfont, File midi, File wav) {
        return List.of(executable, "-n", "-i", "-q",
                "-F", wav.getAbsolutePath(),
                "-T", "wav", "-O", "s16", "-r", "44100", "-g", "0.9",
                soundfont.getAbsolutePath(), midi.getAbsolutePath());
    }

    /** Truncates or zero-pads (silence) to exactly {@code length} bytes. */
    static byte[] exactLength(byte[] pcm, long length) {
        return Arrays.copyOf(pcm, (int) length);
    }

    private static byte[] readPcm(File wav) throws IOException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
            AudioFormat f = in.getFormat();
            boolean matches = f.getSampleRate() == WavRenderer.FORMAT.getSampleRate()
                    && f.getChannels() == 2 && f.getSampleSizeInBits() == 16 && !f.isBigEndian();
            try (AudioInputStream converted = matches ? in : AudioSystem.getAudioInputStream(WavRenderer.FORMAT, in)) {
                return converted.readAllBytes();
            }
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("FluidSynth produced an unreadable WAV file", e);
        }
    }
}
