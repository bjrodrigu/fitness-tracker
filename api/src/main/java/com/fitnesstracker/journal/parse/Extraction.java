package com.fitnesstracker.journal.parse;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What the language model pulls out of a message: plain facts only. Matching exercise names,
 * finding today's entries, unit conversion and food lookups all happen in code afterwards
 * (see {@link DraftBuilder}), so a small model only has to read, not reason.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Extraction(List<Item> items) {

    public Extraction {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonPropertyDescription("run, lift, fix_set, fix_run or food") String kind,
            @JsonPropertyDescription("0 for today, -1 for yesterday, -2 for the day before") Integer dayOffset,
            @JsonPropertyDescription("run distance as said") Double distance,
            @JsonPropertyDescription("mi, km or m") String distanceUnit,
            @JsonPropertyDescription("run time in minutes, e.g. 42.5") Double minutes,
            @JsonPropertyDescription("how hard it felt, 1 to 10, only if said") Integer effort,
            @JsonPropertyDescription("short title, only if the message names the workout, e.g. Tempo run") String title,
            @JsonPropertyDescription("how it went, in the person's words") String notes,
            @JsonPropertyDescription("exercise name as said, e.g. bench") String exercise,
            @JsonPropertyDescription("sets for a lift") List<SetGroup> sets,
            @JsonPropertyDescription("for fix_set: which set, 1 for the first, 0 for the last") Integer setNumber,
            @JsonPropertyDescription("for fix_set: corrected reps") Integer reps,
            @JsonPropertyDescription("for fix_set: corrected weight") Double weight,
            @JsonPropertyDescription("lb or kg") String weightUnit,
            @JsonPropertyDescription("name of what was eaten, e.g. Chicken burrito bowl") String dish,
            @JsonPropertyDescription("breakfast, lunch, dinner or snack, only if said") String meal,
            @JsonPropertyDescription("ingredients of what was eaten") List<Ingredient> ingredients) {
    }

    /** "3x8 at 185" is one group: 3 sets of 8 reps at 185. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SetGroup(
            @JsonPropertyDescription("number of sets, 1 if not said") Integer count,
            Integer reps,
            @JsonPropertyDescription("0 for bodyweight") Double weight,
            @JsonPropertyDescription("lb or kg") String weightUnit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ingredient(
            @JsonPropertyDescription("plain food name to look up, e.g. chicken breast") String food,
            @JsonPropertyDescription("amount as said, e.g. 2 tbsp") String amount,
            @JsonPropertyDescription("best guess of the weight in grams") Double grams) {
    }
}
