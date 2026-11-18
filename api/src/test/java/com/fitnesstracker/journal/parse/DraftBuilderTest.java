package com.fitnesstracker.journal.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.activity.Exercise;
import com.fitnesstracker.journal.activity.ExerciseSet;
import com.fitnesstracker.journal.foods.FoodResult;
import com.fitnesstracker.journal.foods.FoodResult.Nutrients;
import com.fitnesstracker.journal.nutrition.Meal;
import com.fitnesstracker.journal.parse.Extraction.Ingredient;
import com.fitnesstracker.journal.parse.Extraction.Item;
import com.fitnesstracker.journal.parse.Extraction.SetGroup;
import com.fitnesstracker.journal.parse.ParseResult.Action;
import com.fitnesstracker.journal.parse.ParseResult.Kind;

class DraftBuilderTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    static final Map<String, Integer> NAMES = Map.of("Bench press", 20, "Incline bench press", 3, "Back squat", 15,
            "Pull-ups", 6);

    static Item run(double dist, String unit, Double min, String notes) {
        return new Item("run", 0, dist, unit, min, null, null, notes, null, null, null, null, null, null, null, null, null);
    }

    static Item lift(String ex, SetGroup... sets) {
        return new Item("lift", 0, null, null, null, null, null, null, ex, List.of(sets), null, null, null, null, null,
                null, null);
    }

    static Item fixSet(String ex, Integer n, Integer reps, Double weight) {
        return new Item("fix_set", 0, null, null, null, null, null, null, ex, null, n, reps, weight, null, null, null,
                null);
    }

    static Item food(String dish, String meal, Ingredient... ings) {
        return new Item("food", 0, null, null, null, null, null, null, null, null, null, null, null, null, dish, meal,
                List.of(ings));
    }

    static Activity strength(String id, Exercise... ex) {
        return new Activity(id, "u", ActivityType.STRENGTH, TODAY, null, "Push day", null, null, null, null, null,
                List.of(), List.of(ex), "manual", null, Instant.EPOCH, Instant.EPOCH);
    }

    static DraftBuilder builder(List<Activity> recent, List<FoodResult> foods) {
        return new DraftBuilder(TODAY, 18, recent, NAMES, q -> foods);
    }

    @Test
    void runAndLiftBecomeTwoNewEntries() {
        var out = builder(List.of(), List.of()).build(new Extraction(List.of(
                run(5, "km", 28.0, "Easy"),
                lift("bench", new SetGroup(3, 8, 185.0, "lb")),
                lift("squats", new SetGroup(2, 5, 100.0, "kg")))));

        assertThat(out.drafts()).hasSize(2);
        var r = out.drafts().get(0);
        assertThat(r.kind()).isEqualTo(Kind.RUN);
        assertThat(r.activity().distanceMi()).isEqualTo(3.11);
        assertThat(r.activity().durationSec()).isEqualTo(1680);
        assertThat(r.activity().notes()).isEqualTo("Easy");

        var l = out.drafts().get(1);
        assertThat(l.action()).isEqualTo(Action.CREATE);
        assertThat(l.activity().exercises()).extracting(Exercise::name).containsExactly("Bench press", "Back squat");
        assertThat(l.activity().exercises().get(0).sets()).hasSize(3).allMatch(s -> s.reps() == 8 && s.weightLb() == 185);
        assertThat(l.activity().exercises().get(1).sets().get(0).weightLb()).isEqualTo(220.5);
        assertThat(l.changes()).contains("Bench press: 3 × 8 at 185 lb");
    }

    @Test
    void setsGoIntoTodaysSessionAndCorrectionsChangeTheLastSet() {
        var today = strength("s1", new Exercise("Bench press", List.of(new ExerciseSet(8, 185), new ExerciseSet(8, 185))));
        var out = builder(List.of(today), List.of()).build(new Extraction(List.of(
                lift("Bench Press", new SetGroup(1, 8, 185.0, null)),
                lift("pullups", new SetGroup(3, 10, null, null)),
                fixSet("bench", 0, 6, null))));

        assertThat(out.drafts()).hasSize(1);
        var d = out.drafts().get(0);
        assertThat(d.action()).isEqualTo(Action.UPDATE);
        assertThat(d.activity().id()).isEqualTo("s1");
        assertThat(d.activity().title()).isEqualTo("Push day");
        var bench = d.activity().exercises().get(0);
        assertThat(bench.sets()).extracting(ExerciseSet::reps).containsExactly(8, 8, 6);
        assertThat(d.activity().exercises().get(1).name()).isEqualTo("Pull-ups");
        assertThat(d.changes()).contains("Bench press set 3: 8 at 185 lb → 6 at 185 lb",
                "No weight was given for Pull-ups, so it's saved as bodyweight");
    }

    @Test
    void correctionWithNothingToCorrectIsANote() {
        var out = builder(List.of(), List.of()).build(new Extraction(List.of(fixSet("bench", 0, 6, null))));
        assertThat(out.drafts()).isEmpty();
        assertThat(out.notes()).containsExactly("There's no lifting session on today to correct.");
    }

    @Test
    void runGarminAlreadySyncedGetsTheNotesInstead() {
        var synced = new Activity("g1", "u", ActivityType.RUN, TODAY, null, "Morning Run", null, null, 2900, 5.04, 150,
                List.of(), List.of(), "garmin", "123", Instant.EPOCH, Instant.EPOCH);
        var out = builder(List.of(synced), List.of()).build(new Extraction(List.of(run(5, "mi", null, "Legs felt heavy"))));
        assertThat(out.drafts()).hasSize(1);
        var d = out.drafts().get(0);
        assertThat(d.action()).isEqualTo(Action.UPDATE);
        assertThat(d.activity().notes()).isEqualTo("Legs felt heavy");
        assertThat(d.activity().distanceMi()).isEqualTo(5.04);
    }

    @Test
    void mealLooksUpEachIngredientAndPrefersPlainFoods() {
        var branded = new FoodResult("off", "9", "Chicken breast strips", "Tyson", null, new Nutrients(150, 20, 2, 6), List.of());
        var plain = new FoodResult("usda", "1", "Chicken, breast, roasted", null, null, new Nutrients(165, 31, 0, 3.6), List.of());
        var rice = new FoodResult("usda", "2", "Rice, white, cooked", null, null, new Nutrients(130, 2.7, 28, 0.3), List.of());
        var out = new DraftBuilder(TODAY, 13, List.of(), NAMES,
                q -> q.contains("rice") ? List.of(rice) : q.contains("chicken") ? List.of(branded, plain) : List.of())
                .build(new Extraction(List.of(food("chicken and rice", null,
                        new Ingredient("chicken breast", "6 oz", 150.0),
                        new Ingredient("white rice", "1 cup", 160.0),
                        new Ingredient("mystery sauce", "", null)))));

        var f = out.drafts().get(0);
        assertThat(f.kind()).isEqualTo(Kind.FOOD);
        assertThat(f.food().meal()).isEqualTo(Meal.LUNCH);
        assertThat(f.food().name()).isEqualTo("Chicken and rice");
        var ings = f.food().ingredients();
        assertThat(ings.get(0).grams()).isEqualTo(170.1); // from "6 oz", not the model's guess
        assertThat(ings.get(0).gramsGuessed()).isFalse();
        assertThat(ings.get(0).options().get(0).name()).isEqualTo("Chicken, breast, roasted");
        assertThat(ings.get(1).grams()).isEqualTo(160);
        assertThat(ings.get(1).gramsGuessed()).isTrue();
        assertThat(ings.get(2).options()).isEmpty();
        assertThat(f.changes()).contains("1 ingredient wasn't found: search for it or remove it");
    }

    @Test
    void exerciseNamesMatchWhatYouAlreadyLog() {
        assertThat(ExerciseNames.match("bench", NAMES).name()).isEqualTo("Bench press");
        assertThat(ExerciseNames.match("incline bench", NAMES).name()).isEqualTo("Incline bench press");
        assertThat(ExerciseNames.match("squats", NAMES).name()).isEqualTo("Back squat");
        assertThat(ExerciseNames.match("pull ups", NAMES).name()).isEqualTo("Pull-ups");
        var fresh = ExerciseNames.match("rdl", NAMES);
        assertThat(fresh.name()).isEqualTo("Romanian deadlift");
        assertThat(fresh.known()).isFalse();
    }

    @Test
    void amountsWithAWeightBecomeGrams() {
        assertThat(DraftBuilder.grams("200 g")).isEqualTo(200);
        assertThat(DraftBuilder.grams("6oz")).isCloseTo(170.1, org.assertj.core.data.Offset.offset(0.01));
        assertThat(DraftBuilder.grams("1 cup")).isNull();
        assertThat(DraftBuilder.miles(10.0, "km")).isEqualTo(6.21);
        assertThat(DraftBuilder.pounds(60.0, "kg")).isEqualTo(132.5);
    }

    @Test
    void longMessagesAreReadOneSentenceAtATime() {
        assertThat(ParseService.sentences("Easy 4 miles, legs heavy. Then bench 3x8 at 185! Lunch was a turkey sandwich."))
                .containsExactly("Easy 4 miles, legs heavy.", "Then bench 3x8 at 185!", "Lunch was a turkey sandwich.");
        assertThat(ParseService.sentences("Ran 5.5 miles. Felt good.\nbench 3x5"))
                .containsExactly("Ran 5.5 miles. Felt good.", "bench 3x5");
        assertThat(ParseService.sentences("  bench 3x8 at 185  ")).containsExactly("bench 3x8 at 185");
    }
}
