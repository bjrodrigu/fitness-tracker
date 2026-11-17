package com.fitnesstracker.journal.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fitnesstracker.journal.nutrition.GoalCalculator.Activity;
import com.fitnesstracker.journal.nutrition.GoalCalculator.Profile;
import com.fitnesstracker.journal.nutrition.GoalCalculator.Sex;
import com.fitnesstracker.journal.nutrition.NutritionGoal.Direction;

class GoalCalculatorTest {

    @Test
    void maintenanceMatchesMifflinStJeor() {
        // 180 lb, 70 in, 30-year-old man: BMR = 10*81.65 + 6.25*177.8 - 150 + 5 = 1782; x1.55 = 2762.
        var s = GoalCalculator.suggest(new Profile(Sex.MALE, 30, 70.0, 180.0, Activity.MODERATE, Direction.MAINTAIN, null));
        assertThat(s.maintenanceCalories()).isEqualTo(2760);
        assertThat(s.calories()).isEqualTo(2760);
        assertThat(s.proteinG()).isEqualTo(144); // 0.8 g/lb
        assertThat(s.notes()).isEmpty();
    }

    @Test
    void losingAPoundAWeekIs500Under() {
        var s = GoalCalculator.suggest(new Profile(Sex.MALE, 30, 70.0, 180.0, Activity.MODERATE, Direction.LOSE, 1.0));
        assertThat(s.calories()).isEqualTo(2260);
        assertThat(s.proteinG()).isEqualTo(162); // 0.9 g/lb
        // Macros add back up to the calories, give or take rounding.
        assertThat(s.proteinG() * 4 + s.carbsG() * 4 + s.fatG() * 9).isBetween(2245, 2275);
    }

    @Test
    void fastLossIsCappedAtOnePercentOfBodyWeight() {
        var s = GoalCalculator.suggest(new Profile(Sex.FEMALE, 35, 64.0, 150.0, Activity.MODERATE, Direction.LOSE, 3.0));
        assertThat(s.weeklyRateLb()).isEqualTo(1.5);
        assertThat(s.notes()).anyMatch(n -> n.contains("1.5 lb a week"));
    }

    @Test
    void caloriesNeverGoBelowTheFloor() {
        var s = GoalCalculator.suggest(new Profile(Sex.FEMALE, 60, 60.0, 120.0, Activity.SEDENTARY, Direction.LOSE, 1.0));
        assertThat(s.calories()).isEqualTo(GoalCalculator.MIN_CALORIES_FEMALE);
        assertThat(s.weeklyRateLb()).isLessThan(1.0);
        assertThat(s.notes()).anyMatch(n -> n.contains("1200"));
    }

    @Test
    void gainIsCappedAtAPoundAWeek() {
        var s = GoalCalculator.suggest(new Profile(Sex.MALE, 22, 72.0, 160.0, Activity.VERY, Direction.GAIN, 2.0));
        assertThat(s.weeklyRateLb()).isEqualTo(1.0);
        assertThat(s.calories()).isEqualTo(s.maintenanceCalories() + 500);
    }
}
