package com.jazzerator.gui;

import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.ChordSlot;
import com.jazzerator.form.Section;
import com.jazzerator.form.Song;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.JPanel;

/** Draws a song as a piano roll: section bands, chord names, colored notes per part, and a playhead. */
final class PianoRollPanel extends JPanel {
    private static final Color BACKGROUND = new Color(0x1E1F24);
    private static final Color BAND_A = new Color(0x26282F);
    private static final Color BAND_B = new Color(0x2D3038);
    private static final Color GRID = new Color(0x3A3D46);
    private static final Color TEXT = new Color(0xC9CCD6);
    private static final Color PLAYHEAD = new Color(0xFF5A5F);
    private static final Map<Integer, Color> PART_COLORS = Map.of(
            Arranger.LEAD, new Color(0xFFA94D),
            Arranger.HARMONY, new Color(0xFFD43B),
            Arranger.COMP, new Color(0x4DABF7),
            Arranger.BASS, new Color(0x69DB7C),
            Arranger.DRUMS, new Color(0x868E96));
    private static final int HEADER = 36;  // section labels + chord names
    private static final int DRUM_LANE = 28;
    private static final int LEGEND = 18;

    private record Bar(int channel, int pitch, long start, long end) {
    }

    private Song song;
    private final List<Bar> notes = new ArrayList<>();
    private long totalTicks = 1;
    private int lowPitch = 36;
    private int highPitch = 84;
    private long playhead = -1;

    PianoRollPanel() {
        setPreferredSize(new Dimension(900, 420));
        setBackground(BACKGROUND);
    }

    void show(Song song, Sequence sequence) {
        this.song = song;
        notes.clear();
        Map<Integer, Long> open = new HashMap<>();
        List<MidiEvent> events = new ArrayList<>();
        for (Track t : sequence.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof ShortMessage) {
                    events.add(t.get(i));
                }
            }
        }
        events.sort((a, b) -> Long.compare(a.getTick(), b.getTick()));
        int lo = 127;
        int hi = 0;
        for (MidiEvent e : events) {
            ShortMessage m = (ShortMessage) e.getMessage();
            int key = m.getChannel() * 128 + m.getData1();
            if (m.getCommand() == ShortMessage.NOTE_ON && m.getData2() > 0) {
                open.put(key, e.getTick());
            } else if (m.getCommand() == ShortMessage.NOTE_OFF || m.getCommand() == ShortMessage.NOTE_ON) {
                Long start = open.remove(key);
                if (start != null) {
                    notes.add(new Bar(m.getChannel(), m.getData1(), start, e.getTick()));
                    if (m.getChannel() != Arranger.DRUMS) {
                        lo = Math.min(lo, m.getData1());
                        hi = Math.max(hi, m.getData1());
                    }
                }
            }
        }
        lowPitch = Math.max(0, lo - 2);
        highPitch = Math.min(127, hi + 2);
        totalTicks = Math.max(1, song.endTick());
        playhead = -1;
        repaint();
    }

    void setPlayhead(long tick) {
        playhead = tick;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (song == null) {
            g.setColor(TEXT);
            g.drawString("Press Generate to make a song.", 20, 30);
            return;
        }
        int rollTop = HEADER;
        int rollBottom = h - DRUM_LANE - LEGEND;
        double xScale = (w - 1.0) / totalTicks;
        double rowHeight = (rollBottom - rollTop) / (double) (highPitch - lowPitch + 1);

        // Section bands, bar lines, labels and chord names.
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        int band = 0;
        for (Section s : song.sections()) {
            int x0 = (int) (s.startTick() * xScale);
            int x1 = (int) (s.endTick() * xScale);
            g.setColor(band++ % 2 == 0 ? BAND_A : BAND_B);
            g.fillRect(x0, 0, x1 - x0, h);
            g.setColor(GRID);
            for (long bar = s.startTick(); bar < s.endTick(); bar += s.ticksPerBar()) {
                int x = (int) (bar * xScale);
                g.drawLine(x, rollTop, x, rollBottom);
            }
            g.setColor(TEXT);
            g.drawString(s.label(), x0 + 4, 14);
            g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            int lastX = -100;
            for (ChordSlot c : s.chords()) {
                int x = (int) (c.startTick() * xScale) + 2;
                if (x - lastX > 34) { // skip names that would overlap
                    g.drawString(song.spec().key().spell(c.chord()), x, 30);
                    lastX = x;
                }
            }
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        }

        // Notes: pitched parts on the roll, drums in their own lane at the bottom.
        for (Bar n : notes) {
            int x = (int) (n.start * xScale);
            int width = Math.max(1, (int) ((n.end - n.start) * xScale));
            g.setColor(PART_COLORS.getOrDefault(n.channel, TEXT));
            if (n.channel == Arranger.DRUMS) {
                g.fillRect(x, rollBottom + 4 + (n.pitch % 4) * 6, 2, 4);
            } else {
                int y = rollBottom - (int) ((n.pitch - lowPitch + 1) * rowHeight);
                g.fillRoundRect(x, y, width, Math.max(2, (int) rowHeight - 1), 3, 3);
            }
        }

        if (playhead >= 0) {
            int x = (int) (playhead * xScale);
            g.setColor(PLAYHEAD);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x, 0, x, h);
        }

        // Legend
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        String[] names = {"Lead", "Harmony", "Chords", "Bass", "Drums"};
        int[] channels = {Arranger.LEAD, Arranger.HARMONY, Arranger.COMP, Arranger.BASS, Arranger.DRUMS};
        int lx = w - 330;
        for (int i = 0; i < names.length; i++) {
            g.setColor(PART_COLORS.get(channels[i]));
            g.fillRect(lx, h - 14, 10, 10);
            g.setColor(TEXT);
            g.drawString(names[i], lx + 14, h - 5);
            lx += 64;
        }
    }
}
