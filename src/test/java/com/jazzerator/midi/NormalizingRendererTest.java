package com.jazzerator.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioInputStream;
import org.junit.jupiter.api.Test;

class NormalizingRendererTest {
    /** Songs that render at different raw levels all come out at the same loudness, without clipping. */
    @Test
    void songsLandAtTheSameLoudness() throws Exception {
        AudioRenderer chip = new ChipRenderer();
        AudioRenderer normalized = new NormalizingRenderer(chip);
        double low = 0;
        double high = -200;
        for (long seed : new long[]{1, 2, 3, 7}) {
            Song song = SongGenerator.generate(seed, null);
            Sequence seq = Arranger.render(song);
            try (AudioInputStream in = normalized.render(seq, song.spec().tempo(), 0)) {
                byte[] pcm = in.readAllBytes();
                double[] stats = stats(pcm);
                low = Math.min(low, stats[0]);
                high = Math.max(high, stats[0]);
                assertTrue(stats[1] <= Math.pow(10, NormalizingRenderer.CEILING_DB / 20) * 32768 + 1, "peak over ceiling");
            }
        }
        assertTrue(high - low <= 1.0 || high <= NormalizingRenderer.TARGET_DB + 0.5,
                "loudness spread " + (high - low) + " dB");
    }

    @Test
    void silenceIsLeftAlone() {
        assertEquals(1.0, NormalizingRenderer.gainFor(new byte[8]));
    }

    /** {RMS dBFS, peak sample}. */
    private static double[] stats(byte[] pcm) {
        double sum = 0;
        int peak = 0;
        int n = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int v = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sum += (double) v * v;
            peak = Math.max(peak, Math.abs(v));
            n++;
        }
        return new double[]{20 * Math.log10(Math.sqrt(sum / n) / 32768.0), peak};
    }
}
