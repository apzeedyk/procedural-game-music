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
        double compilationMinutes,
        File video,
        double fps,
        String ffmpeg,
        boolean chip,
        boolean fluidsynth,
        String fluidsynthPath,
        Integer leadOverride,
        Integer harmonyOverride,
        Integer compOverride,
        Integer bassOverride) {

    /** Applies any --lead/--harmony/--comp/--bass overrides to a generated song. */
    com.jazzerator.form.Song withOverrides(com.jazzerator.form.Song song) {
        if (leadOverride == null && harmonyOverride == null && compOverride == null && bassOverride == null) {
            return song;
        }
        com.jazzerator.preset.Instruments i = song.spec().instruments();
        return song.withInstruments(new com.jazzerator.preset.Instruments(
                leadOverride != null ? leadOverride : i.lead(),
                compOverride != null ? compOverride : i.comp(),
                bassOverride != null ? bassOverride : i.bass(),
                harmonyOverride != null ? harmonyOverride : i.harmony()));
    }

    /** The sound source chosen on the command line. */
    com.jazzerator.midi.AudioRenderer renderer() {
        if (chip) {
            return new com.jazzerator.midi.ChipRenderer();
        }
        if (fluidsynth) {
            return new com.jazzerator.midi.FluidSynthRenderer(fluidsynthPath, soundfont);
        }
        return com.jazzerator.midi.AudioRenderer.soundFont(soundfont);
    }

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
              --synth <name>      Sound source: soundfont (default, JDK synthesizer), fluidsynth (FluidSynth,
                                  needs --soundfont), or chip (built-in 8-bit synthesizer)
              --fluidsynth <path> FluidSynth executable (default: fluidsynth on the PATH)
              --lead <instrument> Override an instrument with a General MIDI name or number, e.g.
              --harmony <...>     --lead "saw lead" or --comp 0. --harmony also adds a harmony line if the
              --comp <...>        song had none ("none" removes it). The composition is unchanged.
              --bass <...>
              --compilation <min> Render consecutive songs into one WAV of at least <min> minutes,
                                  plus a tracklist with YouTube chapter timestamps
              --video <path>      With --compilation: also make an MP4 over a looping background
                                  (a folder of numbered PNG frames, or a single image)
              --fps <n>           Background animation frame rate (default: 12)
              --ffmpeg <path>     ffmpeg executable (default: ffmpeg on the PATH)
              --list              List genres and their pairings
              --help              Show this help
              --gui               Open the desktop app instead (no other options)
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
        File video = null;
        double fps = 12;
        String ffmpeg = "ffmpeg";
        boolean chip = false;
        boolean fluidsynth = false;
        String fluidsynthPath = "fluidsynth";
        Integer leadOverride = null;
        Integer harmonyOverride = null;
        Integer compOverride = null;
        Integer bassOverride = null;
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
                case "--video" -> {
                    video = new File(value(args, ++i, arg));
                    if (!video.exists()) {
                        throw new UsageException("Video background not found: " + video);
                    }
                }
                case "--fps" -> {
                    String v = value(args, ++i, arg);
                    try {
                        fps = Double.parseDouble(v);
                    } catch (NumberFormatException e) {
                        throw new UsageException("--fps expects a number, got '" + v + "'.");
                    }
                    if (fps <= 0) {
                        throw new UsageException("--fps must be positive.");
                    }
                }
                case "--ffmpeg" -> ffmpeg = value(args, ++i, arg);
                case "--fluidsynth" -> fluidsynthPath = value(args, ++i, arg);
                case "--lead" -> leadOverride = instrument(arg, value(args, ++i, arg));
                case "--harmony" -> {
                    String v = value(args, ++i, arg);
                    harmonyOverride = v.equalsIgnoreCase("none") ? com.jazzerator.preset.Instruments.NONE
                            : instrument(arg, v);
                }
                case "--comp" -> compOverride = instrument(arg, value(args, ++i, arg));
                case "--bass" -> bassOverride = instrument(arg, value(args, ++i, arg));
                case "--synth" -> {
                    String synth = value(args, ++i, arg);
                    switch (synth) {
                        case "chip" -> chip = true;
                        case "fluidsynth" -> fluidsynth = true;
                        case "soundfont" -> {
                            chip = false;
                            fluidsynth = false;
                        }
                        default -> throw new UsageException(
                                "Unknown synth '" + synth + "'. Options: soundfont, fluidsynth, chip");
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
        if (fluidsynth && soundfont == null) {
            throw new UsageException("--synth fluidsynth needs --soundfont <file.sf2>.");
        }
        if (video != null && compilationMinutes <= 0) {
            throw new UsageException("--video needs --compilation <minutes>.");
        }
        return new Cli(seed, genre, structure, count, play, out, help, list, soundfont, wav, compilationMinutes,
                video, fps, ffmpeg, chip, fluidsynth, fluidsynthPath, leadOverride, harmonyOverride, compOverride,
                bassOverride);
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

    private static int instrument(String flag, String text) {
        return com.jazzerator.preset.Instruments.byName(text).orElseThrow(() -> new UsageException(
                flag + ": no single General MIDI instrument matches '" + text + "'."));
    }

    private static long parseLong(String flag, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new UsageException(flag + " expects a number, got '" + value + "'.");
        }
    }
}
