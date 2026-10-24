package com.jazzerator.harmony;

import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Structure;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.theory.Chord;
import com.jazzerator.theory.Key;
import com.jazzerator.util.Rng;
import com.jazzerator.util.Weighted;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expands the structure into sections and gives each distinct label its own progression (one chord per bar,
 * repeated to fill the section). Each section ends with a cadence for its role, then is decorated once per label
 * so repeats sound the same. Intros borrow the first section's progression; the song ends on a full cadence.
 */
public final class HarmonyPlanner {
    private static final int MAX_DISTINCT_TRIES = 10;

    private HarmonyPlanner() {
    }

    /** How a section ends: home sections resolve to the tonic, away sections pause on the dominant. */
    public enum Cadence { AUTHENTIC, HALF }

    public static Cadence cadenceFor(String label) {
        return switch (label) {
            case "A", "Chorus", "Outro" -> Cadence.AUTHENTIC;
            default -> Cadence.HALF;
        };
    }

    public static List<Section> plan(SongSpec spec, Rng rng) {
        long ticksPerBar = (long) MidiWriter.PPQ * spec.beatsPerBar();
        Key key = spec.key();
        Map<String, List<Chord>> progressions = assignProgressions(spec, rng);
        Chord dominant = dominant(key, rng);
        Map<String, List<ChordSlot>> templates = new HashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();

        List<String> labels = spec.structure().labels();
        List<Section> sections = new ArrayList<>();
        long tick = 0;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int bars = Structure.barsFor(label, spec.sectionBars());
            // The final section always resolves, even if its label normally ends on a half cadence.
            boolean last = i == labels.size() - 1;
            Cadence cadence = last ? Cadence.AUTHENTIC : cadenceFor(label);
            String templateKey = cadence == cadenceFor(label) ? label : label + "#final";
            List<ChordSlot> template = templates.computeIfAbsent(templateKey, k -> {
                List<ChordSlot> plain = plainTemplate(progressions.get(label), bars, ticksPerBar);
                applyCadence(plain, cadence, key, dominant);
                return HarmonyDecorator.decorate(plain, key, spec.pairing().harmony(), rng);
            });

            List<ChordSlot> slots = new ArrayList<>();
            for (ChordSlot t : template) {
                slots.add(new ChordSlot(t.chord(), tick + t.startTick(), t.durationTicks()));
            }
            int occurrence = occurrences.merge(label, 1, Integer::sum);
            sections.add(new Section(label, occurrence, tick, bars, ticksPerBar, List.copyOf(slots)));
            tick += bars * ticksPerBar;
        }
        return sections;
    }

    public static Chord tonic(Key key) {
        return key.chord(key.mode().isMinor() ? "i" : "I");
    }

    /** The cadential dominant that suits the mode, chosen once per song. */
    static Chord dominant(Key key, Rng rng) {
        Weighted<String> options = switch (key.mode()) {
            case IONIAN, LYDIAN, HARMONIC_MINOR, LOCRIAN -> Weighted.of("V7", 1.0);
            case MIXOLYDIAN -> Weighted.of("bVII", 2.0).and("V7", 1.0);
            case AEOLIAN, DORIAN -> Weighted.of("V7", 2.0).and("bVII", 1.0);
            case PHRYGIAN -> Weighted.of("bII", 2.0).and("bvii", 1.0);
        };
        return key.chord(options.pick(rng));
    }

    private static Chord predominant(Key key) {
        return key.chord(key.mode().isMinor() ? "iv" : "IV");
    }

    /**
     * Authentic: penultimate bar dominant, last bar tonic. Half: last bar dominant. If the bar before the
     * cadence already holds the dominant, it becomes a predominant so the cadence is approached, not repeated.
     */
    private static void applyCadence(List<ChordSlot> slots, Cadence cadence, Key key, Chord dominant) {
        int n = slots.size();
        int dominantBar = cadence == Cadence.AUTHENTIC ? n - 2 : n - 1;
        if (dominantBar < 0) {
            return;
        }
        replace(slots, dominantBar, dominant);
        if (cadence == Cadence.AUTHENTIC) {
            replace(slots, n - 1, tonic(key));
        }
        if (dominantBar >= 1 && slots.get(dominantBar - 1).chord().root() == dominant.root()) {
            replace(slots, dominantBar - 1, predominant(key));
        }
    }

    private static void replace(List<ChordSlot> slots, int index, Chord chord) {
        ChordSlot s = slots.get(index);
        slots.set(index, new ChordSlot(chord, s.startTick(), s.durationTicks()));
    }

    private static List<ChordSlot> plainTemplate(List<Chord> progression, int bars, long ticksPerBar) {
        List<ChordSlot> slots = new ArrayList<>();
        for (int bar = 0; bar < bars; bar++) {
            slots.add(new ChordSlot(progression.get(bar % progression.size()), bar * ticksPerBar, ticksPerBar));
        }
        return slots;
    }

    /** One progression per distinct label, avoiding reuse between labels when the pairing has enough options. */
    private static Map<String, List<Chord>> assignProgressions(SongSpec spec, Rng rng) {
        Map<String, String> chosen = new HashMap<>();
        for (String label : spec.structure().labels()) {
            if (chosen.containsKey(label) || isFrame(label)) {
                continue;
            }
            String prog = spec.pairing().progressions().pick(rng);
            for (int t = 0; t < MAX_DISTINCT_TRIES && chosen.containsValue(prog); t++) {
                prog = spec.pairing().progressions().pick(rng);
            }
            chosen.put(label, prog);
        }
        // Intro and outro reuse the first main section's harmony.
        String first = spec.structure().labels().stream().filter(l -> !isFrame(l)).findFirst().orElseThrow();
        chosen.putIfAbsent("Intro", chosen.get(first));
        chosen.putIfAbsent("Outro", chosen.get(first));

        Map<String, List<Chord>> result = new HashMap<>();
        chosen.forEach((label, prog) ->
                result.put(label, Arrays.stream(prog.split(" ")).map(spec.key()::chord).toList()));
        return result;
    }

    private static boolean isFrame(String label) {
        return label.equals("Intro") || label.equals("Outro");
    }
}
