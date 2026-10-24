package com.jazzerator.melody;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.form.Section;
import com.jazzerator.harmony.HarmonyPlanner;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Feel;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Mode;
import com.jazzerator.util.Rng;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrnamenterTest {
    private static final int PPQ = MidiWriter.PPQ;
    private static final Key C = Key.of("C", Mode.IONIAN);
    private static final OrnamentStyle EVERYTHING = new OrnamentStyle(1, 1, 1, 1, 1, 1, 1, 1, 1);

    private static OrnamentStyle only(String field) {
        return new OrnamentStyle(
                field.equals("run") ? 1 : 0, field.equals("trill") ? 1 : 0, field.equals("passing") ? 1 : 0,
                field.equals("neighbor") ? 1 : 0, field.equals("grace") ? 1 : 0, field.equals("split") ? 1 : 0,
                field.equals("merge") ? 1 : 0, field.equals("staccato") ? 1 : 0, 0);
    }

    private static List<MelodyNote> orn(List<MelodyNote> plain, OrnamentStyle style) {
        return Ornamenter.ornament(plain, style, C, 120, 4, Feel.STRAIGHT, new Rng(1));
    }

    @Test
    void noneStyleChangesNothing() {
        List<MelodyNote> plain = List.of(new MelodyNote(72, 0, PPQ, 90), new MelodyNote(76, PPQ, PPQ, 90));
        assertEquals(plain, orn(plain, OrnamentStyle.NONE));
    }

    @Test
    void runClimbsByScaleIntoTheTarget() {
        List<MelodyNote> plain = List.of(new MelodyNote(72, 0, 2L * PPQ, 90), new MelodyNote(79, 2L * PPQ, PPQ, 90));
        List<MelodyNote> out = orn(plain, only("run"));
        assertTrue(out.size() >= 4);
        assertEquals(72, out.get(0).pitch());
        MelodyNote target = out.get(out.size() - 1);
        assertEquals(79, target.pitch());
        MelodyNote approach = out.get(out.size() - 2);
        assertEquals(77, approach.pitch()); // F, one scale step below G
        assertEquals(target.startTick(), approach.endTick());
    }

    @Test
    void trillAlternatesWithUpperNeighborThenHolds() {
        List<MelodyNote> plain = List.of(new MelodyNote(72, 0, 2L * PPQ, 90));
        List<MelodyNote> out = orn(plain, only("trill"));
        assertEquals(72, out.get(0).pitch());
        assertEquals(74, out.get(1).pitch());
        assertEquals(72, out.get(out.size() - 1).pitch());
        assertEquals(2L * PPQ, out.get(out.size() - 1).endTick());
    }

    @Test
    void passingToneFillsAThird() {
        List<MelodyNote> plain = List.of(new MelodyNote(60, 0, PPQ, 90), new MelodyNote(64, PPQ, PPQ, 90));
        List<MelodyNote> out = orn(plain, only("passing"));
        assertEquals(62, out.get(1).pitch());
    }

    @Test
    void mergeAbsorbsOnlyWeakBeatNotes() {
        List<MelodyNote> plain = List.of(new MelodyNote(60, 0, PPQ, 90), new MelodyNote(62, PPQ, PPQ, 90),
                new MelodyNote(64, 2L * PPQ, PPQ, 90));
        List<MelodyNote> out = orn(plain, only("merge"));
        // Beat 2 is weak and gets absorbed; beat 3 is strong and survives.
        assertEquals(new MelodyNote(60, 0, 2L * PPQ, 90), out.get(0));
        assertEquals(64, out.get(1).pitch());
    }

    @Test
    void invariantsHoldAcrossManySongs() {
        for (long seed = 0; seed < 300; seed++) {
            Rng rng = new Rng(seed);
            SongSpec spec = SongSpec.random(rng, null);
            List<Section> sections = HarmonyPlanner.plan(spec, rng);
            List<MelodyNote> plain = MelodyPlanner.plan(spec, sections, rng);
            long ticksPerBar = (long) PPQ * spec.beatsPerBar();
            for (OrnamentStyle style : new OrnamentStyle[]{EVERYTHING, OrnamentStyle.SHRED, OrnamentStyle.JAZZY}) {
                List<MelodyNote> out = Ornamenter.ornament(plain, style, spec.key(), spec.tempo(),
                        spec.beatsPerBar(), spec.feel(), new Rng(seed));
                long lastEnd = 0;
                for (MelodyNote n : out) {
                    assertTrue(n.durationTicks() > 0, "seed " + seed);
                    assertTrue(n.startTick() >= lastEnd, "overlap at seed " + seed + " tick " + n.startTick());
                    assertEquals(n.startTick() / ticksPerBar, (n.endTick() - 1) / ticksPerBar,
                            "crosses barline at seed " + seed);
                    assertTrue(n.pitch() >= MelodyPlanner.MIN_PITCH && n.pitch() <= MelodyPlanner.MAX_PITCH);
                    lastEnd = n.endTick();
                }
                // Downbeat notes survive untouched in pitch and start.
                for (MelodyNote p : plain) {
                    if (p.startTick() % ticksPerBar == 0) {
                        assertTrue(out.stream().anyMatch(o -> o.startTick() == p.startTick() && o.pitch() == p.pitch()),
                                "seed " + seed + " lost downbeat at " + p.startTick());
                    }
                }
            }
        }
    }
}
