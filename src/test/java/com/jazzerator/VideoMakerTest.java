package com.jazzerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.preset.Presets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoMakerTest {
    @TempDir
    Path dir;

    @Test
    void framePatternFromNumberedFiles() throws IOException {
        for (String name : new String[]{"frame0000.png", "frame0001.png", "frame0002.png"}) {
            Files.createFile(dir.resolve(name));
        }
        assertEquals("frame%04d.png", VideoMaker.framePattern(dir.toFile()));
    }

    @Test
    void framePatternRejectsEmptyOrUnnumberedFolders() throws IOException {
        File empty = Files.createDirectory(dir.resolve("empty")).toFile();
        assertThrows(IOException.class, () -> VideoMaker.framePattern(empty));
        Path odd = Files.createDirectory(dir.resolve("odd"));
        Files.createFile(odd.resolve("background.png"));
        assertThrows(IOException.class, () -> VideoMaker.framePattern(odd.toFile()));
    }

    @Test
    void assTimestamps() {
        assertEquals("0:00:00.00", VideoMaker.assTime(0));
        assertEquals("0:01:05.50", VideoMaker.assTime(65.5));
        assertEquals("1:00:01.25", VideoMaker.assTime(3601.25));
    }

    @Test
    void oneCaptionPairPerSongMatchingChapters() {
        List<Compilation.Entry> entries = Compilation.plan(1, Presets.byId("lofi").orElseThrow(), null, 3);
        String ass = VideoMaker.captions(entries);
        long dialogues = ass.lines().filter(l -> l.startsWith("Dialogue:")).count();
        assertEquals(entries.size() * 2L, dialogues);
        for (Compilation.Entry e : entries) {
            assertTrue(ass.contains("Dialogue: 0," + VideoMaker.assTime(e.startSeconds()) + ","));
            assertTrue(ass.contains("seed " + e.song().spec().seed()));
        }
        assertTrue(ass.contains("procedurally generated, not AI"));
    }
}
