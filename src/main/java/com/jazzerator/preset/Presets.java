package com.jazzerator.preset;

import static com.jazzerator.form.Structure.AABA;
import static com.jazzerator.form.Structure.ABAB;
import static com.jazzerator.form.Structure.ABBA;
import static com.jazzerator.form.Structure.INTRO_LOOP_OUTRO;
import static com.jazzerator.form.Structure.RONDO;
import static com.jazzerator.form.Structure.VERSE_CHORUS;
import static com.jazzerator.form.Structure.VERSE_CHORUS_BRIDGE;
import static com.jazzerator.preset.Feel.DRIVING;
import static com.jazzerator.preset.Feel.SHUFFLE;
import static com.jazzerator.preset.Feel.STRAIGHT;
import static com.jazzerator.preset.Feel.SWING;
import static com.jazzerator.preset.Instruments.*;
import static com.jazzerator.theory.Mode.AEOLIAN;
import static com.jazzerator.theory.Mode.DORIAN;
import static com.jazzerator.theory.Mode.HARMONIC_MINOR;
import static com.jazzerator.theory.Mode.IONIAN;
import static com.jazzerator.theory.Mode.LYDIAN;
import static com.jazzerator.theory.Mode.MIXOLYDIAN;
import static com.jazzerator.theory.Mode.PHRYGIAN;

import com.jazzerator.arrange.Groove;
import com.jazzerator.form.Structure;
import com.jazzerator.harmony.HarmonyStyle;
import com.jazzerator.melody.OrnamentStyle;
import com.jazzerator.theory.Mode;
import com.jazzerator.util.Weighted;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The table of video game genres and their common pairings. */
public final class Presets {
    private static final Weighted<Integer> FOUR_FOUR = Weighted.of(4, 1.0);
    private static final Weighted<Integer> MOSTLY_EIGHT_BARS = Weighted.of(8, 3.0).and(4, 1.0);

    public static final List<GenrePreset> ALL = List.of(
            new GenrePreset("overworld", "Overworld", 1.5, List.of(
                    pairing("Heroic march", 3)
                            .modes(Weighted.of(IONIAN, 1.0))
                            .tonics(Weighted.uniform("C", "D", "F", "G", "Bb"))
                            .tempo(120, 144)
                            .feels(Weighted.of(STRAIGHT, 1.0))
                            .progressions(Weighted.of("I V vi IV", 3.0).and("I IV V IV", 2.0)
                                    .and("I vi IV V", 2.0).and("I iii IV V", 1.0).and("IV V iii vi", 1.0))
                            .structures(Weighted.of(AABA, 2.0).and(VERSE_CHORUS, 2.0).and(INTRO_LOOP_OUTRO, 1.0))
                            .instruments(Weighted.of(new Instruments(BRASS, STRINGS, ACOUSTIC_BASS), 3.0)
                                    .and(new Instruments(BRASS, STRINGS, FINGER_BASS, TROMBONE), 2.0)
                                    .and(new Instruments(BRASS, STRINGS, FINGER_BASS, FRENCH_HORN), 0.5)
                                    .and(new Instruments(TRUMPET, STRINGS, FINGER_BASS, FRENCH_HORN), 0.5)
                                    .and(new Instruments(FRENCH_HORN, STRINGS, ACOUSTIC_BASS, TROMBONE), 0.25))
                            .groove(Groove.MARCH)
                            .build(),
                    pairing("Pastoral fields", 2)
                            .modes(Weighted.of(LYDIAN, 2.0).and(IONIAN, 1.0))
                            .tonics(Weighted.uniform("C", "F", "G", "D"))
                            .tempo(88, 108)
                            .feels(Weighted.of(STRAIGHT, 1.0))
                            .beats(Weighted.of(3, 2.0).and(4, 1.0))
                            .progressions(Weighted.of("I II IV I", 2.0).and("Imaj7 II Imaj7 II", 2.0)
                                    .and("I II vi V", 1.0))
                            .structures(Weighted.of(AABA, 2.0).and(ABAB, 1.0).and(ABBA, 1.0))
                            .instruments(new Instruments(FLUTE, HARP, ACOUSTIC_BASS),
                                    new Instruments(CLARINET, NYLON_GUITAR, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.LUSH)
                            .ornaments(OrnamentStyle.PASTORAL)
                            .groove(Groove.LIGHT)
                            .build(),
                    pairing("Mixolydian adventure", 1.5)
                            .modes(Weighted.of(MIXOLYDIAN, 1.0))
                            .tonics(Weighted.uniform("D", "E", "G", "A"))
                            .tempo(126, 150)
                            .feels(Weighted.of(STRAIGHT, 2.0).and(SHUFFLE, 1.0))
                            .progressions(Weighted.of("I bVII IV I", 3.0).and("I v bVII IV", 1.0)
                                    .and("I bVII I bVII", 1.0))
                            .structures(Weighted.of(VERSE_CHORUS, 1.0).and(ABAB, 1.0).and(INTRO_LOOP_OUTRO, 1.0))
                            .instruments(Weighted.of(new Instruments(BRASS, OVERDRIVE_GUITAR, FINGER_BASS), 3.0)
                                    .and(new Instruments(BRASS, PIANO, FINGER_BASS, TROMBONE), 1.5)
                                    .and(new Instruments(TRUMPET, PIANO, FINGER_BASS, FRENCH_HORN), 0.5))
                            .groove(Groove.ROCK)
                            .build())),

            new GenrePreset("dungeon", "Dungeon", 1.0, List.of(
                    pairing("Dark cavern", 3)
                            .modes(Weighted.of(AEOLIAN, 2.0).and(PHRYGIAN, 1.0))
                            .tonics(Weighted.uniform("D", "E", "A", "C"))
                            .tempo(70, 92)
                            .feels(Weighted.of(STRAIGHT, 1.0))
                            .progressions(Weighted.of("i bVI bVII i", 3.0).and("i bII i bVII", 1.0)
                                    .and("i iv i bVI", 2.0).and("i bVI iv bVII", 1.0))
                            .structures(Weighted.of(ABAB, 2.0).and(ABBA, 1.0).and(INTRO_LOOP_OUTRO, 1.0))
                            .instruments(new Instruments(OBOE, SLOW_STRINGS, ACOUSTIC_BASS),
                                    new Instruments(CLARINET, WARM_PAD, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.LUSH)
                            .ornaments(OrnamentStyle.SPARSE)
                            .groove(Groove.TRIBAL)
                            .build(),
                    pairing("Ancient ruins", 2)
                            .modes(Weighted.of(DORIAN, 1.0))
                            .tonics(Weighted.uniform("D", "E", "A", "G"))
                            .tempo(88, 110)
                            .feels(Weighted.of(STRAIGHT, 2.0).and(SHUFFLE, 1.0))
                            .beats(Weighted.of(4, 2.0).and(3, 1.0))
                            .progressions(Weighted.of("i IV i IV", 2.0).and("i bVII IV i", 2.0)
                                    .and("i ii bIII IV", 1.0))
                            .structures(Weighted.of(ABAB, 1.0).and(RONDO, 1.0).and(AABA, 1.0))
                            .instruments(new Instruments(PAN_FLUTE, MARIMBA, ACOUSTIC_BASS),
                                    new Instruments(FLUTE, HARP, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.LUSH)
                            .ornaments(OrnamentStyle.PASTORAL)
                            .groove(Groove.TRIBAL)
                            .build())),

            new GenrePreset("boss", "Boss Battle", 1.2, List.of(
                    pairing("Phrygian fury", 3)
                            .modes(Weighted.of(PHRYGIAN, 1.0))
                            .tonics(Weighted.uniform("E", "D", "A"))
                            .tempo(160, 185)
                            .feels(Weighted.of(DRIVING, 1.0))
                            .progressions(Weighted.of("i bII i bVII", 3.0).and("i bVI bVII i", 2.0)
                                    .and("i bII bIII bII", 1.0))
                            .structures(Weighted.of(INTRO_LOOP_OUTRO, 2.0).and(ABAB, 1.0).and(AABA, 1.0))
                            .instruments(new Instruments(DISTORTION_GUITAR, BRASS, SYNTH_BASS, DISTORTION_GUITAR),
                                    new Instruments(SAW_LEAD, DISTORTION_GUITAR, SYNTH_BASS))
                            .harmony(HarmonyStyle.POWER)
                            .ornaments(OrnamentStyle.SHRED)
                            .groove(Groove.DRIVING)
                            .build(),
                    pairing("Gothic organ", 2)
                            .modes(Weighted.of(HARMONIC_MINOR, 1.0))
                            .tonics(Weighted.uniform("C", "D", "G", "B"))
                            .tempo(138, 160)
                            .feels(Weighted.of(STRAIGHT, 1.0).and(DRIVING, 1.0))
                            .progressions(Weighted.of("i iv V i", 3.0).and("i bVI iv V", 2.0)
                                    .and("i viio7 i V", 1.0))
                            .structures(Weighted.of(AABA, 1.0).and(INTRO_LOOP_OUTRO, 1.0).and(RONDO, 1.0))
                            .instruments(new Instruments(CHURCH_ORGAN, CHOIR, SYNTH_BASS),
                                    new Instruments(BRASS, CHURCH_ORGAN, FINGER_BASS))
                            .harmony(HarmonyStyle.POWER)
                            .ornaments(OrnamentStyle.SHRED)
                            .groove(Groove.DRIVING)
                            .build())),

            new GenrePreset("town", "Town / Shop", 1.2, List.of(
                    pairing("Jazzy shop", 2)
                            .modes(Weighted.of(IONIAN, 1.0))
                            .tonics(Weighted.uniform("F", "Bb", "Eb", "C"))
                            .tempo(100, 132)
                            .feels(Weighted.of(SWING, 1.0))
                            .progressions(Weighted.of("Imaj7 vi7 ii7 V7", 3.0).and("ii7 V7 Imaj7 Imaj7", 2.0)
                                    .and("iii7 vi7 ii7 V7", 1.0).and("Imaj7 IVmaj7 iii7 vi7", 1.0))
                            .structures(Weighted.of(AABA, 3.0).and(ABAB, 1.0))
                            .instruments(new Instruments(VIBRAPHONE, E_PIANO, ACOUSTIC_BASS),
                                    new Instruments(CLARINET, PIANO, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.JAZZY)
                            .ornaments(OrnamentStyle.JAZZY)
                            .groove(Groove.SWING)
                            .build(),
                    pairing("Cozy village", 2)
                            .modes(Weighted.of(IONIAN, 2.0).and(MIXOLYDIAN, 1.0))
                            .tonics(Weighted.uniform("C", "G", "D", "F"))
                            .tempo(92, 116)
                            .feels(Weighted.of(STRAIGHT, 2.0).and(SHUFFLE, 1.0))
                            .beats(Weighted.of(4, 1.0).and(3, 1.0))
                            .progressions(Weighted.of("I IV I V", 2.0).and("I vi IV V", 2.0)
                                    .and("I IV ii V", 1.0))
                            .structures(Weighted.of(AABA, 1.0).and(ABAB, 1.0).and(ABBA, 1.0))
                            .instruments(new Instruments(ACCORDION, NYLON_GUITAR, ACOUSTIC_BASS),
                                    new Instruments(FLUTE, NYLON_GUITAR, ACOUSTIC_BASS))
                            .ornaments(OrnamentStyle.PASTORAL)
                            .groove(Groove.LIGHT)
                            .build())),

            new GenrePreset("chiptune", "Chiptune / 8-bit", 1.2, List.of(
                    pairing("8-bit hero", 3)
                            .modes(Weighted.of(IONIAN, 1.0))
                            .tonics(Weighted.uniform("C", "D", "E", "G", "A"))
                            .tempo(140, 170)
                            .feels(Weighted.of(STRAIGHT, 2.0).and(DRIVING, 1.0))
                            .progressions(Weighted.of("I V vi IV", 2.0).and("vi IV I V", 2.0)
                                    .and("I bVI bVII I", 1.0).and("I IV V I", 1.0))
                            .structures(Weighted.of(INTRO_LOOP_OUTRO, 2.0).and(VERSE_CHORUS, 1.0).and(AABA, 1.0))
                            .instruments(new Instruments(SQUARE_LEAD, SQUARE_LEAD, SYNTH_BASS),
                                    new Instruments(SQUARE_LEAD, SAW_LEAD, SYNTH_BASS, SQUARE_LEAD))
                            .harmony(HarmonyStyle.POWER)
                            .ornaments(OrnamentStyle.CHIP)
                            .groove(Groove.CHIP)
                            .build(),
                    pairing("Chip castle", 2)
                            .modes(Weighted.of(AEOLIAN, 2.0).and(HARMONIC_MINOR, 1.0))
                            .tonics(Weighted.uniform("A", "C", "D", "E"))
                            .tempo(120, 145)
                            .feels(Weighted.of(STRAIGHT, 1.0).and(DRIVING, 1.0))
                            .progressions(Weighted.of("i bVI bIII bVII", 3.0).and("i iv bVII bIII", 1.0)
                                    .and("i bVII bVI bVII", 2.0))
                            .structures(Weighted.of(ABAB, 1.0).and(INTRO_LOOP_OUTRO, 1.0))
                            .instruments(new Instruments(SQUARE_LEAD, SAW_LEAD, SYNTH_BASS))
                            .harmony(HarmonyStyle.POWER)
                            .ornaments(OrnamentStyle.CHIP)
                            .groove(Groove.CHIP)
                            .build())),

            new GenrePreset("lofi", "Lo-fi Save Room", 1.0, List.of(
                    pairing("Save point", 2)
                            .modes(Weighted.of(IONIAN, 1.0))
                            .tonics(Weighted.uniform("C", "D", "F", "Eb", "Ab"))
                            .tempo(70, 88)
                            .feels(Weighted.of(SWING, 2.0).and(STRAIGHT, 1.0))
                            .progressions(Weighted.of("ii9 V9 Imaj7 Imaj7", 2.0).and("IVmaj7 iii7 ii7 Imaj7", 2.0)
                                    .and("Imaj7 vi7 ii7 V7", 1.0).and("ii7 bVII9 Imaj7 Imaj7", 1.0))
                            .structures(Weighted.of(AABA, 1.0).and(ABAB, 2.0))
                            .instruments(new Instruments(MUSIC_BOX, E_PIANO, FINGER_BASS),
                                    new Instruments(CELESTA, E_PIANO, FINGER_BASS),
                                    new Instruments(VIBRAPHONE, E_PIANO, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.JAZZY)
                            .ornaments(OrnamentStyle.JAZZY)
                            .groove(Groove.LOFI)
                            .build(),
                    pairing("Rainy window", 1)
                            .modes(Weighted.of(DORIAN, 1.0))
                            .tonics(Weighted.uniform("C", "D", "F", "G", "A"))
                            .tempo(68, 84)
                            .feels(Weighted.of(SWING, 2.0).and(STRAIGHT, 1.0))
                            .progressions(Weighted.of("i7 IV7 i7 IV7", 2.0).and("i7 bIIImaj7 IV7 i7", 1.0)
                                    .and("i9 bVIImaj7 IV9 i7", 1.0).and("iv7 bVII7 bIIImaj7 i7", 1.0))
                            .structures(Weighted.of(AABA, 1.0).and(ABAB, 2.0))
                            .instruments(new Instruments(E_PIANO, WARM_PAD, FINGER_BASS),
                                    new Instruments(VIBRAPHONE, E_PIANO, FINGER_BASS))
                            .harmony(HarmonyStyle.JAZZY)
                            .ornaments(OrnamentStyle.JAZZY)
                            .groove(Groove.LOFI)
                            .build())),

            new GenrePreset("racing", "Racing", 0.8, List.of(
                    pairing("Turbo circuit", 1)
                            .modes(Weighted.of(AEOLIAN, 1.0).and(DORIAN, 1.0))
                            .tonics(Weighted.uniform("E", "F#", "A", "B", "C#"))
                            .tempo(150, 176)
                            .feels(Weighted.of(DRIVING, 1.0))
                            .progressions(Weighted.of("i bVI bVII i", 2.0).and("i bVII bVI bVII", 2.0)
                                    .and("i bIII bVII IV", 1.0))
                            .structures(Weighted.of(INTRO_LOOP_OUTRO, 1.0).and(VERSE_CHORUS_BRIDGE, 1.0)
                                    .and(VERSE_CHORUS, 1.0))
                            .instruments(new Instruments(SAW_LEAD, OVERDRIVE_GUITAR, SYNTH_BASS),
                                    new Instruments(SQUARE_LEAD, ROCK_ORGAN, SYNTH_BASS))
                            .harmony(HarmonyStyle.POWER)
                            .ornaments(OrnamentStyle.SHRED)
                            .groove(Groove.DRIVING)
                            .build())),

            new GenrePreset("puzzle", "Puzzle", 0.8, List.of(
                    pairing("Brain teaser", 2)
                            .modes(Weighted.of(IONIAN, 2.0).and(LYDIAN, 1.0))
                            .tonics(Weighted.uniform("C", "F", "G", "D"))
                            .tempo(100, 124)
                            .feels(Weighted.of(STRAIGHT, 1.0).and(SHUFFLE, 1.0))
                            .progressions(Weighted.of("I vi ii V", 2.0).and("Imaj7 IVmaj7 Imaj7 IVmaj7", 1.0)
                                    .and("I iii IV iv", 1.0))
                            .structures(Weighted.of(ABAB, 1.0).and(RONDO, 2.0))
                            .instruments(new Instruments(MARIMBA, PIZZICATO, ACOUSTIC_BASS),
                                    new Instruments(CELESTA, PIZZICATO, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.LUSH)
                            .groove(Groove.LIGHT)
                            .build(),
                    pairing("Mysterious mechanism", 1)
                            .modes(Weighted.of(DORIAN, 1.0).and(AEOLIAN, 1.0))
                            .tonics(Weighted.uniform("D", "E", "G", "A"))
                            .tempo(84, 104)
                            .feels(Weighted.of(STRAIGHT, 1.0))
                            .beats(Weighted.of(3, 1.0).and(4, 1.0))
                            .progressions(Weighted.of("i bVII bVI bVII", 2.0).and("i iv bVII bIII", 1.0)
                                    .and("i bIII iv i", 1.0))
                            .structures(Weighted.of(ABAB, 1.0).and(RONDO, 1.0).and(ABBA, 1.0))
                            .instruments(new Instruments(CELESTA, HARP, ACOUSTIC_BASS),
                                    new Instruments(VIBRAPHONE, STRINGS, ACOUSTIC_BASS))
                            .harmony(HarmonyStyle.LUSH)
                            .ornaments(OrnamentStyle.SPARSE)
                            .groove(Groove.TRIBAL)
                            .build())));

    private Presets() {
    }

    public static Optional<GenrePreset> byId(String id) {
        String wanted = id.toLowerCase(Locale.ROOT);
        return ALL.stream().filter(g -> g.id().equals(wanted)).findFirst();
    }

    private static Builder pairing(String name, double weight) {
        return new Builder(name, weight);
    }

    /** Fluent builder so the table above stays readable. Meter and section length have sensible defaults. */
    private static final class Builder {
        private final String name;
        private final double weight;
        private Weighted<Mode> modes;
        private Weighted<String> tonics;
        private int tempoMin;
        private int tempoMax;
        private Weighted<Feel> feels;
        private Weighted<Integer> beats = FOUR_FOUR;
        private Weighted<Integer> sectionBars = MOSTLY_EIGHT_BARS;
        private Weighted<String> progressions;
        private Weighted<Structure> structures;
        private Weighted<Instruments> instruments;
        private HarmonyStyle harmony = HarmonyStyle.DEFAULT;
        private OrnamentStyle ornaments = OrnamentStyle.DEFAULT;
        private Groove groove = Groove.ROCK;

        Builder(String name, double weight) {
            this.name = name;
            this.weight = weight;
        }

        Builder modes(Weighted<Mode> v) { modes = v; return this; }
        Builder tonics(Weighted<String> v) { tonics = v; return this; }
        Builder tempo(int min, int max) { tempoMin = min; tempoMax = max; return this; }
        Builder feels(Weighted<Feel> v) { feels = v; return this; }
        Builder beats(Weighted<Integer> v) { beats = v; return this; }
        Builder progressions(Weighted<String> v) { progressions = v; return this; }
        Builder structures(Weighted<Structure> v) { structures = v; return this; }
        Builder instruments(Instruments... v) { instruments = Weighted.uniform(v); return this; }
        Builder instruments(Weighted<Instruments> v) { instruments = v; return this; }
        Builder harmony(HarmonyStyle v) { harmony = v; return this; }
        Builder ornaments(OrnamentStyle v) { ornaments = v; return this; }
        Builder groove(Groove v) { groove = v; return this; }

        Pairing build() {
            return new Pairing(name, weight, modes, tonics, tempoMin, tempoMax, feels, beats, sectionBars,
                    progressions, structures, instruments, harmony, ornaments, groove);
        }
    }
}
