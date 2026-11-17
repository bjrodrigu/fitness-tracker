package com.fitnesstracker.journal.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One journal entry: a run or a strength session, with free-form notes.
 * Run fields (distance, heart rate) are empty for strength sessions, and exercises are empty
 * for runs. Every entry belongs to one account (userId), which is never read from or sent
 * to the browser.
 */
@Document("activities")
@CompoundIndex(name = "user_date", def = "{'userId': 1, 'date': -1}")
@CompoundIndex(name = "user_external", def = "{'userId': 1, 'externalId': 1}", unique = true,
        partialFilter = "{ 'externalId': { $exists: true } }")
public record Activity(
        @Id String id,
        @JsonIgnore String userId,
        @NotNull ActivityType type,
        @NotNull LocalDate date,
        LocalTime startTime,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 20000) String notes,
        @Min(1) @Max(10) Integer effort,
        @PositiveOrZero Integer durationSec,
        @PositiveOrZero Double distanceMi,
        @Min(30) @Max(250) Integer avgHeartRate,
        @Size(max = 12) List<@NotBlank @Size(max = 30) String> tags,
        @Size(max = 40) List<@Valid Exercise> exercises,
        String source,
        String externalId,
        Instant createdAt,
        Instant updatedAt) {

    public Activity {
        tags = tags == null ? List.of() : List.copyOf(tags);
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    /** Seconds per mile, or null when there is no distance or time. */
    @JsonProperty(value = "paceSecPerMi", access = JsonProperty.Access.READ_ONLY)
    public Integer paceSecPerMi() {
        if (distanceMi == null || durationSec == null || distanceMi <= 0 || durationSec <= 0) {
            return null;
        }
        return (int) Math.round(durationSec / distanceMi);
    }

    /** Total pounds lifted: sum of reps x weight over all sets. */
    @JsonProperty(value = "volumeLb", access = JsonProperty.Access.READ_ONLY)
    public double volumeLb() {
        return exercises.stream()
                .flatMap(e -> e.sets().stream())
                .mapToDouble(s -> s.reps() * s.weightLb())
                .sum();
    }

    Activity withMeta(String newId, String owner, String newSource, Instant created, Instant updated) {
        return new Activity(newId, owner, type, date, startTime, title, notes, effort, durationSec, distanceMi,
                avgHeartRate, tags, exercises, newSource, externalId, created, updated);
    }
}
