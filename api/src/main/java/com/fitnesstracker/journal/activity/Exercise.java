package com.fitnesstracker.journal.activity;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Exercise(@NotBlank @Size(max = 80) String name, List<@Valid ExerciseSet> sets) {

    public Exercise {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }
}
