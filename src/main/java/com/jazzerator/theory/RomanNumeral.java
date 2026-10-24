package com.jazzerator.theory;

import com.jazzerator.theory.Chord.Quality;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses roman-numeral chord symbols. Roots are measured from the tonic's MAJOR scale, so accidentals are
 * explicit: in any key, "bVII" is a whole step below the tonic. Case sets the default triad (upper = major,
 * lower = minor). Supported suffixes: 7, maj7, 9, maj9, add9, sus2, sus4, ° / o / dim, °7 / o7 / dim7,
 * ø / ø7 / h7, + / aug. Secondary chords use a slash: "V7/V", "viio7/ii".
 */
final class RomanNumeral {
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII"};
    private static final Pattern PATTERN =
            Pattern.compile("^([b#]?)(VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii|i)([^/]*)(?:/(.+))?$");

    private RomanNumeral() {
    }

    static Chord parse(String symbol, Key key) {
        Matcher m = PATTERN.matcher(symbol.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad roman numeral: " + symbol);
        }
        int tonic = key.tonic();
        if (m.group(4) != null) {
            // Secondary chord: interpret the numeral relative to the target chord's root, as a major key.
            tonic = parse(m.group(4), key).root();
        }

        String numeral = m.group(2);
        int degree = indexOf(numeral.toUpperCase());
        int accidental = switch (m.group(1)) {
            case "b" -> -1;
            case "#" -> 1;
            default -> 0;
        };
        int root = tonic + MAJOR[degree] + accidental;
        boolean upper = Character.isUpperCase(numeral.charAt(0));

        String suffix = m.group(3);
        return switch (suffix) {
            case "" -> new Chord(root, upper ? Quality.MAJ : Quality.MIN);
            case "7" -> new Chord(root, upper ? Quality.DOM7 : Quality.MIN7);
            case "maj7" -> new Chord(root, Quality.MAJ7);
            case "9" -> new Chord(root, upper ? Quality.DOM7 : Quality.MIN7, true, 0);
            case "maj9" -> new Chord(root, Quality.MAJ7, true, 0);
            case "add9" -> new Chord(root, upper ? Quality.MAJ : Quality.MIN, true, 0);
            case "sus2" -> new Chord(root, Quality.SUS2);
            case "sus4" -> new Chord(root, Quality.SUS4);
            case "°", "o", "dim" -> new Chord(root, Quality.DIM);
            case "°7", "o7", "dim7" -> new Chord(root, Quality.DIM7);
            case "ø", "ø7", "h", "h7" -> new Chord(root, Quality.HALF_DIM7);
            case "+", "aug" -> new Chord(root, Quality.AUG);
            default -> throw new IllegalArgumentException("Bad chord suffix '" + suffix + "' in " + symbol);
        };
    }

    private static int indexOf(String upperNumeral) {
        for (int i = 0; i < NUMERALS.length; i++) {
            if (NUMERALS[i].equals(upperNumeral)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Bad numeral: " + upperNumeral);
    }
}
