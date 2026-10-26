package com.jazzerator.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import java.util.Map;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioInputStream;
import org.junit.jupiter.api.Test;

class ChipRendererTest {
    private final ChipRenderer chip = new ChipRenderer();

    @Test
    void rendersTheFullSongWithAudibleSoundAndNoClipping() throws Exception {
        Song song = SongGenerator.generate(3, null);
        Sequence seq = Arranger.renderMixed(song, chip);
        try (AudioInputStream in = chip.render(seq, song.spec().tempo(), 0)) {
            assertEquals(WavRenderer.seconds(seq, song.spec().tempo(), 0), in.getFrameLength() / 44_100.0, 0.01);
            byte[] pcm = in.readAllBytes();
            int peak = 0;
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                peak = Math.max(peak, Math.abs((short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8))));
            }
            assertTrue(peak > 2000, "too quiet: " + peak);
            assertTrue(peak < 32767, "clipped");
        }
    }

    @Test
    void sameSequenceSameAudio() throws Exception {
        Song song = SongGenerator.generate(5, null);
        Sequence seq = Arranger.render(song);
        try (AudioInputStream a = chip.render(seq, song.spec().tempo(), 0);
             AudioInputStream b = chip.render(seq, song.spec().tempo(), 0)) {
            assertArrayEquals(a.readAllBytes(), b.readAllBytes());
        }
    }

    @Test
    void autoMixWorksWithChipSound() throws Exception {
        Song song = SongGenerator.generate(3, null);
        Sequence mixed = Arranger.renderMixed(song, chip);
        int bpm = song.spec().tempo();
        double lead = MixBalancer.loudness(mixed, bpm, chip, Arranger.LEAD);
        for (Map.Entry<Integer, Double> target : Arranger.mixTargets(chip).entrySet()) {
            double level = MixBalancer.loudness(mixed, bpm, chip, target.getKey());
            if (level < -80) {
                continue;
            }
            double relative = level - lead;
            assertTrue(Math.abs(relative - target.getValue()) <= 1.5,
                    "channel " + target.getKey() + " at " + relative + " dB, target " + target.getValue());
        }
    }
}
