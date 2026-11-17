package com.fitnesstracker.journal.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fitnesstracker.journal.nutrition.FoodEntry;
import com.fitnesstracker.journal.nutrition.Meal;
import com.fitnesstracker.journal.nutrition.NutritionGoal;
import com.fitnesstracker.journal.nutrition.NutritionGoal.Direction;
import com.fitnesstracker.journal.nutrition.WeightEntry;

/**
 * Eight weeks of made-up eating and weigh-ins for the demo account: a slow cut from about
 * 171 lb toward 162, with a few unlogged days and normal day-to-day weight noise.
 */
public final class DemoNutritionGenerator {

    static final int DAYS = 56;

    private DemoNutritionGenerator() {
    }

    record Food(Meal meal, String name, int kcal, double p, double c, double f) {
    }

    static final List<Food> FOODS = List.of(
            new Food(Meal.BREAKFAST, "Oatmeal with banana", 310, 9, 58, 5),
            new Food(Meal.BREAKFAST, "Greek yogurt and granola", 330, 24, 38, 9),
            new Food(Meal.BREAKFAST, "Two eggs on toast", 360, 19, 30, 17),
            new Food(Meal.LUNCH, "Chicken burrito bowl", 640, 45, 70, 18),
            new Food(Meal.LUNCH, "Turkey sandwich", 480, 32, 50, 14),
            new Food(Meal.LUNCH, "Tuna salad", 420, 38, 18, 22),
            new Food(Meal.LUNCH, "Leftover pasta", 560, 22, 80, 15),
            new Food(Meal.DINNER, "Salmon, rice and broccoli", 620, 42, 60, 20),
            new Food(Meal.DINNER, "Chili", 540, 36, 48, 20),
            new Food(Meal.DINNER, "Stir fry with tofu", 520, 28, 62, 17),
            new Food(Meal.DINNER, "Pizza, 2 slices", 600, 24, 68, 24),
            new Food(Meal.SNACK, "Protein shake", 160, 30, 4, 3),
            new Food(Meal.SNACK, "Apple and peanut butter", 280, 7, 30, 16),
            new Food(Meal.SNACK, "Trail mix", 210, 6, 18, 14),
            new Food(Meal.SNACK, "Cottage cheese", 180, 24, 8, 5));

    public static NutritionGoal goal(String userId, LocalDate today) {
        return new NutritionGoal(userId, Direction.LOSE, 162.0, 0.75, 2150, 150, 230, 68, today.minusDays(DAYS - 1L));
    }

    public static List<FoodEntry> food(String userId, LocalDate today, long seed, Instant now) {
        Random rnd = new Random(seed);
        List<FoodEntry> out = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            // Most days are logged; a weekend day now and then isn't.
            if (i > 0 && rnd.nextDouble() < (d.getDayOfWeek().getValue() >= 6 ? 0.25 : 0.06)) {
                continue;
            }
            for (Meal meal : Meal.values()) {
                if (i == 0 && meal == Meal.DINNER) {
                    break; // today: dinner hasn't happened yet
                }
                List<Food> options = FOODS.stream().filter(f -> f.meal() == meal).toList();
                int count = meal == Meal.SNACK ? 1 + (rnd.nextDouble() < 0.4 ? 1 : 0) : 1;
                for (int k = 0; k < count; k++) {
                    Food f = options.get(rnd.nextInt(options.size()));
                    double servings = meal == Meal.SNACK || rnd.nextDouble() < 0.8 ? 1 : 1.5;
                    out.add(new FoodEntry(null, userId, d, meal, f.name(), servings, null, f.kcal(), f.p(), f.c(), f.f(),
                            now.minusSeconds(i * 86400L - meal.ordinal() * 3600L - k * 60L)));
                }
            }
        }
        return out;
    }

    public static List<WeightEntry> weights(String userId, LocalDate today, long seed) {
        Random rnd = new Random(seed + 1);
        List<WeightEntry> out = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            if (i > 0 && rnd.nextDouble() < 0.3) {
                continue;
            }
            double trend = 170.8 - (DAYS - 1 - i) * (0.65 / 7);
            double lb = trend + rnd.nextGaussian() * 0.7;
            out.add(new WeightEntry(null, userId, today.minusDays(i), Math.round(lb * 10) / 10.0));
        }
        return out;
    }
}
