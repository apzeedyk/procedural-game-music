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
        GenrePreset genre = forcedGenre != null ? forcedGenre : weightedGenre(rng);
        Pairing p = weightedPairing(rng, genre.pairings());
        Key key = new Key(Note.parse(p.tonics().pick(rng)), p.modes().pick(rng));
        return new SongSpec(
                rng.seed(),
                genre,
                p,
                key,
                rng.range(p.tempoMin(), p.tempoMax()),
                p.feels().pick(rng),
                p.beatsPerBar().pick(rng),
                p.sectionBars().pick(rng),
                pickStructure(rng, p, forcedStructure),
                rng.pick(p.instruments()));
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
                "Instr:     " + Instruments.name(instruments.lead()) + " / " + Instruments.name(instruments.comp())
                        + " / " + Instruments.name(instruments.bass()),
                "Groove:    " + pairing.groove().displayName());
    }

    private static Structure pickStructure(Rng rng, Pairing p, Structure forced) {
        Structure picked = p.structures().pick(rng);
        return forced != null ? forced : picked;
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
