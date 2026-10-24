package com.jazzerator;

import com.jazzerator.form.Structure;
import com.jazzerator.preset.GenrePreset;
import com.jazzerator.preset.Presets;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Command-line options and parsing. */
record Cli(
        long seed,
        GenrePreset genre,
        Structure structure,
        int count,
        boolean play,
        String out,
        boolean help,
        boolean list,
        File soundfont,
        boolean wav,
        double compilationMinutes) {

    static final String USAGE = """
            Usage: java -jar jazzerator.jar [options]

            Generates random video game music as MIDI files.

            Options:
              --seed <n>          Seed for a reproducible song (default: random)
              --genre <id>        Force a genre: %s
              --structure <id>    Force a form: %s
              --count <n>         Generate n songs with consecutive seeds (default: 1)
              --out <file.mid>    Output file (default: jazzerator-<genre>-<seed>.mid)
              --play              Play each song through the synthesizer
              --wav               Also render each song to a .wav file
              --soundfont <file>  Use a SoundFont (.sf2) for playback and rendering
              --compilation <min> Render consecutive songs into one WAV of at least <min> minutes,
                                  plus a tracklist with YouTube chapter timestamps
              --list              List genres and their pairings
              --help              Show this help
            """.formatted(
            Presets.ALL.stream().map(GenrePreset::id).collect(Collectors.joining(", ")),
            Arrays.stream(Structure.values()).map(Structure::id).collect(Collectors.joining(", ")));

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    static Cli parse(String... args) {
        long seed = ThreadLocalRandom.current().nextInt(1, 1_000_000);
        GenrePreset genre = null;
        Structure structure = null;
        int count = 1;
        boolean play = false;
        String out = null;
        boolean help = false;
        boolean list = false;
        File soundfont = null;
        boolean wav = false;
        double compilationMinutes = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--seed" -> seed = parseLong(arg, value(args, ++i, arg));
                case "--genre" -> {
                    String id = value(args, ++i, arg);
                    genre = Presets.byId(id).orElseThrow(() -> new UsageException("Unknown genre '" + id + "'."));
                }
                case "--structure" -> {
                    String id = value(args, ++i, arg);
                    structure = Structure.byId(id)
                            .orElseThrow(() -> new UsageException("Unknown structure '" + id + "'."));
                }
                case "--count" -> {
                    count = (int) parseLong(arg, value(args, ++i, arg));
                    if (count < 1) {
                        throw new UsageException("--count must be at least 1.");
                    }
                }
                case "--out" -> out = value(args, ++i, arg);
                case "--play" -> play = true;
                case "--list" -> list = true;
                case "--wav" -> wav = true;
                case "--soundfont" -> {
                    soundfont = new File(value(args, ++i, arg));
                    if (!soundfont.isFile()) {
                        throw new UsageException("SoundFont not found: " + soundfont);
                    }
                }
                case "--compilation" -> {
                    String v = value(args, ++i, arg);
                    try {
                        compilationMinutes = Double.parseDouble(v);
                    } catch (NumberFormatException e) {
                        throw new UsageException("--compilation expects minutes, got '" + v + "'.");
                    }
                    if (compilationMinutes <= 0) {
                        throw new UsageException("--compilation must be more than 0 minutes.");
                    }
                }
                case "--help", "-h" -> help = true;
                default -> throw new UsageException("Unknown option '" + arg + "'.");
            }
        }
        return new Cli(seed, genre, structure, count, play, out, help, list, soundfont, wav, compilationMinutes);
    }

    /** Output path for the n-th song (0-based) of a batch. */
    String outputFor(int n, long songSeed, String genreId) {
        if (out == null) {
            return "jazzerator-" + genreId + "-" + songSeed + ".mid";
        }
        if (count == 1) {
            return out;
        }
        String base = out.endsWith(".mid") ? out.substring(0, out.length() - 4) : out;
        return base + "-" + (n + 1) + ".mid";
    }

    private static String value(String[] args, int i, String flag) {
        if (i >= args.length || args[i].startsWith("--")) {
            throw new UsageException(flag + " needs a value.");
        }
        return args[i];
    }

    private static long parseLong(String flag, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new UsageException(flag + " expects a number, got '" + value + "'.");
        }
    }
}
