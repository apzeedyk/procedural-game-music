package com.jazzerator.preset;

import com.jazzerator.form.Structure;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Note;
import com.jazzerator.util.Rng;
import com.jazzerator.util.Weighted;
import java.util.List;

/** The resolved top-level choices for one song. */
public record SongSpec(
        long seed,
        GenrePreset genre,
        Pairing pairing,
        Key key,
        int tempo,
        Feel feel,
        int beatsPerBar,
        int sectionBars,
        Structure structure,
        Instruments instruments) {

    public static SongSpec random(Rng rng, GenrePreset forcedGenre) {
        return random(rng, forcedGenre, null);
    }

    /**
     * Genre is picked by weight (unless forced), then a pairing within it, then values within that pairing. A forced
     * structure replaces the picked one after all random draws, so every other choice for the seed is unchanged.
     */
    public static SongSpec random(Rng rng, GenrePreset forcedGenre, Structure forcedStructure) {
        // Each choice has its own random stream, so changing one table never reshuffles the others.
        GenrePreset genre = forcedGenre != null ? forcedGenre : weightedGenre(rng.fork("genre"));
        Pairing p = weightedPairing(rng.fork("pairing", genre.id()), genre.pairings());
        Key key = new Key(Note.parse(p.tonics().pick(rng.fork("tonic"))), p.modes().pick(rng.fork("mode")));
        Structure structure = forcedStructure != null ? forcedStructure : p.structures().pick(rng.fork("structure"));
        return new SongSpec(
                rng.seed(),
                genre,
                p,
                key,
                rng.fork("tempo").range(p.tempoMin(), p.tempoMax()),
                p.feels().pick(rng.fork("feel")),
                p.beatsPerBar().pick(rng.fork("meter")),
                p.sectionBars().pick(rng.fork("section-bars")),
                structure,
                p.instruments().pick(rng.fork("instruments")));
    }

    /** The same song performed on different instruments (instruments don't affect the composition). */
    public SongSpec withInstruments(Instruments replacement) {
        return new SongSpec(seed, genre, pairing, key, tempo, feel, beatsPerBar, sectionBars, structure, replacement);
    }

    public int totalBars() {
        return structure.totalBars(sectionBars);
    }

    public double durationSeconds() {
        return totalBars() * beatsPerBar * 60.0 / tempo;
    }

    public String summary() {
        int secs = (int) Math.round(durationSeconds());
        return String.join("\n",
                "Seed:      " + seed,
                "Genre:     " + genre.name() + " / " + pairing.name(),
                "Key:       " + key.name(),
                "Tempo:     " + tempo + " BPM, " + feel.displayName() + ", " + beatsPerBar + "/4",
                "Structure: " + structure.displayName() + " (" + sectionBars + "-bar sections)",
                "Length:    " + totalBars() + " bars, ~" + secs / 60 + ":" + String.format("%02d", secs % 60),
                "Instr:     " + Instruments.name(instruments.lead())
                        + (instruments.hasHarmony() ? " + " + Instruments.name(instruments.harmony()) + " (3rds)" : "")
                        + " / " + Instruments.name(instruments.comp()) + " / " + Instruments.name(instruments.bass()),
                "Groove:    " + pairing.groove().displayName());
    }

    private static GenrePreset weightedGenre(Rng rng) {
        Weighted<GenrePreset> w = null;
        for (GenrePreset g : Presets.ALL) {
            w = w == null ? Weighted.of(g, g.weight()) : w.and(g, g.weight());
        }
        return w.pick(rng);
    }

    private static Pairing weightedPairing(Rng rng, List<Pairing> pairings) {
        Weighted<Pairing> w = null;
        for (Pairing p : pairings) {
            w = w == null ? Weighted.of(p, p.weight()) : w.and(p, p.weight());
        }
        return w.pick(rng);
    }
}
