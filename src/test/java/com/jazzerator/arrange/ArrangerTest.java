package com.jazzerator.arrange;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.melody.Harmonizer;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Mode;
import com.jazzerator.preset.Instruments;
import com.jazzerator.preset.Presets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            Set<Integer> expected = new HashSet<>(Set.of(Arranger.LEAD, Arranger.COMP, Arranger.BASS, Arranger.DRUMS));
            if (song.spec().instruments().hasHarmony()) {
                expected.add(Arranger.HARMONY);
            }
            assertEquals(expected, channels, "seed " + seed);
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
    void slowAttackCompInstrumentsSustainInsteadOfPulsing() {
        int checked = 0;
        for (long seed = 0; seed < 400; seed++) {
            Song song = SongGenerator.generate(seed, null);
            if (!Instruments.isSlowAttack(song.spec().instruments().comp())) {
                continue;
            }
            checked++;
            Map<Integer, Long> onAt = new HashMap<>();
            for (Track track : Arranger.render(song).getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent e = track.get(i);
                    if (!(e.getMessage() instanceof ShortMessage m) || m.getChannel() != Arranger.COMP) {
                        continue;
                    }
                    if (m.getCommand() == ShortMessage.NOTE_ON && m.getData2() > 0) {
                        onAt.put(m.getData1(), e.getTick());
                    } else if (m.getCommand() == ShortMessage.NOTE_OFF) {
                        long length = e.getTick() - onAt.remove(m.getData1());
                        assertTrue(length >= PPQ, "seed " + seed + ": " + length + "-tick note on "
                                + Instruments.name(song.spec().instruments().comp()));
                    }
                }
            }
        }
        assertTrue(checked > 10, "too few slow-attack songs sampled: " + checked);
        assertEquals(Groove.CompStyle.SUSTAIN,
                CompPart.articulation(Groove.CompStyle.BLOCK_QUARTERS, Instruments.STRINGS));
        assertEquals(Groove.CompStyle.BLOCK_QUARTERS,
                CompPart.articulation(Groove.CompStyle.BLOCK_QUARTERS, Instruments.PIANO));
        assertEquals(Groove.CompStyle.STRUM, CompPart.articulation(Groove.CompStyle.STRUM, Instruments.WARM_PAD));
    }

    /**
     * The comp keeps a steady register across the main sections, so it doesn't vanish under the melody in one
     * section and pop out in the next.
     */
    @Test
    void compRegisterIsSteadyAcrossSections() {
        for (long seed = 0; seed < 300; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Sequence seq = Arranger.render(song);
            Timing timing = new Timing(song);
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (Section s : song.sections()) {
                boolean frame = s.label().equals("Intro") || s.label().equals("Outro"); // may settle lower at the end
                long from = timing.map(s.startTick());
                long to = timing.map(s.endTick());
                long sum = 0;
                int count = 0;
                for (Track track : seq.getTracks()) {
                    for (int i = 0; i < track.size(); i++) {
                        MidiEvent e = track.get(i);
                        if (e.getMessage() instanceof ShortMessage m && m.getCommand() == ShortMessage.NOTE_ON
                                && m.getData2() > 0 && m.getChannel() == Arranger.COMP
                                && e.getTick() >= from && e.getTick() < to) {
                            assertTrue(m.getData1() <= 70, "seed " + seed + " comp note too high: " + m.getData1());
                            sum += m.getData1();
                            count++;
                        }
                    }
                }
                if (count > 0 && !frame) {
                    low = Math.min(low, sum / (double) count);
                    high = Math.max(high, sum / (double) count);
                }
            }
            assertTrue(high - low <= 4.0, "seed " + seed + " comp register drifts " + (high - low) + " semitones");
        }
    }

    /** Chords are voiced underneath the melody voices, so accompaniment and tune never trade places. */
    @Test
    void compStaysUnderTheMelodyVoices() {
        int slots = 0;
        int underneath = 0;
        int withinCeiling = 0;
        for (long seed = 0; seed < 300; seed++) {
            Song song = SongGenerator.generate(seed, null);
            List<MelodyNote> upper = new ArrayList<>(song.melody());
            if (song.spec().instruments().hasHarmony()) {
                upper.addAll(Harmonizer.thirdsBelow(song.melody(), song.sections(), song.spec().key().scale(),
                        song.spec().beatsPerBar()));
            }
            List<int[]> voicings = CompPart.planVoicings(song, upper);
            int i = 0;
            for (Section s : song.sections()) {
                for (ChordSlot slot : s.chords()) {
                    int[] v = voicings.get(i++);
                    int top = v[v.length - 1];
                    if (top <= CompPart.ceiling(slot, upper)) {
                        withinCeiling++;
                    }
                    int lowestUpper = upper.stream()
                            .filter(n -> n.startTick() < slot.endTick() && n.endTick() > slot.startTick()
                                    && n.durationTicks() > PPQ / 8)
                            .mapToInt(MelodyNote::pitch).min().orElse(Integer.MAX_VALUE);
                    slots++;
                    if (top < lowestUpper) {
                        underneath++;
                    }
                }
            }
        }
        // Only very wide chords under a very low melody may miss; the bass-protecting floor may force an overlap.
        assertTrue(withinCeiling >= slots * 0.99, withinCeiling + " of " + slots + " chords fit under their ceiling");
        assertTrue(underneath >= slots * 0.95, underneath + " of " + slots + " chords sit under the melody voices");
    }

    @Test
    void suspensionResolvesByStepInPlace() {
        Key a = Key.of("A", Mode.MIXOLYDIAN);
        int[] sus = {50, 52, 57}; // D3 E3 A3 = Asus4
        int[] resolved = CompPart.resolveSuspension(a.chord("I").withQuality(Chord.Quality.SUS4), sus, a.chord("I"), 70);
        assertArrayEquals(new int[]{49, 52, 57}, resolved); // C#3 E3 A3: only the D moved, down a half step
    }

    /** Wherever a suspended chord resolves to its plain chord, exactly one voice moves, by step. */
    @Test
    void everySuspensionResolvesWithOneStepwiseVoice() {
        int resolutions = 0;
        for (long seed = 0; seed < 300; seed++) {
            Song song = SongGenerator.generate(seed, null);
            List<MelodyNote> upper = new ArrayList<>(song.melody());
            if (song.spec().instruments().hasHarmony()) {
                upper.addAll(Harmonizer.thirdsBelow(song.melody(), song.sections(), song.spec().key().scale(),
                        song.spec().beatsPerBar()));
            }
            List<int[]> voicings = CompPart.planVoicings(song, upper);
            List<ChordSlot> slots = song.sections().stream().flatMap(x -> x.chords().stream()).toList();
            for (int i = 1; i < slots.size(); i++) {
                Chord before = slots.get(i - 1).chord();
                Chord after = slots.get(i).chord();
                boolean suspension = (before.quality() == Chord.Quality.SUS4 || before.quality() == Chord.Quality.SUS2)
                        && before.root() == after.root() && after.size() == 3;
                int ceiling = CompPart.ceiling(slots.get(i), upper);
                if (!suspension || CompPart.resolveSuspension(before, voicings.get(i - 1), after, ceiling) == null) {
                    continue; // e.g. a sus2 rising into the melody: re-voicing is the right call there
                }
                int[] from = voicings.get(i - 1);
                int[] to = voicings.get(i);
                int changed = 0;
                int largest = 0;
                for (int k = 0; k < from.length; k++) {
                    if (from[k] != to[k]) {
                        changed++;
                        largest = Math.max(largest, Math.abs(from[k] - to[k]));
                    }
                }
                resolutions++;
                assertTrue(changed <= 1 && largest <= 2, "seed " + seed + " suspension at slot " + i
                        + " re-voiced: " + java.util.Arrays.toString(from) + " -> " + java.util.Arrays.toString(to));
            }
        }
        assertTrue(resolutions > 20, "too few suspensions sampled: " + resolutions);
    }

    /** Chords avoid low, muddy close intervals (the arranger's low interval limits) whenever a clean voicing fits. */
    @Test
    void voicingsRespectLowIntervalLimits() {
        int total = 0;
        int muddy = 0;
        for (long seed = 0; seed < 300; seed++) {
            Song song = SongGenerator.generate(seed, null);
            List<MelodyNote> upper = new ArrayList<>(song.melody());
            if (song.spec().instruments().hasHarmony()) {
                upper.addAll(Harmonizer.thirdsBelow(song.melody(), song.sections(), song.spec().key().scale(),
                        song.spec().beatsPerBar()));
            }
            for (int[] v : CompPart.planVoicings(song, upper)) {
                total++;
                if (CompPart.mudViolations(v) > 0) {
                    muddy++;
                }
            }
        }
        assertTrue(muddy <= total * 0.03, muddy + " of " + total + " chords are muddy");
    }

    @Test
    void drop2OpensACloseVoicing() {
        assertArrayEquals(new int[]{47, 52, 56, 62}, CompPart.drop2(new int[]{52, 56, 59, 62})); // B3 drops to B2
        assertEquals(1, CompPart.mudViolations(new int[]{50, 52, 56, 59})); // D3-E3 is below Eb3
        assertEquals(0, CompPart.mudViolations(new int[]{50, 56, 59}));     // rootless E7: clean
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
