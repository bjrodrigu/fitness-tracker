package com.fitnesstracker.journal.nutrition;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Daily targets and where you're headed. The id is the account id, so there's one per account.
 */
@Document("nutrition_goals")
public record NutritionGoal(
        @Id @JsonIgnore String userId,
        @NotNull Direction direction,
        @DecimalMin("60") @DecimalMax("700") Double targetWeightLb,
        /** Planned pounds per week, always positive; direction says which way. */
        @DecimalMin("0") @DecimalMax("2") Double weeklyRateLb,
        @NotNull @Min(800) @Max(6000) Integer calories,
        @NotNull @Min(0) @Max(500) Integer proteinG,
        @NotNull @Min(0) @Max(1000) Integer carbsG,
        @NotNull @Min(0) @Max(400) Integer fatG,
        LocalDate startDate) {

    public enum Direction {
        LOSE, MAINTAIN, GAIN
    }

    NutritionGoal owned(String owner, LocalDate start) {
        return new NutritionGoal(owner, direction, targetWeightLb, weeklyRateLb, calories, proteinG, carbsG, fatG,
                startDate != null ? startDate : start);
    }
}
