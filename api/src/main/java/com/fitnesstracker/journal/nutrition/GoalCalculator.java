package com.fitnesstracker.journal.nutrition;

import java.util.ArrayList;
import java.util.List;

import com.fitnesstracker.journal.nutrition.NutritionGoal.Direction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Suggests daily calories and macros. Maintenance is Mifflin-St Jeor times an activity factor;
 * each pound a week of change is 500 kcal a day. Pure functions, no Spring.
 */
public final class GoalCalculator {

    /** Fastest suggested loss, and never more than 1% of body weight a week. */
    static final double MAX_LOSS_LB_PER_WEEK = 2.0;
    static final double MAX_GAIN_LB_PER_WEEK = 1.0;
    static final int MIN_CALORIES_FEMALE = 1200;
    static final int MIN_CALORIES_MALE = 1500;

    private GoalCalculator() {
    }

    public enum Sex {
        FEMALE, MALE
    }

    public enum Activity {
        /** Desk job, little exercise. */
        SEDENTARY(1.2),
        /** Exercise 1 to 3 days a week. */
        LIGHT(1.375),
        /** Exercise 3 to 5 days a week. */
        MODERATE(1.55),
        /** Hard training 6 to 7 days a week. */
        VERY(1.725);

        final double factor;

        Activity(double factor) {
            this.factor = factor;
        }
    }

    public record Profile(
            @NotNull Sex sex,
            @NotNull @Min(14) @Max(100) Integer age,
            @NotNull @DecimalMin("48") @DecimalMax("90") Double heightIn,
            @NotNull @DecimalMin("60") @DecimalMax("700") Double weightLb,
            @NotNull Activity activity,
            @NotNull Direction direction,
            @DecimalMin("0") @DecimalMax("5") Double weeklyRateLb) {
    }

    public record Suggestion(int maintenanceCalories, int calories, int proteinG, int carbsG, int fatG,
            double weeklyRateLb, List<String> notes) {
    }

    public static Suggestion suggest(Profile p) {
        List<String> notes = new ArrayList<>();
        double kg = p.weightLb() * 0.45359237;
        double cm = p.heightIn() * 2.54;
        double bmr = 10 * kg + 6.25 * cm - 5 * p.age() + (p.sex() == Sex.MALE ? 5 : -161);
        int maintenance = roundTo(bmr * p.activity().factor, 10);

        double rate = p.direction() == Direction.MAINTAIN || p.weeklyRateLb() == null ? 0 : p.weeklyRateLb();
        if (p.direction() == Direction.LOSE) {
            double cap = Math.min(MAX_LOSS_LB_PER_WEEK, p.weightLb() * 0.01);
            if (rate > cap) {
                rate = Math.floor(cap * 4) / 4;
                notes.add("Rate lowered to " + fmt(rate) + " lb a week, about 1% of body weight at most.");
            }
        } else if (p.direction() == Direction.GAIN && rate > MAX_GAIN_LB_PER_WEEK) {
            rate = MAX_GAIN_LB_PER_WEEK;
            notes.add("Rate lowered to 1 lb a week; faster gain is mostly fat.");
        }

        int sign = p.direction() == Direction.LOSE ? -1 : p.direction() == Direction.GAIN ? 1 : 0;
        int calories = roundTo(maintenance + sign * rate * 500, 10);
        int floor = p.sex() == Sex.MALE ? MIN_CALORIES_MALE : MIN_CALORIES_FEMALE;
        if (calories < floor) {
            calories = floor;
            rate = Math.max(0, (maintenance - floor) / 500.0);
            notes.add("Calories kept at " + floor + " a day, a common minimum without medical supervision, so the "
                    + "expected rate is about " + fmt(rate) + " lb a week.");
        }

        // Protein per pound of body weight: higher when changing weight, to keep or build muscle.
        double proteinPerLb = p.direction() == Direction.MAINTAIN ? 0.8 : 0.9;
        int protein = (int) Math.round(Math.min(p.weightLb(), 250) * proteinPerLb);
        int fat = (int) Math.round(calories * 0.28 / 9);
        int carbs = (int) Math.max(0, Math.round((calories - protein * 4 - fat * 9) / 4.0));
        return new Suggestion(maintenance, calories, protein, carbs, fat, rate, notes);
    }

    private static int roundTo(double v, int step) {
        return (int) (Math.round(v / step) * step);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }
}
