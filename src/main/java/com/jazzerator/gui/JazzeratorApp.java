package com.jazzerator.gui;

import com.jazzerator.SongGenerator;
import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import com.jazzerator.form.Structure;
import com.jazzerator.midi.AudioRenderer;
import com.jazzerator.midi.ChipRenderer;
import com.jazzerator.midi.FluidSynthRenderer;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.midi.NormalizingRenderer;
import com.jazzerator.preset.GenrePreset;
import com.jazzerator.preset.Instruments;
import com.jazzerator.preset.Pairing;
import com.jazzerator.preset.Presets;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
import java.util.prefs.Preferences;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * A desktop front end for the generator: pick a seed, genre, pairing, form, instruments and sound source,
 * generate, see the song as a piano roll and chord chart, play it, and export MIDI or WAV. Uses exactly the same
 * pipeline as the command line (auto-mix, loudness normalization, overrides), so the same settings give the same
 * song.
 */
public final class JazzeratorApp {
    private static final String RANDOM = "Random";
    private static final String AUTO = "Auto (pairing's choice)";
    private static final String NONE = "None";
    private static final String SOUND_JAVA = "SoundFont (Java synth)";
    private static final String SOUND_FLUID = "SoundFont (FluidSynth)";
    private static final String SOUND_CHIP = "Chip (8-bit)";

    private final Preferences prefs = Preferences.userNodeForPackage(JazzeratorApp.class);
    private final JFrame frame = new JFrame("Jazzerator");
    private final JTextField seedField = new JTextField(10);
    private final JComboBox<Object> genreBox = new JComboBox<>();
    private final JComboBox<Object> pairingBox = new JComboBox<>();
    private final JComboBox<Object> structureBox = new JComboBox<>();
    private final JComboBox<String> leadBox = instrumentBox(false);
    private final JComboBox<String> harmonyBox = instrumentBox(true);
    private final JComboBox<String> compBox = instrumentBox(false);
    private final JComboBox<String> bassBox = instrumentBox(false);
    private final JComboBox<String> soundBox = new JComboBox<>(new String[]{SOUND_FLUID, SOUND_JAVA, SOUND_CHIP});
    private final JTextField soundfontField = new JTextField(18);
    private final JTextField fluidsynthField = new JTextField(18);
    private final JButton generateButton = new JButton("Generate");
    private final JButton newSongButton = new JButton("New random song");
    private final JButton playButton = new JButton("▶ Play");
    private final JButton stopButton = new JButton("■ Stop");
    private final JButton midiButton = new JButton("Export MIDI…");
    private final JButton wavButton = new JButton("Export WAV…");
    private final JLabel status = new JLabel("Ready.");
    private final JTextArea chart = new JTextArea();
    private final PianoRollPanel roll = new PianoRollPanel();
    private final AudioPlayback playback = new AudioPlayback();

    /** What's currently generated, and the settings it was made with. */
    private Song song;
    private Sequence sequence;
    private AudioRenderer renderer;
    private boolean dirty = true;

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // the default look and feel is fine
            }
            new JazzeratorApp().show();
        });
    }

    private void show() {
        buildControls();
        loadPrefs();
        chart.setEditable(false);
        chart.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roll, new JScrollPane(chart));
        right.setResizeWeight(0.7);
        frame.setLayout(new BorderLayout());
        frame.add(controlPanel(), BorderLayout.WEST);
        frame.add(right, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                savePrefs();
                playback.stop();
            }
        });
        frame.setSize(1300, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        new Timer(33, e -> followPlayhead()).start();
    }

    // ---- controls ----------------------------------------------------------------------------------------------

    private void buildControls() {
        seedField.setText(String.valueOf(ThreadLocalRandom.current().nextInt(1, 1_000_000)));
        genreBox.addItem(RANDOM);
        Presets.ALL.forEach(genreBox::addItem);
        genreBox.setRenderer(nameRenderer());
        pairingBox.setRenderer(nameRenderer());
        structureBox.addItem(RANDOM);
        for (Structure s : Structure.values()) {
            structureBox.addItem(s);
        }
        structureBox.setRenderer(nameRenderer());
        refreshPairings();

        genreBox.addActionListener(e -> {
            refreshPairings();
            markDirty();
        });
        for (JComboBox<?> box : java.util.List.of(pairingBox, structureBox, leadBox, harmonyBox, compBox, bassBox,
                soundBox)) {
            box.addActionListener(e -> markDirty());
        }
        seedField.addActionListener(e -> generate(false));
        generateButton.addActionListener(e -> generate(false));
        newSongButton.addActionListener(e -> {
            seedField.setText(String.valueOf(ThreadLocalRandom.current().nextInt(1, 1_000_000)));
            generate(false);
        });
        playButton.addActionListener(e -> {
            if (dirty || song == null) {
                generate(true);
            } else {
                play();
            }
        });
        stopButton.addActionListener(e -> {
            playback.stop();
            roll.setPlayhead(-1);
        });
        midiButton.addActionListener(e -> exportMidi());
        wavButton.addActionListener(e -> exportWav());
    }

    private JPanel controlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int row = 0;
        JButton dice = new JButton("🎲");
        dice.setToolTipText("Random seed");
        dice.addActionListener(e -> {
            seedField.setText(String.valueOf(ThreadLocalRandom.current().nextInt(1, 1_000_000)));
            markDirty();
        });
        JPanel seedRow = new JPanel(new BorderLayout(4, 0));
        seedRow.add(seedField, BorderLayout.CENTER);
        seedRow.add(dice, BorderLayout.EAST);
        row = addRow(p, row, "Seed", seedRow);
        row = addRow(p, row, "Genre", genreBox);
        row = addRow(p, row, "Pairing", pairingBox);
        row = addRow(p, row, "Structure", structureBox);
        row = addSeparator(p, row, "Instruments");
        row = addRow(p, row, "Lead", leadBox);
        row = addRow(p, row, "Harmony", harmonyBox);
        row = addRow(p, row, "Chords", compBox);
        row = addRow(p, row, "Bass", bassBox);
        row = addSeparator(p, row, "Sound");
        row = addRow(p, row, "Engine", soundBox);
        row = addRow(p, row, "SoundFont", withBrowse(soundfontField, "SoundFont (.sf2)", "sf2", "sf3"));
        row = addRow(p, row, "FluidSynth", withBrowse(fluidsynthField, "fluidsynth.exe", "exe"));
        row = addSeparator(p, row, "");
        JPanel buttons = new JPanel(new GridBagLayout());
        GridBagConstraints b = new GridBagConstraints();
        b.fill = GridBagConstraints.HORIZONTAL;
        b.insets = new Insets(2, 2, 2, 2);
        b.weightx = 1;
        b.gridx = 0;
        b.gridy = 0;
        b.gridwidth = 2;
        buttons.add(generateButton, b);
        b.gridy = 1;
        buttons.add(newSongButton, b);
        b.gridwidth = 1;
        b.gridy = 2;
        buttons.add(playButton, b);
        b.gridx = 1;
        buttons.add(stopButton, b);
        b.gridx = 0;
        b.gridy = 3;
        buttons.add(midiButton, b);
        b.gridx = 1;
        buttons.add(wavButton, b);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(buttons, c);
        c.gridy = row + 1;
        c.weighty = 1;
        p.add(new JPanel(), c); // push everything to the top
        return p;
    }

    private static int addRow(JPanel p, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = row;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        p.add(field, c);
        return row + 1;
    }

    private static int addSeparator(JPanel p, int row, String title) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 3, 2, 3);
        JPanel sep = new JPanel(new BorderLayout(6, 0));
        if (!title.isEmpty()) {
            JLabel l = new JLabel(title);
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            sep.add(l, BorderLayout.WEST);
        }
        sep.add(new JSeparator(), BorderLayout.CENTER);
        p.add(sep, c);
        return row + 1;
    }

    private JPanel withBrowse(JTextField field, String description, String... extensions) {
        JButton browse = new JButton("…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(field.getText().isBlank() ? "." : field.getText());
            chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
                markDirty();
            }
        });
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(field, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private static JComboBox<String> instrumentBox(boolean allowNone) {
        JComboBox<String> box = new JComboBox<>();
        box.addItem(AUTO);
        if (allowNone) {
            box.addItem(NONE);
        }
        for (int p = 0; p < 128; p++) {
            box.addItem(p + " " + Instruments.name(p));
        }
        box.setMaximumRowCount(20);
        return box;
    }

    private void refreshPairings() {
        pairingBox.removeAllItems();
        pairingBox.addItem(RANDOM);
        if (genreBox.getSelectedItem() instanceof GenrePreset g) {
            g.pairings().forEach(pairingBox::addItem);
        }
    }

    private static javax.swing.ListCellRenderer<Object> nameRenderer() {
        return new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focus) {
                Object shown = value instanceof GenrePreset g ? g.name()
                        : value instanceof Pairing p ? p.name()
                        : value instanceof Structure s ? s.displayName()
                        : value;
                return super.getListCellRendererComponent(list, shown, index, selected, focus);
            }
        };
    }

    private void markDirty() {
        dirty = true;
    }

    // ---- generate, play, export ----------------------------------------------------------------------------------

    private void generate(boolean playWhenDone) {
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            error("The seed must be a whole number.");
            return;
        }
        AudioRenderer chosen;
        try {
            chosen = chosenRenderer();
        } catch (IllegalStateException e) {
            error(e.getMessage());
            return;
        }
        GenrePreset genre = genreBox.getSelectedItem() instanceof GenrePreset g ? g : null;
        Pairing pairing = pairingBox.getSelectedItem() instanceof Pairing p ? p : null;
        Structure structure = structureBox.getSelectedItem() instanceof Structure s ? s : null;
        playback.stop();
        busy("Generating seed " + seed + "…");
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                Song s = withOverrides(SongGenerator.generate(seed, genre, structure, pairing));
                Sequence q = Arranger.renderMixed(s, chosen); // the mix is balanced for the chosen sound
                return new Object[]{s, q};
            }

            @Override
            protected void done() {
                try {
                    Object[] result = get();
                    song = (Song) result[0];
                    sequence = (Sequence) result[1];
                    renderer = chosen;
                    dirty = false;
                    roll.show(song, sequence);
                    chart.setText(song.spec().summary() + "\n\n" + song.chart());
                    chart.setCaretPosition(0);
                    idle("Generated " + song.spec().genre().name() + " / " + song.spec().pairing().name()
                            + " (seed " + seed + ").");
                    if (playWhenDone) {
                        play();
                    }
                } catch (Exception e) {
                    failed("Generation failed", e);
                }
            }
        }.execute();
    }

    private void play() {
        Song s = song;
        Sequence q = sequence;
        AudioRenderer r = renderer;
        busy("Rendering audio…");
        new SwingWorker<AudioInputStream, Void>() {
            @Override
            protected AudioInputStream doInBackground() throws Exception {
                return new NormalizingRenderer(r).render(q, s.spec().tempo(), 0);
            }

            @Override
            protected void done() {
                try {
                    playback.play(get());
                    idle("Playing " + s.spec().pairing().name() + "…");
                } catch (Exception e) {
                    failed("Playback failed", e);
                }
            }
        }.execute();
    }

    private void exportMidi() {
        if (song == null) {
            error("Generate a song first.");
            return;
        }
        File file = chooseSave("jazzerator-" + song.spec().genre().id() + "-" + song.spec().seed() + ".mid",
                "MIDI file", "mid");
        if (file == null) {
            return;
        }
        try {
            MidiWriter.save(sequence, file);
            idle("Saved " + file.getAbsolutePath());
        } catch (Exception e) {
            failed("Export failed", e);
        }
    }

    private void exportWav() {
        if (song == null) {
            error("Generate a song first.");
            return;
        }
        File file = chooseSave("jazzerator-" + song.spec().genre().id() + "-" + song.spec().seed() + ".wav",
                "WAV audio", "wav");
        if (file == null) {
            return;
        }
        Song s = song;
        Sequence q = sequence;
        AudioRenderer r = renderer;
        busy("Rendering WAV…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (AudioInputStream audio = new NormalizingRenderer(r).render(q, s.spec().tempo(), 0)) {
                    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, file);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    idle("Saved " + file.getAbsolutePath());
                } catch (Exception e) {
                    failed("Export failed", e);
                }
            }
        }.execute();
    }

    private File chooseSave(String defaultName, String description, String extension) {
        JFileChooser chooser = new JFileChooser(prefs.get("exportDir", "."));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + extension)) {
            file = new File(file.getPath() + "." + extension);
        }
        prefs.put("exportDir", file.getParent());
        return file;
    }

    // ---- settings ------------------------------------------------------------------------------------------------

    private AudioRenderer chosenRenderer() {
        String sound = (String) soundBox.getSelectedItem();
        File sf = soundfontField.getText().isBlank() ? null : new File(soundfontField.getText().trim());
        if (SOUND_CHIP.equals(sound)) {
            return new ChipRenderer();
        }
        if (sf != null && !sf.isFile()) {
            throw new IllegalStateException("SoundFont not found: " + sf);
        }
        if (SOUND_FLUID.equals(sound)) {
            if (sf == null) {
                throw new IllegalStateException("FluidSynth needs a SoundFont: choose a .sf2 file.");
            }
            String exe = fluidsynthField.getText().isBlank() ? "fluidsynth" : fluidsynthField.getText().trim();
            return new FluidSynthRenderer(exe, sf);
        }
        return AudioRenderer.soundFont(sf);
    }

    private Song withOverrides(Song s) {
        Instruments i = s.spec().instruments();
        Integer lead = program(leadBox);
        Integer harmony = harmonyBox.getSelectedItem() == NONE ? Integer.valueOf(Instruments.NONE) : program(harmonyBox);
        Integer comp = program(compBox);
        Integer bass = program(bassBox);
        if (lead == null && harmony == null && comp == null && bass == null) {
            return s;
        }
        return s.withInstruments(new Instruments(
                lead != null ? lead : i.lead(),
                comp != null ? comp : i.comp(),
                bass != null ? bass : i.bass(),
                harmony != null ? harmony : i.harmony()));
    }

    /** The program number chosen in an instrument box, or null for "Auto". */
    private static Integer program(JComboBox<String> box) {
        String item = (String) box.getSelectedItem();
        if (item == null || item.equals(AUTO) || item.equals(NONE)) {
            return null;
        }
        return Integer.parseInt(item.substring(0, item.indexOf(' ')));
    }

    private void loadPrefs() {
        File defaultFont = new File("soundfonts/MuseScore_General.sf2");
        soundfontField.setText(prefs.get("soundfont", defaultFont.isFile() ? defaultFont.getAbsolutePath() : ""));
        fluidsynthField.setText(prefs.get("fluidsynth", detectFluidSynth()));
        soundBox.setSelectedItem(prefs.get("sound", fluidsynthField.getText().isBlank() ? SOUND_JAVA : SOUND_FLUID));
    }

    /** Looks for fluidsynth.exe under a tools/fluidsynth folder beside the project; empty if not found. */
    private static String detectFluidSynth() {
        File tools = new File("../tools/fluidsynth");
        try (var files = java.nio.file.Files.find(tools.toPath(), 4,
                (path, attrs) -> path.getFileName().toString().equalsIgnoreCase("fluidsynth.exe"))) {
            return files.findFirst().map(p -> p.toAbsolutePath().normalize().toString()).orElse("");
        } catch (java.io.IOException | java.io.UncheckedIOException e) {
            return "";
        }
    }

    private void savePrefs() {
        prefs.put("soundfont", soundfontField.getText());
        prefs.put("fluidsynth", fluidsynthField.getText());
        prefs.put("sound", (String) soundBox.getSelectedItem());
    }

    // ---- status --------------------------------------------------------------------------------------------------

    private void followPlayhead() {
        double seconds = playback.positionSeconds();
        if (song == null || seconds < 0) {
            return;
        }
        long tick = Math.round(seconds * song.spec().tempo() * MidiWriter.PPQ / 60.0);
        roll.setPlayhead(playback.isPlaying() ? tick : -1);
    }

    private void busy(String message) {
        status.setText(message);
        for (JButton b : new JButton[]{generateButton, newSongButton, playButton, midiButton, wavButton}) {
            b.setEnabled(false);
        }
    }

    private void idle(String message) {
        status.setText(message);
        for (JButton b : new JButton[]{generateButton, newSongButton, playButton, midiButton, wavButton}) {
            b.setEnabled(true);
        }
    }

    private void failed(String what, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        idle(what + ": " + cause.getMessage());
        JOptionPane.showMessageDialog(frame, what + ":\n" + cause.getMessage(), "Jazzerator",
                JOptionPane.ERROR_MESSAGE);
    }

    private void error(String message) {
        status.setText(message);
        JOptionPane.showMessageDialog(frame, message, "Jazzerator", JOptionPane.WARNING_MESSAGE);
    }
}
