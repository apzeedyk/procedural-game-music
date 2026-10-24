package com.jazzerator.preset;

/** General MIDI program numbers (0-based) for each part. */
public record Instruments(int lead, int comp, int bass) {
    public static final int PIANO = 0;
    public static final int E_PIANO = 4;
    public static final int CELESTA = 8;
    public static final int MUSIC_BOX = 10;
    public static final int VIBRAPHONE = 11;
    public static final int MARIMBA = 12;
    public static final int ROCK_ORGAN = 18;
    public static final int CHURCH_ORGAN = 19;
    public static final int ACCORDION = 21;
    public static final int NYLON_GUITAR = 24;
    public static final int OVERDRIVE_GUITAR = 29;
    public static final int DISTORTION_GUITAR = 30;
    public static final int ACOUSTIC_BASS = 32;
    public static final int FINGER_BASS = 33;
    public static final int SYNTH_BASS = 38;
    public static final int PIZZICATO = 45;
    public static final int HARP = 46;
    public static final int STRINGS = 48;
    public static final int SLOW_STRINGS = 49;
    public static final int CHOIR = 52;
    public static final int TRUMPET = 56;
    public static final int FRENCH_HORN = 60;
    public static final int BRASS = 61;
    public static final int OBOE = 68;
    public static final int CLARINET = 71;
    public static final int FLUTE = 73;
    public static final int PAN_FLUTE = 75;
    public static final int SQUARE_LEAD = 80;
    public static final int SAW_LEAD = 81;
    public static final int WARM_PAD = 89;

    private static final String[] GM_NAMES = {
        "Acoustic Grand Piano", "Bright Piano", "Electric Grand", "Honky-tonk Piano", "Electric Piano 1",
        "Electric Piano 2", "Harpsichord", "Clavinet", "Celesta", "Glockenspiel", "Music Box", "Vibraphone",
        "Marimba", "Xylophone", "Tubular Bells", "Dulcimer", "Drawbar Organ", "Percussive Organ", "Rock Organ",
        "Church Organ", "Reed Organ", "Accordion", "Harmonica", "Tango Accordion", "Nylon Guitar", "Steel Guitar",
        "Jazz Guitar", "Clean Guitar", "Muted Guitar", "Overdrive Guitar", "Distortion Guitar", "Guitar Harmonics",
        "Acoustic Bass", "Finger Bass", "Pick Bass", "Fretless Bass", "Slap Bass 1", "Slap Bass 2", "Synth Bass 1",
        "Synth Bass 2", "Violin", "Viola", "Cello", "Contrabass", "Tremolo Strings", "Pizzicato Strings",
        "Orchestral Harp", "Timpani", "String Ensemble", "Slow Strings", "Synth Strings 1", "Synth Strings 2",
        "Choir Aahs", "Voice Oohs", "Synth Voice", "Orchestra Hit", "Trumpet", "Trombone", "Tuba", "Muted Trumpet",
        "French Horn", "Brass Section", "Synth Brass 1", "Synth Brass 2", "Soprano Sax", "Alto Sax", "Tenor Sax",
        "Baritone Sax", "Oboe", "English Horn", "Bassoon", "Clarinet", "Piccolo", "Flute", "Recorder", "Pan Flute",
        "Blown Bottle", "Shakuhachi", "Whistle", "Ocarina", "Square Lead", "Saw Lead", "Calliope Lead", "Chiff Lead",
        "Charang Lead", "Voice Lead", "Fifths Lead", "Bass + Lead", "New Age Pad", "Warm Pad", "Polysynth Pad",
        "Choir Pad", "Bowed Pad", "Metallic Pad", "Halo Pad", "Sweep Pad", "Rain FX", "Soundtrack FX", "Crystal FX",
        "Atmosphere FX", "Brightness FX", "Goblins FX", "Echoes FX", "Sci-fi FX", "Sitar", "Banjo", "Shamisen",
        "Koto", "Kalimba", "Bagpipe", "Fiddle", "Shanai", "Tinkle Bell", "Agogo", "Steel Drums", "Woodblock",
        "Taiko Drum", "Melodic Tom", "Synth Drum", "Reverse Cymbal", "Guitar Fret Noise", "Breath Noise", "Seashore",
        "Bird Tweet", "Telephone Ring", "Helicopter", "Applause", "Gunshot"
    };

    /** General MIDI instrument name for a 0-based program number. */
    public static String name(int program) {
        return program >= 0 && program < GM_NAMES.length ? GM_NAMES[program] : "Program " + program;
    }
}
