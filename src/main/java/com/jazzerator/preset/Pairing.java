package com.jazzerator.preset;

import com.jazzerator.arrange.Groove;
import com.jazzerator.form.Structure;
import com.jazzerator.harmony.HarmonyStyle;
import com.jazzerator.melody.OrnamentStyle;
import com.jazzerator.theory.Mode;
import com.jazzerator.util.Weighted;

/**
 * One common pairing of musical choices within a genre, e.g. "Phrygian fury" for boss battles. Every field is
 * constrained to values that sound right together; generation only varies within these bounds.
 *
 * @param progressions roman-numeral progressions (space separated, roots relative to the tonic's major scale)
 */
public record Pairing(
        String name,
        double weight,
        Weighted<Mode> modes,
        Weighted<String> tonics,
        int tempoMin,
        int tempoMax,
        Weighted<Feel> feels,
        Weighted<Integer> beatsPerBar,
        Weighted<Integer> sectionBars,
        Weighted<String> progressions,
        Weighted<Structure> structures,
        Weighted<Instruments> instruments,
        HarmonyStyle harmony,
        OrnamentStyle ornaments,
        Groove groove) {
}
