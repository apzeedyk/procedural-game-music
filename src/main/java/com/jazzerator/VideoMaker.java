package com.jazzerator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a compilation WAV into a YouTube-ready MP4: a looping pixel-art animation (a folder of numbered PNG
 * frames, or a single image), scaled with nearest-neighbor so pixels stay crisp, plus burned-in "now playing"
 * captions that change at each song. Requires ffmpeg on the PATH (or given explicitly).
 */
public final class VideoMaker {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private VideoMaker() {
    }

    public static void make(List<Compilation.Entry> entries, File background, double fps, File wav, File mp4,
                            String ffmpeg) throws IOException, InterruptedException {
        File dir = mp4.getAbsoluteFile().getParentFile();
        String base = mp4.getName().replaceFirst("\\.mp4$", "");
        File subtitles = new File(dir, base + ".ass");
        Files.writeString(subtitles.toPath(), captions(entries), StandardCharsets.UTF_8);

        // Few threads keep memory low: a 1080p encode with default threading can exceed what a busy 8 GB PC has free.
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-stats",
                "-filter_threads", "1"));
        if (background.isDirectory()) {
            String pattern = framePattern(background);
            cmd.addAll(List.of("-stream_loop", "-1", "-framerate", fmt(fps), "-i",
                    new File(background, pattern).getAbsolutePath()));
        } else {
            cmd.addAll(List.of("-loop", "1", "-framerate", fmt(fps), "-i", background.getAbsolutePath()));
        }
        cmd.addAll(List.of(
                "-i", wav.getAbsolutePath(),
                // Subtitles are referenced relative to the working directory: Windows drive colons break the filter.
                "-vf", "scale=" + WIDTH + ":" + HEIGHT + ":force_original_aspect_ratio=decrease:flags=neighbor,"
                        + "pad=" + WIDTH + ":" + HEIGHT + ":(ow-iw)/2:(oh-ih)/2,"
                        + "subtitles=" + subtitles.getName(),
                "-map", "0:v", "-map", "1:a",
                "-c:v", "libx264", "-preset", "veryfast", "-tune", "animation", "-crf", "20", "-pix_fmt", "yuv420p",
                "-threads", "2",
                "-c:a", "aac", "-b:a", "192k",
                // Exact length: -shortest alone overshoots with an endlessly looping video input.
                "-t", String.format(Locale.ROOT, "%.3f", totalSeconds(entries)), "-shortest",
                "-movflags", "+faststart",
                mp4.getAbsolutePath()));

        Process p = new ProcessBuilder(cmd).directory(dir).inheritIO().start();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("ffmpeg failed with exit code " + exit);
        }
    }

    /** ffmpeg image2 pattern for a folder of numbered frames, e.g. "frame%04d.png". */
    static String framePattern(File folder) throws IOException {
        String[] names = folder.list((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (names == null || names.length == 0) {
            throw new IOException("No PNG frames in " + folder);
        }
        java.util.Arrays.sort(names);
        String first = names[0];
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)(\\d+)(\\.png)$").matcher(first);
        if (!m.matches()) {
            throw new IOException("Frames must be numbered like frame0000.png, found " + first);
        }
        return m.group(1).replace("%", "%%") + "%0" + m.group(2).length() + "d" + m.group(3);
    }

    /** An ASS subtitle script with one "now playing" caption per song. */
    static String captions(List<Compilation.Entry> entries) {
        StringBuilder sb = new StringBuilder("""
                [Script Info]
                ScriptType: v4.00+
                PlayResX: %d
                PlayResY: %d
                WrapStyle: 2

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, \
                Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, \
                MarginL, MarginR, MarginV, Encoding
                Style: Now,Consolas,40,&H00FFFFFF,&H00FFFFFF,&H00201810,&H80000000,1,0,0,0,100,100,0,0,1,3,2,1,48,48,64,1
                Style: Meta,Consolas,26,&H00E8E0D8,&H00FFFFFF,&H00201810,&H80000000,0,0,0,0,100,100,0,0,1,2,1,1,48,48,30,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """.formatted(WIDTH, HEIGHT));
        for (Compilation.Entry e : entries) {
            var spec = e.song().spec();
            String start = assTime(e.startSeconds());
            String end = assTime(e.startSeconds() + e.seconds());
            String title = spec.genre().name() + " - " + spec.pairing().name();
            String meta = spec.key().name() + " · " + spec.tempo() + " BPM · seed " + spec.seed()
                    + " · procedurally generated, not AI";
            sb.append("Dialogue: 0,").append(start).append(',').append(end).append(",Now,,0,0,0,,{\\fad(600,600)}")
                    .append(escape(title)).append('\n');
            sb.append("Dialogue: 0,").append(start).append(',').append(end).append(",Meta,,0,0,0,,{\\fad(600,600)}")
                    .append(escape(meta)).append('\n');
        }
        return sb.toString();
    }

    private static double totalSeconds(List<Compilation.Entry> entries) {
        Compilation.Entry last = entries.get(entries.size() - 1);
        return last.startSeconds() + last.seconds();
    }

    static String assTime(double seconds) {
        long cs = Math.round(seconds * 100);
        return String.format("%d:%02d:%02d.%02d", cs / 360000, cs / 6000 % 60, cs / 100 % 60, cs % 100);
    }

    private static String escape(String text) {
        return text.replace("{", "(").replace("}", ")").replace("\n", " ");
    }

    private static String fmt(double fps) {
        return fps == Math.floor(fps) ? String.valueOf((long) fps) : String.valueOf(fps);
    }
}
