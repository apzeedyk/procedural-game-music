package com.jazzerator.harmony;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.theory.Key;
import com.jazzerator.theory.Note;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Modal songs keep their mode: no chord (from the preset progression, the cadence, or a decoration) contains a
 * note that would erase the mode's character, like a leading tone in Mixolydian (B7's D# in E Mixolydian).
 */
class ModeIntegrityTest {
    @Test
    void modalSongsNeverUseTheirAvoidedNotes() {
        List<String> problems = new ArrayList<>();
        int modal = 0;
        for (long seed = 0; seed < 1500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Key key = song.spec().key();
            if (key.mode().avoid().length == 0) {
                continue;
            }
            modal++;
            for (Section s : song.sections()) {
                for (ChordSlot slot : s.chords()) {
                    for (int offset : key.mode().avoid()) {
                        if (slot.chord().containsPc(key.tonic() + offset)) {
                            problems.add("seed " + seed + " " + song.spec().pairing().name() + " " + key.name()
                                    + ": " + key.spell(slot.chord()) + " has " + Note.name(key.tonic() + offset,
                                    key.prefersFlats()));
                        }
                    }
                }
            }
        }
        assertTrue(modal > 100, "too few modal songs sampled: " + modal);
        assertTrue(problems.isEmpty(), problems.size() + " mode-breaking chords, e.g. "
                + problems.subList(0, Math.min(8, problems.size())));
    }

    /**
     * Modal harmony habits (Open Music Theory; modal rock practice): one chord per bar except a final sus-resolve,
     * and no dominant 7th falling a fifth, which would tonicize the parent major key instead of the modal tonic.
     */
    @Test
    void modalSongsKeepModalHabits() {
        List<String> problems = new ArrayList<>();
        for (long seed = 0; seed < 1500; seed++) {
            Song song = SongGenerator.generate(seed, null);
            Key key = song.spec().key();
            if (key.mode().avoid().length == 0) {
                continue;
            }
            for (Section s : song.sections()) {
                List<ChordSlot> chords = s.chords();
                for (int i = 0; i < chords.size(); i++) {
                    ChordSlot slot = chords.get(i);
                    boolean lastBar = slot.startTick() >= s.endTick() - s.ticksPerBar();
                    if (Math.floorMod(slot.startTick() - s.startTick(), s.ticksPerBar()) != 0 && !lastBar) {
                        problems.add("seed " + seed + " " + key.name() + ": mid-bar change to "
                                + key.spell(slot.chord()) + " in " + s.label());
                    }
                    if (i + 1 < chords.size() && slot.chord().quality() == com.jazzerator.theory.Chord.Quality.DOM7
                            && chords.get(i + 1).chord().root() == Note.pc(slot.chord().root() + 5)) {
                        problems.add("seed " + seed + " " + key.name() + ": " + key.spell(slot.chord()) + " -> "
                                + key.spell(chords.get(i + 1).chord()) + " tonicizes the parent key");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " modal habit violations, e.g. "
                + problems.subList(0, Math.min(8, problems.size())));
    }
}
