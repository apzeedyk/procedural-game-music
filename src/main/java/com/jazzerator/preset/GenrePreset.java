package com.jazzerator.preset;

import java.util.List;

/** A video game music genre made up of one or more common pairings. */
public record GenrePreset(String id, String name, double weight, List<Pairing> pairings) {
}
