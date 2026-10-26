package com.jazzerator.midi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioInputStream;

/**
 * Final mastering step: brings every song to the same loudness, so songs in a compilation (and renders from
 * different synthesizers or SoundFonts) play at a consistent level. Gain is limited so peaks never exceed the
 * ceiling. Wraps the renderer used for final output only: the auto-mix measures relative levels before this.
 */
public final class NormalizingRenderer implements AudioRenderer {
    /** RMS loudness target. Streaming platforms normalize to about -14 LUFS; -16 dBFS RMS lands near that. */
    public static final double TARGET_DB = -16.0;
    /** Peak ceiling, just under full scale. */
    public static final double CEILING_DB = -0.5;

    private final AudioRenderer inner;

    public NormalizingRenderer(AudioRenderer inner) {
        this.inner = inner;
    }

    @Override
    public AudioInputStream render(Sequence sequence, int bpm, double padSeconds) throws IOException {
        byte[] pcm;
        long frames;
        try (AudioInputStream in = inner.render(sequence, bpm, padSeconds)) {
            frames = in.getFrameLength();
            pcm = in.readAllBytes();
        }
        double gain = gainFor(pcm);
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int v = (int) Math.round(((short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8))) * gain);
            v = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
            pcm[i] = (byte) v;
            pcm[i + 1] = (byte) (v >> 8);
        }
        return new AudioInputStream(new ByteArrayInputStream(pcm), WavRenderer.FORMAT, frames);
    }

    /** Gain that reaches the loudness target, reduced if needed so the loudest peak stays under the ceiling. */
    static double gainFor(byte[] pcm) {
        double sum = 0;
        int peak = 0;
        int n = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int v = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sum += (double) v * v;
            peak = Math.max(peak, Math.abs(v));
            n++;
        }
        if (n == 0 || peak == 0) {
            return 1.0;
        }
        double rms = Math.sqrt(sum / n) / 32768.0;
        double toTarget = Math.pow(10, TARGET_DB / 20) / rms;
        double toCeiling = Math.pow(10, CEILING_DB / 20) / (peak / 32768.0);
        return Math.min(toTarget, toCeiling);
    }
}
