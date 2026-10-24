package com.jazzerator.form;

import com.jazzerator.melody.MelodyNote;
import com.jazzerator.preset.SongSpec;
import java.util.List;

/** A fully planned song: the spec, its sections and harmony, and the lead melody. */
public record Song(SongSpec spec, List<Section> sections, List<MelodyNote> melody) {
    public long endTick() {
        return sections.isEmpty() ? 0 : sections.get(sections.size() - 1).endTick();
    }

    public int totalBars() {
        return sections.stream().mapToInt(Section::bars).sum();
    }

    /** Human-readable chord chart, one line per section, bars separated by '|'. */
    public String chart() {
        StringBuilder sb = new StringBuilder();
        int bar = 1;
        for (Section s : sections) {
            sb.append(String.format("%-7s bars %2d-%-2d |", s.label(), bar, bar + s.bars() - 1));
            long currentBar = 0;
            for (ChordSlot slot : s.chords()) {
                long slotBar = (slot.startTick() - s.startTick()) / s.ticksPerBar();
                if (slotBar != currentBar) {
                    sb.append(" |");
                    currentBar = slotBar;
                }
                sb.append(' ').append(spec.key().spell(slot.chord()));
            }
            sb.append('\n');
            bar += s.bars();
        }
        return sb.toString();
    }
}
