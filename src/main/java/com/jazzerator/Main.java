package com.jazzerator;

import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import com.jazzerator.midi.MidiPlayer;
import com.jazzerator.midi.MidiWriter;
import com.jazzerator.midi.WavRenderer;
import com.jazzerator.preset.GenrePreset;
import com.jazzerator.preset.Pairing;
import com.jazzerator.preset.Presets;
import java.io.File;
import java.util.List;
import javax.sound.midi.Sequence;

public final class Main {
    public static void main(String[] args) throws Exception {
        Cli cli;
        try {
            cli = Cli.parse(args);
        } catch (Cli.UsageException e) {
            System.err.println("Error: " + e.getMessage() + "\n");
            System.err.print(Cli.USAGE);
            System.exit(2);
            return;
        }
        if (cli.help()) {
            System.out.print(Cli.USAGE);
            return;
        }
        if (cli.list()) {
            printGenres();
            return;
        }

        if (cli.compilationMinutes() > 0) {
            writeCompilation(cli);
            return;
        }

        for (int n = 0; n < cli.count(); n++) {
            long seed = cli.seed() + n;
            Song song = SongGenerator.generate(seed, cli.genre(), cli.structure());
            System.out.println(song.spec().summary());
            System.out.println(song.chart());

            Sequence sequence = Arranger.render(song);
            File file = new File(cli.outputFor(n, seed, song.spec().genre().id()));
            MidiWriter.save(sequence, file);
            System.out.println("Wrote " + file.getAbsolutePath());
            if (cli.wav()) {
                File wav = new File(file.getPath().replaceFirst("\\.mid$", "") + ".wav");
                WavRenderer.write(sequence, song.spec().tempo(), cli.soundfont(), wav);
                System.out.println("Wrote " + wav.getAbsolutePath());
            }
            System.out.println();
            if (cli.play()) {
                MidiPlayer.play(sequence, cli.soundfont());
            }
        }
    }

    private static void writeCompilation(Cli cli) throws Exception {
        String genreId = cli.genre() != null ? cli.genre().id() : "mixed";
        String base = cli.out() != null ? cli.out().replaceFirst("\\.(wav|mid)$", "")
                : "jazzerator-compilation-" + genreId + "-" + cli.seed();
        File wav = new File(base + ".wav");
        File tracklist = new File(base + "-tracklist.txt");
        List<Compilation.Entry> entries =
                Compilation.plan(cli.seed(), cli.genre(), cli.structure(), cli.compilationMinutes());
        Compilation.Entry last = entries.get(entries.size() - 1);
        int total = (int) Math.round(last.startSeconds() + last.seconds());
        System.out.printf("Compilation: %d songs, %d:%02d:%02d%n", entries.size(), total / 3600, total % 3600 / 60,
                total % 60);
        Compilation.write(entries, cli.soundfont(), wav, tracklist);
        System.out.println("Wrote " + wav.getAbsolutePath());
        System.out.println("Wrote " + tracklist.getAbsolutePath());
        if (cli.video() != null) {
            File mp4 = new File(base + ".mp4");
            System.out.println("Encoding video...");
            VideoMaker.make(entries, cli.video(), cli.fps(), wav, mp4, cli.ffmpeg());
            System.out.println("Wrote " + mp4.getAbsolutePath());
        }
    }

    private static void printGenres() {
        for (GenrePreset g : Presets.ALL) {
            System.out.printf("%-10s %s%n", g.id(), g.name());
            for (Pairing p : g.pairings()) {
                System.out.printf("           - %-22s %d-%d BPM, %s, %s groove%n", p.name(), p.tempoMin(), p.tempoMax(),
                        String.join("/", p.modes().items().stream().map(m -> m.displayName()).toList()),
                        p.groove().displayName());
            }
        }
    }
}
