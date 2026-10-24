package com.jazzerator.theory;

import java.util.Set;

/** A tonic plus a mode, e.g. D Dorian. */
public record Key(int tonic, Mode mode) {
    private static final Set<Integer> FLAT_MAJOR_KEYS = Set.of(5, 10, 3, 8, 1, 6); // F Bb Eb Ab Db Gb

    public Key {
        tonic = Note.pc(tonic);
    }

    public static Key of(String tonicName, Mode mode) {
        return new Key(Note.parse(tonicName), mode);
    }

    public Scale scale() {
        return new Scale(tonic, mode);
    }

    /** Whether to spell notes with flats, based on the parent major key's signature. */
    public boolean prefersFlats() {
        int parentMajor = Note.pc(tonic - mode.offsetFromIonian());
        return FLAT_MAJOR_KEYS.contains(parentMajor);
    }

    /** Parses a roman numeral (e.g. "vi", "bVII", "V7/V") into a chord in this key. */
    public Chord chord(String roman) {
        return RomanNumeral.parse(roman, this);
    }

    /** Chord name spelled for this key; chromatic roots (bVI, bVII, tritone subs) are spelled with flats. */
    public String spell(Chord chord) {
        return chord.name(scale().contains(chord.root()) ? prefersFlats() : true);
    }

    public String name() {
        return Note.name(tonic, prefersFlats()) + " " + mode.displayName();
    }

    @Override
    public String toString() {
        return name();
    }
}
