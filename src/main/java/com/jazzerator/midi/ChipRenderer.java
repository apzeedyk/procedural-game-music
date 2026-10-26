package com.jazzerator.midi;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.sampled.AudioInputStream;

/**
 * A retro chiptune synthesizer: every part is a plain waveform instead of a sampled instrument, in the spirit of
 * the NES sound chip. Lead = 50% pulse with vibrato, harmony = 25% pulse, chords = 12.5% pulse, bass = triangle,
 * drums = seeded noise bursts and pitch-swept tones. Deterministic: the same sequence renders the same audio.
 */
public final class ChipRenderer implements AudioRenderer {
    private static final float RATE = WavRenderer.FORMAT.getFrameRate();

    // Channel layout (matches the Arranger): 0 lead, 1 comp, 2 bass, 3 harmony, 9 drums.
    private static final int LEAD = 0;
    private static final int COMP = 1;
    private static final int BASS = 2;
    private static final int HARMONY = 3;
    private static final int DRUMS = 9;

    private static final double ATTACK = 0.004;
    private static final double DECAY = 0.08;
    private static final double SUSTAIN = 0.75;
    private static final double RELEASE = 0.05;
    /** Drums are built hot enough that the mixer turns them down, never up against the top of the fader. */
    private static final double DRUM_LEVEL = 2.5;

    @Override
    public AudioInputStream render(Sequence sequence, int bpm, double padSeconds) {
        double secondsPerTick = 60.0 / (bpm * (double) sequence.getResolution());
        int frames = (int) Math.round(RATE * WavRenderer.seconds(sequence, bpm, padSeconds));
        float[] left = new float[frames];
        float[] right = new float[frames];

        int[] volume = new int[16];
        Arrays.fill(volume, 100);
        Map<Integer, long[]> open = new HashMap<>(); // channel*128+pitch -> {startTick, velocity}
        for (MidiEvent e : WavRenderer.sortedShortEvents(sequence)) {
            ShortMessage m = (ShortMessage) e.getMessage();
            int ch = m.getChannel();
            int key = ch * 128 + m.getData1();
            if (m.getCommand() == ShortMessage.CONTROL_CHANGE && m.getData1() == 7) {
                volume[ch] = m.getData2();
            } else if (m.getCommand() == ShortMessage.NOTE_ON && m.getData2() > 0) {
                open.put(key, new long[]{e.getTick(), m.getData2()});
            } else if (m.getCommand() == ShortMessage.NOTE_OFF || m.getCommand() == ShortMessage.NOTE_ON) {
                long[] on = open.remove(key);
                if (on != null) {
                    double gain = Math.pow(volume[ch] / 127.0, 2) * (on[1] / 127.0); // GM volume curve
                    double start = on[0] * secondsPerTick;
                    double end = e.getTick() * secondsPerTick;
                    if (ch == DRUMS) {
                        drum(left, right, m.getData1(), start, gain, on[0]);
                    } else {
                        tone(left, right, ch, m.getData1(), start, end, gain);
                    }
                }
            }
        }
        byte[] pcm = toPcm(left, right);
        return new AudioInputStream(new ByteArrayInputStream(pcm), WavRenderer.FORMAT, frames);
    }

    private static void tone(float[] left, float[] right, int channel, int pitch, double start, double end,
                             double gain) {
        double freq = 440 * Math.pow(2, (pitch - 69) / 12.0);
        double level;
        double duty;
        double pan; // -1 left .. +1 right: chords and harmony spread a little for width
        switch (channel) {
            case LEAD -> { level = 0.20; duty = 0.5; pan = 0; }
            case HARMONY -> { level = 0.20; duty = 0.25; pan = 0.35; }
            case COMP -> { level = 0.13; duty = 0.25; pan = -0.35; }
            case BASS -> { level = 0.30; duty = -1; pan = 0; } // triangle
            default -> { level = 0.15; duty = 0.5; pan = 0; }
        }
        double amp = level * gain;
        int from = (int) (start * RATE);
        int to = Math.min(left.length, (int) ((end + RELEASE) * RATE));
        double held = end - start;
        double phase = 0;
        for (int i = from; i < to; i++) {
            double t = (i - from) / RATE;
            double env = t < ATTACK ? t / ATTACK
                    : t < ATTACK + DECAY ? 1 - (1 - SUSTAIN) * (t - ATTACK) / DECAY
                    : SUSTAIN;
            if (t > held) {
                env *= Math.max(0, 1 - (t - held) / RELEASE);
            }
            // Gentle delayed vibrato on long lead notes, like a chip composer's pitch LFO.
            double vibrato = channel == LEAD && t > 0.25 ? 1 + 0.004 * Math.sin(2 * Math.PI * 5.5 * t) : 1;
            phase += freq * vibrato / RATE;
            phase -= Math.floor(phase);
            double s = duty < 0 ? 4 * Math.abs(phase - 0.5) - 1 : (phase < duty ? 1 : -1);
            double v = s * env * amp;
            left[i] += (float) (v * (1 - Math.max(0, pan)));
            right[i] += (float) (v * (1 + Math.min(0, pan)));
        }
    }

    /** NES-style percussion from noise bursts and pitch sweeps, keyed by General MIDI drum note. */
    private static void drum(float[] left, float[] right, int note, double start, double velocityGain, long seed) {
        double gain = velocityGain * DRUM_LEVEL;
        switch (note) {
            case 35, 36 -> sweep(left, right, start, 0.18, 120, 45, 0.55 * gain);              // kick
            case 38, 40 -> {                                                                   // snare
                noise(left, right, start, 0.15, 0.30 * gain, false, seed);
                sweep(left, right, start, 0.08, 190, 160, 0.20 * gain);
            }
            case 42, 44 -> noise(left, right, start, 0.04, 0.16 * gain, true, seed);          // closed / pedal hat
            case 46 -> noise(left, right, start, 0.25, 0.14 * gain, true, seed);               // open hat
            case 49, 57 -> noise(left, right, start, 0.9, 0.18 * gain, true, seed);            // crash
            case 51, 59 -> noise(left, right, start, 0.3, 0.10 * gain, true, seed);            // ride
            case 45, 47, 48, 50 -> sweep(left, right, start, 0.22, 80 + (note - 45) * 25.0,
                    55 + (note - 45) * 18.0, 0.40 * gain);                                     // toms
            case 54, 70, 69 -> noise(left, right, start, 0.06, 0.10 * gain, true, seed);       // tambourine, shaker
            default -> noise(left, right, start, 0.05, 0.10 * gain, true, seed);
        }
    }

    /** A triangle tone gliding from one frequency to another with an exponential decay (kick, toms, snare body). */
    private static void sweep(float[] left, float[] right, double start, double length, double fromHz, double toHz,
                              double amp) {
        int from = (int) (start * RATE);
        int to = Math.min(left.length, from + (int) (length * RATE));
        double phase = 0;
        for (int i = from; i < to; i++) {
            double t = (i - from) / RATE;
            double freq = fromHz * Math.pow(toHz / fromHz, t / length);
            phase += freq / RATE;
            phase -= Math.floor(phase);
            double v = (4 * Math.abs(phase - 0.5) - 1) * amp * Math.exp(-5 * t / length);
            left[i] += (float) v;
            right[i] += (float) v;
        }
    }

    /** A burst of seeded white noise; "bright" applies a first-difference high-pass for hats and cymbals. */
    private static void noise(float[] left, float[] right, double start, double length, double amp, boolean bright,
                              long seed) {
        int from = (int) (start * RATE);
        int to = Math.min(left.length, from + (int) (length * RATE));
        long state = seed * 0x9E3779B97F4A7C15L + 1;
        double previous = 0;
        for (int i = from; i < to; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            double x = (state >>> 11) * 0x1.0p-53 * 2 - 1;
            double s = bright ? (x - previous) * 0.7 : x;
            previous = x;
            double t = (i - from) / RATE;
            double v = s * amp * Math.exp(-5 * t / length);
            left[i] += (float) v;
            right[i] += (float) v;
        }
    }

    /** Soft-clipped 16-bit little-endian stereo. */
    private static byte[] toPcm(float[] left, float[] right) {
        byte[] out = new byte[left.length * 4];
        for (int i = 0; i < left.length; i++) {
            short l = (short) Math.round(Math.tanh(left[i]) * 32000);
            short r = (short) Math.round(Math.tanh(right[i]) * 32000);
            out[i * 4] = (byte) l;
            out[i * 4 + 1] = (byte) (l >> 8);
            out[i * 4 + 2] = (byte) r;
            out[i * 4 + 3] = (byte) (r >> 8);
        }
        return out;
    }
}
