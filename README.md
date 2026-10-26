# Jazzerator

Procedural video game music generator in Java. Every song (overworld themes, dungeon crawls, boss battles, shop jingles, chiptune, lo-fi save rooms) is composed from music-theory rules and weighted random choices.

**Procedural, not AI.** There is no machine learning and no training data. A seeded random number generator walks through hand-written tables of genre conventions, so the same seed always produces the same song, byte for byte.

## Features

- **Genre pairings.** Choices aren't drawn independently. A song starts from a *pairing* of choices that go together, such as *Boss Battle / Phrygian fury*: Phrygian mode, 160–185 BPM, driving 16ths, distortion guitar, `i bII i bVII`. There are 8 genres and 16 pairings.
- **Song forms.** AABA, ABAB, ABBA, Verse-Chorus, Verse-Chorus-Bridge, Rondo (ABACA) and Intro-Loop-Outro. Repeated sections reuse their material.
- **Harmony.**
  - Roman-numeral progressions.
  - Cadences matched to each section: authentic cadences on home sections, half cadences on contrasting ones. Loops resolve back into the opening.
  - Decorations: 7ths and 9ths, sus4 resolutions, secondary dominants, tritone substitutions, borrowed chords, deceptive cadences, stepwise bass inversions, and voice leading.
- **Melody.**
  - Motifs built from chord tones and scale steps, developed through repetition, variation, inversion and a closing cadence.
  - Ornaments: runs, trills, passing and neighbor tones, grace notes, repeated notes and staccato.
- **Arrangement.** Eight grooves (March, Light, Rock, Tribal, Driving, Swing, Chip, Lo-fi) set the drums, bass and comping together. You get:
  - walking bass with chromatic approach notes
  - NES-style arpeggios
  - real swing timing
  - drum fills
  - section dynamics
- **Output.** MIDI files, live playback, WAV rendering (with optional SoundFonts), and long compilations with a YouTube-ready chapter tracklist.

## Requirements

- Java 21+
- Maven 3.9+

## Build

```sh
mvn package
```

This builds `target/jazzerator.jar` and runs the test suite.

## Desktop app

```sh
java -jar target/jazzerator.jar --gui
```

Pick a seed, genre, pairing, form, instruments and sound engine; generate; see the song as a piano roll with chord names; play it; and export MIDI or WAV. The app uses the same pipeline as the command line, so the same settings give the same song.

## Usage

```sh
# A random song, written to jazzerator-<genre>-<seed>.mid
java -jar target/jazzerator.jar

# A boss battle you can reproduce, played through your speakers
java -jar target/jazzerator.jar --genre boss --seed 7 --play

# Force a form and also render a WAV
java -jar target/jazzerator.jar --genre town --structure aaba --wav

# One hour of dungeon music in a single WAV, plus a chapter tracklist
java -jar target/jazzerator.jar --genre dungeon --compilation 60 --soundfont GeneralUser.sf2

# List genres and pairings
java -jar target/jazzerator.jar --list
```

| Option | Description |
|---|---|
| `--seed <n>` | Seed for a reproducible song (default: random) |
| `--genre <id>` | `overworld`, `dungeon`, `boss`, `town`, `chiptune`, `lofi`, `racing`, `puzzle` |
| `--structure <id>` | `aaba`, `abab`, `abba`, `verse-chorus`, `verse-chorus-bridge`, `rondo`, `intro-loop-outro` |
| `--count <n>` | Generate n songs with consecutive seeds |
| `--out <file>` | Output file name |
| `--play` | Play through the synthesizer |
| `--wav` | Also render each song to WAV |
| `--soundfont <file.sf2>` | Use a SoundFont for playback and rendering |
| `--synth <name>` | `soundfont` (default, JDK synthesizer), `fluidsynth` (higher-fidelity SoundFont playback; needs `--soundfont`), or `chip` (built-in 8-bit synthesizer) |
| `--fluidsynth <path>` | FluidSynth executable (default: `fluidsynth` on the PATH) |
| `--lead`, `--harmony`, `--comp`, `--bass <instrument>` | Override instruments by General MIDI name or number, e.g. `--lead "saw lead"`; the composition is unchanged |
| `--compilation <minutes>` | Render consecutive songs into one WAV plus a chapter tracklist |
| `--video <frames-or-image>` | With `--compilation`: also make an MP4 over a looping background |
| `--fps <n>` | Background animation frame rate (default 12) |
| `--ffmpeg <path>` | ffmpeg executable (default: `ffmpeg` on the PATH) |

Each run prints the song's settings and a chord chart:

```
Seed:      7
Genre:     Boss Battle / Phrygian fury
Key:       E Phrygian
Tempo:     181 BPM, Driving 16ths, 4/4
Structure: Intro-Loop-Outro (8-bar sections)
Instr:     Distortion Guitar / Brass Section / Synth Bass 1
Groove:    Driving
```

## SoundFonts

The JDK's built-in synthesizer works, but a good General MIDI SoundFont sounds much better. Free options include [GeneralUser GS](https://schristiancollins.com/generaluser.php) and FluidR3_GM. SoundFonts have their own licenses and are not part of this project. Put one in `soundfonts/` (git-ignored) and pass `--soundfont soundfonts/GeneralUser-GS.sf2`.

### FluidSynth

[FluidSynth](https://www.fluidsynth.org/) renders SoundFonts more faithfully than the JDK synthesizer (filters, envelopes, reverb). Download a release, then:

```sh
java -jar target/jazzerator.jar --synth fluidsynth --fluidsynth path/to/fluidsynth.exe     --soundfont soundfonts/MuseScore_General.sf2 --wav
```

All audio output is mixed automatically (each part balanced against the melody) and loudness-normalized, so songs and compilations play at a consistent level.

## Videos

With [ffmpeg](https://ffmpeg.org/) installed, `--video` turns a compilation into a YouTube-ready MP4:

```sh
java -jar target/jazzerator.jar --genre overworld --compilation 60     --soundfont soundfonts/GeneralUser-GS.sf2 --video path/to/background-frames
```

- The background can be a folder of numbered PNG frames (`frame0000.png`, ...) or a single image. It loops for the whole mix and is scaled to 1080p with nearest-neighbor, so pixel art stays crisp.
- A "now playing" caption (genre, pairing, key, tempo, seed) is burned in and changes at each chapter.
- The tracklist file holds YouTube chapter timestamps for the description.
- Check that the background's license allows your use, and credit the artist.

## How it works

```
seed → SongSpec        genre → pairing → key, mode, tempo, feel, meter, form, instruments
     → HarmonyPlanner  progression per section label, cadence by role, HarmonyDecorator embellishments
     → MelodyPlanner   motifs fitted to the chords, phrase development, cadences
     → Ornamenter      runs, trills, passing tones, grace notes, ...
     → Arranger        groove-driven drums, bass and comping, swing, dynamics
     → MIDI / WAV
```

| Package | Contents |
|---|---|
| `theory` | Notes, modes, scales, keys, chords, roman-numeral parsing |
| `preset` | Genre and pairing tables |
| `form` | Structures, sections, the song model |
| `harmony` | Progression planning, cadences, decorations |
| `melody` | Motifs, melody planning, ornamentation |
| `arrange` | Grooves, drum, bass and comp parts, timing |
| `midi` | MIDI writing, playback, WAV rendering |

## License

Copyright 2026 apzee

Licensed under the [Apache License, Version 2.0](LICENSE).
