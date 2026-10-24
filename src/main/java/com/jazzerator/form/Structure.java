package com.jazzerator.form;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Song forms. Each label is a section; repeated labels reuse the same material. */
public enum Structure {
    AABA("AABA", "A", "A", "B", "A"),
    ABAB("ABAB", "A", "B", "A", "B"),
    ABBA("ABBA", "A", "B", "B", "A"),
    VERSE_CHORUS("Verse-Chorus-Verse-Chorus", "Verse", "Chorus", "Verse", "Chorus"),
    VERSE_CHORUS_BRIDGE("Verse-Chorus-Verse-Bridge-Chorus", "Verse", "Chorus", "Verse", "Bridge", "Chorus"),
    RONDO("Rondo (ABACA)", "A", "B", "A", "C", "A"),
    INTRO_LOOP_OUTRO("Intro-Loop-Outro", "Intro", "A", "B", "A", "B", "Outro");

    private final String displayName;
    private final List<String> labels;

    Structure(String displayName, String... labels) {
        this.displayName = displayName;
        this.labels = List.of(labels);
    }

    public String displayName() {
        return displayName;
    }

    /** Command-line id, e.g. "verse-chorus-bridge". */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<Structure> byId(String id) {
        String wanted = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.id().equals(wanted)).findFirst();
    }

    public List<String> labels() {
        return labels;
    }

    /** Intros and outros are half-length; every other section uses the full section length. */
    public static int barsFor(String label, int sectionBars) {
        return label.equals("Intro") || label.equals("Outro") ? Math.max(2, sectionBars / 2) : sectionBars;
    }

    public int totalBars(int sectionBars) {
        return labels.stream().mapToInt(l -> barsFor(l, sectionBars)).sum();
    }
}
