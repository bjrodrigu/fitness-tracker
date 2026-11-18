package com.fitnesstracker.journal.parse;

import java.time.LocalDate;
import java.util.List;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.foods.FoodResult;
import com.fitnesstracker.journal.nutrition.Meal;

/**
 * Drafts for the person to check before anything is saved. Run and lift drafts carry the whole
 * entry to save (with its id when it updates one), so saving goes through the normal endpoints
 * and their validation.
 */
public record ParseResult(List<Draft> drafts, List<String> notes, String model) {

    public enum Kind {
        RUN, LIFT, FOOD
    }

    public enum Action {
        CREATE, UPDATE
    }

    public record Draft(
            Kind kind,
            Action action,
            LocalDate date,
            /** Lines saying what this draft does, e.g. "Bench press: 3 × 8 at 185 lb". */
            List<String> changes,
            /** For runs and lifts: the entry as it will be saved. */
            Activity activity,
            /** For food. */
            FoodDraft food) {
    }

    public record FoodDraft(Meal meal, String name, List<IngredientDraft> ingredients) {
    }

    /**
     * One ingredient with up to three database matches; the first is picked. Nutrition is per
     * 100 g, so the page can recalculate when the grams or the match change.
     */
    public record IngredientDraft(String said, String amount, double grams, boolean gramsGuessed,
            List<FoodResult> options) {
    }
}
