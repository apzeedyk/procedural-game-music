package com.jazzerator;

import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.form.Structure;
import com.jazzerator.harmony.HarmonyPlanner;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.melody.MelodyPlanner;
import com.jazzerator.melody.Ornamenter;
import com.jazzerator.preset.GenrePreset;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.util.Rng;
import java.util.List;

/** The full pipeline from seed to Song: spec, harmony, melody, ornaments. */
public final class SongGenerator {
    private SongGenerator() {
    }

    public static Song generate(long seed, GenrePreset forcedGenre) {
        return generate(seed, forcedGenre, null);
    }

    public static Song generate(long seed, GenrePreset forcedGenre, Structure forcedStructure) {
        Rng rng = new Rng(seed);
        SongSpec spec = SongSpec.random(rng, forcedGenre, forcedStructure);
        List<Section> sections = HarmonyPlanner.plan(spec, rng);
        List<MelodyNote> melody = Ornamenter.ornament(MelodyPlanner.plan(spec, sections, rng), spec, sections, rng);
        return new Song(spec, sections, melody);
    }
}
