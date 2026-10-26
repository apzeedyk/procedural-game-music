package com.jazzerator.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FluidSynth itself isn't run in tests (it's an external program); these cover how it's invoked and trimmed. */
class FluidSynthRendererTest {
    @Test
    void commandRendersOfflineToA16BitWav() {
        List<String> cmd = FluidSynthRenderer.command("fluidsynth", new File("font.sf2"), new File("song.mid"),
                new File("out.wav"));
        assertEquals("fluidsynth", cmd.get(0));
        assertTrue(cmd.containsAll(List.of("-n", "-i", "-F", "-T", "wav", "-O", "s16", "-r", "44100")));
        assertTrue(cmd.get(cmd.size() - 2).endsWith("font.sf2"));
        assertTrue(cmd.get(cmd.size() - 1).endsWith("song.mid"));
    }

    @Test
    void outputIsTrimmedOrPaddedToTheExactLength() {
        assertArrayEquals(new byte[]{1, 2}, FluidSynthRenderer.exactLength(new byte[]{1, 2, 3, 4}, 2));
        assertArrayEquals(new byte[]{1, 2, 0, 0}, FluidSynthRenderer.exactLength(new byte[]{1, 2}, 4));
    }
}
