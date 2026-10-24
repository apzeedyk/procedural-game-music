package com.jazzerator;

import com.jazzerator.arrange.Arranger;
import com.jazzerator.form.Song;
import com.jazzerator.form.Structure;
import com.jazzerator.midi.WavRenderer;
import com.jazzerator.preset.GenrePreset;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * A long mix of consecutive-seed songs rendered into a single WAV, plus a tracklist whose timestamps work as
 * YouTube chapters. Songs are rendered one at a time while the file is written, so memory stays flat.
 */
public final class Compilation {
    public static final double GAP_SECONDS = 2.0;

    public record Entry(Song song, Sequence sequence, double startSeconds, double seconds) {
    }

    private Compilation() {
    }

    /** Songs with consecutive seeds until the running length reaches the target. */
    public static List<Entry> plan(long firstSeed, GenrePreset genre, Structure structure, double minutes) {
        List<Entry> entries = new ArrayList<>();
        double t = 0;
        for (long seed = firstSeed; t < minutes * 60; seed++) {
            Song song = SongGenerator.generate(seed, genre, structure);
            Sequence sequence = Arranger.render(song);
            double seconds = WavRenderer.seconds(sequence, song.spec().tempo(), GAP_SECONDS);
            entries.add(new Entry(song, sequence, t, seconds));
            t += seconds;
        }
        return entries;
    }

    public static void write(List<Entry> entries, File soundfont, File wav, File tracklist) throws IOException {
        Files.writeString(tracklist.toPath(), tracklist(entries));

        long totalFrames = 0;
        for (Entry e : entries) {
            totalFrames += Math.round(WavRenderer.FORMAT.getFrameRate() * e.seconds());
        }
        Iterator<Entry> it = entries.iterator();
        Enumeration<InputStream> streams = new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return it.hasNext();
            }

            @Override
            public InputStream nextElement() {
                Entry e = it.next();
                System.out.printf("  rendering %d:%02d  %s%n", (int) e.startSeconds() / 60,
                        (int) e.startSeconds() % 60, title(e.song()));
                try {
                    return WavRenderer.render(e.sequence(), e.song().spec().tempo(), soundfont, GAP_SECONDS);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        };
        try (AudioInputStream mix = new AudioInputStream(new SequenceInputStream(streams), WavRenderer.FORMAT,
                totalFrames)) {
            AudioSystem.write(mix, AudioFileFormat.Type.WAVE, wav);
        }
    }

    /** One line per song, "m:ss Title" (or "h:mm:ss" past an hour). */
    public static String tracklist(List<Entry> entries) {
        boolean hours = !entries.isEmpty() && entries.get(entries.size() - 1).startSeconds() >= 3600;
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(timestamp(e.startSeconds(), hours)).append(' ').append(title(e.song())).append('\n');
        }
        return sb.toString();
    }

    static String title(Song song) {
        var spec = song.spec();
        return spec.genre().name() + " - " + spec.pairing().name() + " in " + spec.key().name() + ", "
                + spec.tempo() + " BPM (seed " + spec.seed() + ")";
    }

    static String timestamp(double seconds, boolean hours) {
        int s = (int) Math.floor(seconds);
        return hours
                ? String.format("%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
