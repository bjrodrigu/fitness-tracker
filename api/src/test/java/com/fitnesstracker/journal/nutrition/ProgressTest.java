package com.fitnesstracker.journal.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fitnesstracker.journal.nutrition.NutritionGoal.Direction;

class ProgressTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    static FoodEntry food(LocalDate d, double servings, int kcal, double p) {
        return new FoodEntry(null, "u", d, Meal.LUNCH, "Food", servings, null, kcal, p, 10.0, 5.0, null);
    }

    @Test
    void dailyTotalsMultiplyServings() {
        var r = Progress.compute(List.of(food(TODAY, 2, 300, 20), food(TODAY, 0.5, 400, 10), food(TODAY.minusDays(1), 1, 100, 0)),
                List.of(), null, TODAY, 7);
        assertThat(r.days()).hasSize(7);
        var today = r.days().get(6);
        assertThat(today.calories()).isEqualTo(800);
        assertThat(today.proteinG()).isEqualTo(45);
        assertThat(today.entries()).isEqualTo(2);
        // Average over logged days only.
        assertThat(r.summary().avgCalories7()).isEqualTo(450);
        assertThat(r.summary().daysLogged7()).isEqualTo(2);
    }

    @Test
    void trendIsTheSevenDayAverage() {
        var w = List.of(new WeightEntry(null, "u", TODAY.minusDays(8), 200),
                new WeightEntry(null, "u", TODAY.minusDays(2), 180),
                new WeightEntry(null, "u", TODAY, 182));
        var pts = Progress.trend(w);
        assertThat(pts.get(0).trendLb()).isEqualTo(200);
        assertThat(pts.get(1).trendLb()).isEqualTo(190); // 6 days apart, so both count
        assertThat(pts.get(2).trendLb()).isEqualTo(181); // the 200 is 8 days back, outside the window
    }

    @Test
    void steadyLossProjectsTheGoalDate() {
        List<WeightEntry> w = new ArrayList<>();
        for (int i = 42; i >= 0; i--) {
            w.add(new WeightEntry(null, "u", TODAY.minusDays(i), 190 - (42 - i) / 7.0)); // 1 lb a week down
        }
        var goal = new NutritionGoal("u", Direction.LOSE, 180.0, 1.0, 2000, 150, 200, 60, TODAY.minusDays(42));
        var s = Progress.compute(List.of(), w, goal, TODAY, 56).summary();
        assertThat(s.weeklyRateLb()).isBetween(-1.05, -0.95);
        assertThat(s.startLb()).isEqualTo(190.0);
        assertThat(s.remainingLb()).isNegative();
        // About 4.5 lb to go at 1 lb a week.
        assertThat(s.projectedDate()).isBetween(TODAY.plusWeeks(4), TODAY.plusWeeks(5));
    }

    @Test
    void noProjectionWhenHeadingTheWrongWay() {
        List<WeightEntry> w = new ArrayList<>();
        for (int i = 28; i >= 0; i--) {
            w.add(new WeightEntry(null, "u", TODAY.minusDays(i), 180 + (28 - i) / 14.0));
        }
        var goal = new NutritionGoal("u", Direction.LOSE, 170.0, 1.0, 2000, 150, 200, 60, null);
        assertThat(Progress.compute(List.of(), w, goal, TODAY, 28).summary().projectedDate()).isNull();
    }

    @Test
    void emptyIsFine() {
        var r = Progress.compute(List.of(), List.of(), null, TODAY, 7);
        assertThat(r.summary().currentLb()).isNull();
        assertThat(r.summary().avgCalories7()).isNull();
    }
}
