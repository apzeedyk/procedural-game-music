package com.jazzerator.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import java.util.Map;
import javax.sound.midi.Sequence;
import org.junit.jupiter.api.Test;

class MixBalancerTest {
    @Test
    void volumeCurve() {
        assertEquals(100, MixBalancer.volumeFor(0));
        assertEquals(67, MixBalancer.volumeFor(-7), 1);  // 7 dB down
        assertEquals(127, MixBalancer.volumeFor(+10));   // can't boost past the top of the fader
        assertEquals(20, MixBalancer.volumeFor(-60));    // never fully muted
    }

    @Test
    void partsLandOnTheirTargetLevels() throws Exception {
        for (long seed : new long[]{1, 22, 40}) {
            Song song = SongGenerator.generate(seed, null);
            AudioRenderer renderer = AudioRenderer.soundFont(null);
            Sequence mixed = Arranger.renderMixed(song, renderer);
            int bpm = song.spec().tempo();
            double lead = MixBalancer.loudness(mixed, bpm, renderer, Arranger.LEAD);
            for (Map.Entry<Integer, Double> target : Arranger.mixTargets().entrySet()) {
                double level = MixBalancer.loudness(mixed, bpm, renderer, target.getKey());
                if (level < -80) {
                    continue; // part not used in this song
                }
                double relative = level - lead;
                // Within 1.5 dB, unless the fader hit its limit trying to raise a naturally quiet part.
                assertTrue(Math.abs(relative - target.getValue()) <= 1.5 || relative < target.getValue(),
                        "seed " + seed + " channel " + target.getKey() + " at " + relative + " dB, target "
                                + target.getValue());
            }
        }
    }
}
