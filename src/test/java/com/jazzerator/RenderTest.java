package com.jazzerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import com.jazzerator.midi.AudioRenderer;
import com.jazzerator.midi.WavRenderer;
import com.jazzerator.preset.Presets;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderTest {
    @TempDir
    Path dir;

    @Test
    void wavHasExpectedLengthAndIsNotSilent() throws Exception {
        Song song = SongGenerator.generate(1, Presets.byId("chiptune").orElseThrow()); // ~27 s
        Sequence seq = Arranger.render(song);
        File wav = dir.resolve("song.wav").toFile();
        WavRenderer.write(seq, song.spec().tempo(), null, wav);

        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
            double seconds = in.getFrameLength() / in.getFormat().getFrameRate();
            assertEquals(WavRenderer.seconds(seq, song.spec().tempo(), 0), seconds, 0.01);
            byte[] data = in.readAllBytes();
            int peak = 0;
            for (int i = 0; i + 1 < data.length; i += 2) {
                peak = Math.max(peak, Math.abs((short) ((data[i] & 0xff) | (data[i + 1] << 8))));
            }
            assertTrue(peak > 1000, "audio is silent (peak " + peak + ")");
        }
    }

    @Test
    void compilationPlanReachesTargetAndChaptersAreOrdered() {
        List<Compilation.Entry> entries = Compilation.plan(100, Presets.byId("boss").orElseThrow(), null, 5);
        Compilation.Entry last = entries.get(entries.size() - 1);
        assertTrue(last.startSeconds() + last.seconds() >= 5 * 60);
        assertTrue(last.startSeconds() < 5 * 60, "one song too many");
        assertEquals(0.0, entries.get(0).startSeconds());
        for (int i = 1; i < entries.size(); i++) {
            Compilation.Entry prev = entries.get(i - 1);
            assertEquals(prev.startSeconds() + prev.seconds(), entries.get(i).startSeconds(), 1e-9);
            assertEquals(100 + i, entries.get(i).song().spec().seed());
        }
        String[] lines = Compilation.tracklist(entries).split("\n");
        assertEquals(entries.size(), lines.length);
        assertTrue(lines[0].startsWith("0:00 Boss Battle"));
    }

    @Test
    void timestampsSwitchToHoursPastAnHour() {
        assertEquals("1:05", Compilation.timestamp(65.9, false));
        assertEquals("1:01:05", Compilation.timestamp(3665, true));
        assertEquals("0:00:00", Compilation.timestamp(0, true));
    }

    @Test
    void compilationWavMatchesPlannedLength() throws Exception {
        List<Compilation.Entry> entries = Compilation.plan(1, Presets.byId("chiptune").orElseThrow(), null, 0.5);
        File wav = dir.resolve("mix.wav").toFile();
        File txt = dir.resolve("mix.txt").toFile();
        Compilation.write(entries, AudioRenderer.soundFont(null), wav, txt);
        Compilation.Entry last = entries.get(entries.size() - 1);
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
            assertEquals(last.startSeconds() + last.seconds(), in.getFrameLength() / in.getFormat().getFrameRate(),
                    0.05);
        }
        assertTrue(txt.length() > 0);
    }
}
