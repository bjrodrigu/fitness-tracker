package com.fitnesstracker.journal.nutrition;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One food logged on a day. Nutrition is per serving; totals are servings x per-serving values.
 */
@Document("food")
@CompoundIndex(name = "user_date", def = "{'userId': 1, 'date': -1}")
public record FoodEntry(
        @Id String id,
        @JsonIgnore String userId,
        @NotNull LocalDate date,
        @NotNull Meal meal,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.05") @DecimalMax("50") Double servings,
        @NotNull @PositiveOrZero @Max(5000) Integer calories,
        @PositiveOrZero @DecimalMax("500") Double proteinG,
        @PositiveOrZero @DecimalMax("1000") Double carbsG,
        @PositiveOrZero @DecimalMax("500") Double fatG,
        Instant createdAt) {

    public FoodEntry {
        proteinG = proteinG == null ? 0 : proteinG;
        carbsG = carbsG == null ? 0 : carbsG;
        fatG = fatG == null ? 0 : fatG;
    }

    @JsonProperty(value = "totalCalories", access = JsonProperty.Access.READ_ONLY)
    public double totalCalories() {
        return servings * calories;
    }

    @JsonProperty(value = "totalProteinG", access = JsonProperty.Access.READ_ONLY)
    public double totalProteinG() {
        return servings * proteinG;
    }

    @JsonProperty(value = "totalCarbsG", access = JsonProperty.Access.READ_ONLY)
    public double totalCarbsG() {
        return servings * carbsG;
    }

    @JsonProperty(value = "totalFatG", access = JsonProperty.Access.READ_ONLY)
    public double totalFatG() {
        return servings * fatG;
    }

    FoodEntry owned(String newId, String owner, Instant created) {
        return new FoodEntry(newId, owner, date, meal, name.trim(), servings, calories, proteinG, carbsG, fatG, created);
    }
}
