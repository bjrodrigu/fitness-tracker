package com.fitnesstracker.journal.activity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/** One set: reps at a weight. Weight 0 means bodyweight. */
public record ExerciseSet(@Min(0) @Max(1000) int reps, @PositiveOrZero double weightLb) {
}
