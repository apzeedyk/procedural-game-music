package com.jazzerator.preset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.theory.Key;
import com.jazzerator.theory.Mode;
import com.jazzerator.theory.Note;
import com.jazzerator.util.Rng;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PresetsTest {
    @Test
    void sameSeedSameSpec() {
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(SongSpec.random(new Rng(seed), null), SongSpec.random(new Rng(seed), null));
        }
    }

    @Test
    void specValuesStayInsideTheirPairing() {
        for (long seed = 0; seed < 2000; seed++) {
            SongSpec s = SongSpec.random(new Rng(seed), null);
            Pairing p = s.pairing();
            assertTrue(s.genre().pairings().contains(p));
            assertTrue(p.modes().items().contains(s.key().mode()));
            assertTrue(p.tonics().items().stream().anyMatch(t -> Note.parse(t) == s.key().tonic()));
            assertTrue(s.tempo() >= p.tempoMin() && s.tempo() <= p.tempoMax());
            assertTrue(p.feels().items().contains(s.feel()));
            assertTrue(p.beatsPerBar().items().contains(s.beatsPerBar()));
            assertTrue(p.sectionBars().items().contains(s.sectionBars()));
            assertTrue(p.structures().items().contains(s.structure()));
            assertTrue(p.instruments().items().contains(s.instruments()));
        }
    }

    /** Solo brass leads sound thin, so they're rare in the march pairing, but still possible for variety. */
    @Test
    void heroicMarchFavorsBrassSectionButKeepsSoloLeadsPossible() {
        GenrePreset overworld = Presets.byId("overworld").orElseThrow();
        int marches = 0;
        int solo = 0;
        for (long seed = 0; seed < 4000; seed++) {
            SongSpec s = SongSpec.random(new Rng(seed), overworld);
            if (!s.pairing().name().equals("Heroic march")) {
                continue;
            }
            marches++;
            int lead = s.instruments().lead();
            if (lead == Instruments.TRUMPET || lead == Instruments.FRENCH_HORN) {
                solo++;
            }
        }
        double share = solo / (double) marches;
        assertTrue(share > 0.05 && share < 0.3, "solo brass share " + share);
    }

    @Test
    void everyGenreGetsPicked() {
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            seen.add(SongSpec.random(new Rng(seed), null).genre().id());
        }
        assertEquals(Presets.ALL.size(), seen.size());
    }

    @Test
    void everyProgressionParsesInEveryAllowedKey() {
        for (GenrePreset g : Presets.ALL) {
            for (Pairing p : g.pairings()) {
                for (Mode mode : p.modes().items()) {
                    for (String tonic : p.tonics().items()) {
                        Key key = Key.of(tonic, mode);
                        for (String prog : p.progressions().items()) {
                            for (String symbol : prog.split(" ")) {
                                assertDoesNotThrow(() -> key.chord(symbol), p.name() + ": " + symbol);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A tonic numeral written as "I" must only appear in major modes and "i" only in minor ones. */
    @Test
    void tonicNumeralsMatchMode() {
        for (GenrePreset g : Presets.ALL) {
            for (Pairing p : g.pairings()) {
                for (Mode mode : p.modes().items()) {
                    for (String prog : p.progressions().items()) {
                        for (String symbol : prog.split(" ")) {
                            boolean tonic = symbol.matches("^[Ii](?![IiVv]).*") && !symbol.contains("/");
                            if (tonic) {
                                assertEquals(mode.isMinor(), symbol.charAt(0) == 'i',
                                        p.name() + " in " + mode + ": " + prog);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void forcedGenreIsRespected() {
        GenrePreset boss = Presets.byId("boss").orElseThrow();
        for (long seed = 0; seed < 100; seed++) {
            assertEquals(boss, SongSpec.random(new Rng(seed), boss).genre());
        }
        assertFalse(Presets.byId("nope").isPresent());
    }
}
