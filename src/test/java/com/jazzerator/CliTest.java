package com.jazzerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.form.Structure;
import com.jazzerator.preset.Instruments;
import com.jazzerator.preset.Presets;
import com.jazzerator.preset.SongSpec;
import com.jazzerator.util.Rng;
import org.junit.jupiter.api.Test;

class CliTest {
    @Test
    void parsesAllOptions() {
        Cli cli = Cli.parse("--seed", "42", "--genre", "boss", "--structure", "rondo", "--count", "3", "--play",
                "--out", "song.mid");
        assertEquals(42, cli.seed());
        assertEquals("boss", cli.genre().id());
        assertEquals(Structure.RONDO, cli.structure());
        assertEquals(3, cli.count());
        assertTrue(cli.play());
        assertEquals("song-2.mid", cli.outputFor(1, 43, "boss"));
    }

    @Test
    void defaults() {
        Cli cli = Cli.parse();
        assertTrue(cli.seed() >= 1 && cli.seed() < 1_000_000);
        assertNull(cli.genre());
        assertEquals(1, cli.count());
        assertEquals("jazzerator-lofi-7.mid", cli.outputFor(0, 7, "lofi"));
    }

    @Test
    void badInputIsAUsageError() {
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--genre", "polka"));
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--structure", "abcd"));
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--seed", "abc"));
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--seed"));
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--count", "0"));
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--bogus"));
    }

    @Test
    void forcedStructureLeavesOtherChoicesUnchanged() {
        for (long seed = 0; seed < 100; seed++) {
            SongSpec plain = SongSpec.random(new Rng(seed), null);
            SongSpec forced = SongSpec.random(new Rng(seed), null, Structure.ABBA);
            assertEquals(Structure.ABBA, forced.structure());
            assertEquals(plain.key(), forced.key());
            assertEquals(plain.tempo(), forced.tempo());
            assertEquals(plain.pairing(), forced.pairing());
            assertEquals(plain.instruments(), forced.instruments());
        }
    }

    @Test
    void structureIdsRoundTrip() {
        for (Structure s : Structure.values()) {
            assertEquals(s, Structure.byId(s.id()).orElseThrow());
        }
        assertEquals(Structure.VERSE_CHORUS_BRIDGE, Structure.byId("verse-chorus-bridge").orElseThrow());
    }

    @Test
    void allGeneralMidiProgramsHaveNames() {
        assertEquals("Acoustic Grand Piano", Instruments.name(0));
        assertEquals("Trumpet", Instruments.name(Instruments.TRUMPET));
        assertEquals("Square Lead", Instruments.name(Instruments.SQUARE_LEAD));
        assertEquals("Gunshot", Instruments.name(127));
        assertTrue(Presets.ALL.size() > 0);
    }

    @Test
    void instrumentOverridesByNameOrNumber() {
        assertEquals(81, Instruments.byName("saw lead").getAsInt());
        assertEquals(81, Instruments.byName("Saw Lead").getAsInt());
        assertEquals(57, Instruments.byName("trombone").getAsInt());
        assertEquals(0, Instruments.byName("0").getAsInt());
        assertTrue(Instruments.byName("lead").isEmpty()); // ambiguous
        assertThrows(Cli.UsageException.class, () -> Cli.parse("--lead", "kazoo"));

        Cli cli = Cli.parse("--lead", "saw lead", "--harmony", "square lead");
        var song = SongGenerator.generate(3, Presets.byId("overworld").orElseThrow());
        var changed = cli.withOverrides(song);
        assertEquals(81, changed.spec().instruments().lead());
        assertEquals(80, changed.spec().instruments().harmony());
        assertEquals(song.melody(), changed.melody());    // composition untouched
        assertEquals(song.sections(), changed.sections());
    }

    @Test
    void forcingAPairingKeepsTheSeedsOtherChoices() {
        var heroic = Presets.byId("overworld").orElseThrow().pairings().get(0);
        for (long seed = 0; seed < 50; seed++) {
            var forced = SongGenerator.generate(seed, null, null, heroic).spec();
            assertEquals(heroic, forced.pairing());
            assertEquals("overworld", forced.genre().id());
        }
    }
}
