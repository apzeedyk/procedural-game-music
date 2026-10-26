package com.jazzerator.arrange;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jazzerator.SongGenerator;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import com.jazzerator.melody.MelodyNote;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.preset.Instruments;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.jupiter.api.Test;

class PianoPartTest {
    @Test
    void pianoNeverDuplicatesTheHarmonyVoicesJob() {
        int checked = 0;
        for (long seed = 0; seed < 600; seed++) {
            Song song = SongGenerator.generate(seed, null);
            if (!Instruments.isKeyboard(song.spec().instruments().comp()) || !song.spec().instruments().hasHarmony()) {
                continue;
            }
            for (Section s : song.sections()) {
                checked++;
                assertNotEquals(PianoPart.Role.CHORDS, PianoPart.roleFor(song, s.label()),
                        "seed " + seed + ": piano pads the middle while a harmony voice is playing");
            }
        }
        assertTrue(checked > 10, "too few piano + harmony songs: " + checked);
    }

    @Test
    void everyRoleGetsUsedAndPianoSpansAWideRange() {
        Set<PianoPart.Role> seen = EnumSet.noneOf(PianoPart.Role.class);
        int widest = 0;
        for (long seed = 0; seed < 600; seed++) {
            Song song = SongGenerator.generate(seed, null);
            if (!Instruments.isKeyboard(song.spec().instruments().comp())) {
                continue;
            }
            for (Section s : song.sections()) {
                seen.add(PianoPart.roleFor(song, s.label()));
            }
            int lo = 127;
            int hi = 0;
            for (Track t : Arranger.render(song).getTracks()) {
                for (int i = 0; i < t.size(); i++) {
                    if (t.get(i).getMessage() instanceof ShortMessage m && m.getCommand() == ShortMessage.NOTE_ON
                            && m.getData2() > 0 && m.getChannel() == Arranger.COMP) {
                        lo = Math.min(lo, m.getData1());
                        hi = Math.max(hi, m.getData1());
                    }
                }
            }
            widest = Math.max(widest, hi - lo);
        }
        assertTrue(seen.containsAll(EnumSet.of(PianoPart.Role.BASS, PianoPart.Role.RHYTHM, PianoPart.Role.LEAD)),
                "roles seen: " + seen);
        assertTrue(widest >= 36, "piano never spans 3 octaves (widest " + widest + ")");
    }

    @Test
    void leadRoleDoublesTheMelodyAnOctaveUp() {
        for (long seed = 0; seed < 600; seed++) {
            Song song = SongGenerator.generate(seed, null);
            if (!Instruments.isKeyboard(song.spec().instruments().comp())) {
                continue;
            }
            Sequence seq = Arranger.render(song);
            Timing timing = new Timing(song);
            Set<String> pianoNotes = new HashSet<>();
            for (Track t : seq.getTracks()) {
                for (int i = 0; i < t.size(); i++) {
                    MidiEvent e = t.get(i);
                    if (e.getMessage() instanceof ShortMessage m && m.getCommand() == ShortMessage.NOTE_ON
                            && m.getData2() > 0 && m.getChannel() == Arranger.COMP) {
                        pianoNotes.add(m.getData1() + "@" + e.getTick());
                    }
                }
            }
            for (Section s : song.sections()) {
                if (PianoPart.roleFor(song, s.label()) != PianoPart.Role.LEAD) {
                    continue;
                }
                for (MelodyNote n : song.melody()) {
                    if (n.startTick() >= s.startTick() && n.startTick() < s.endTick()
                            && n.durationTicks() > MidiWriter.PPQ / 8) {
                        long at = timing.map(n.startTick());
                        assertTrue(pianoNotes.contains((n.pitch() + 12) + "@" + at) || pianoNotes.contains(n.pitch() + "@" + at),
                                "seed " + seed + " melody note at " + n.startTick() + " not doubled");
                    }
                }
                return; // one lead-role song is enough
            }
        }
    }
}
