package com.jazzerator.arrange;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Presets;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.jupiter.api.Test;

class ArrangerTest {
    private static final long PPQ = MidiWriter.PPQ;

    @Test
    void sameSeedProducesIdenticalMidiBytes() throws Exception {
        for (long seed = 0; seed < 20; seed++) {
            assertArrayEquals(bytes(SongGenerator.generate(seed, null)), bytes(SongGenerator.generate(seed, null)));
        }
    }

    @Test
    void everyNoteOnHasANoteOffAndSaneVelocity() {
        for (long seed = 0; seed < 200; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Sequence seq = Arranger.render(song);
            Map<String, Integer> open = new HashMap<>();
            for (Track track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    if (track.get(i).getMessage() instanceof ShortMessage m) {
                        String key = m.getChannel() + ":" + m.getData1();
                        if (m.getCommand() == ShortMessage.NOTE_ON && m.getData2() > 0) {
                            assertTrue(m.getData2() >= 1 && m.getData2() <= 127);
                            open.merge(key, 1, Integer::sum);
                        } else if (m.getCommand() == ShortMessage.NOTE_OFF) {
                            open.merge(key, -1, Integer::sum);
                        }
                    }
                }
            }
            open.values().forEach(v -> assertEquals(0, v));
        }
    }

    @Test
    void everyGrooveProducesDrumsAndEveryPartPlays() {
        Set<Groove> seen = EnumSet.noneOf(Groove.class);
        for (long seed = 0; seed < 300; seed++) {
            Song song = SongGenerator.generate(seed, null);
            seen.add(song.spec().pairing().groove());
            Set<Integer> channels = new HashSet<>();
            for (Track track : Arranger.render(song).getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    if (track.get(i).getMessage() instanceof ShortMessage m && m.getCommand() == ShortMessage.NOTE_ON) {
                        channels.add(m.getChannel());
                    }
                }
            }
            assertEquals(Set.of(Arranger.LEAD, Arranger.COMP, Arranger.BASS, Arranger.DRUMS), channels,
                    "seed " + seed);
        }
        assertEquals(EnumSet.allOf(Groove.class), seen);
    }

    @Test
    void compAttacksEveryChordChange() {
        for (long seed = 0; seed < 200; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Timing timing = new Timing(song);
            Set<Long> compOnsets = onsets(Arranger.render(song), Arranger.COMP);
            for (Section s : song.sections()) {
                for (ChordSlot slot : s.chords()) {
                    assertTrue(compOnsets.contains(timing.map(slot.startTick())),
                            "seed " + seed + " chord at " + slot.startTick() + " never attacked");
                }
            }
        }
    }

    @Test
    void nothingSoundsAfterTheSongEnds() {
        for (long seed = 0; seed < 200; seed++) {
            Song song = SongGenerator.generate(seed, null);
            for (Track track : Arranger.render(song).getTracks()) {
                assertTrue(track.ticks() <= song.endTick(), "seed " + seed);
            }
        }
    }

    @Test
    void swingMapIsMonotonicAndFixesBeats() {
        Song swing = SongGenerator.generate(1, Presets.byId("town").orElseThrow());
        Song straight = SongGenerator.generate(1, Presets.byId("boss").orElseThrow());
        for (Song song : new Song[]{swing, straight}) {
            Timing t = new Timing(song);
            long last = -1;
            for (long tick = 0; tick <= 4 * PPQ; tick += 5) {
                long mapped = t.map(tick);
                assertTrue(mapped > last || tick == 0);
                last = mapped;
            }
            assertEquals(PPQ, t.map(PPQ));
            assertEquals(3 * PPQ, t.map(3 * PPQ));
        }
        Timing straightTiming = new Timing(straight);
        assertEquals(PPQ / 2, straightTiming.map(PPQ / 2));
    }

    @Test
    void drumPatternsStayInsideTheBar() {
        for (Groove g : Groove.values()) {
            for (int beats : new int[]{3, 4}) {
                assertFalse(DrumPart.pattern(g, beats).isEmpty());
                DrumPart.pattern(g, beats).forEach(h -> assertTrue(h.beat() >= 0 && h.beat() < beats, g + " " + beats));
            }
        }
    }

    private static Set<Long> onsets(Sequence seq, int channel) {
        Set<Long> ticks = new HashSet<>();
        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                if (e.getMessage() instanceof ShortMessage m && m.getCommand() == ShortMessage.NOTE_ON
                        && m.getChannel() == channel && m.getData2() > 0) {
                    ticks.add(e.getTick());
                }
            }
        }
        return ticks;
    }

    private static byte[] bytes(Song song) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MidiSystem.write(Arranger.render(song), 1, out);
        return out.toByteArray();
    }
}
