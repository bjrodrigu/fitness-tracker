package com.fitnesstracker.journal.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.activity.Exercise;
import com.fitnesstracker.journal.activity.ExerciseSet;
import com.fitnesstracker.journal.foods.FoodResult;
import com.fitnesstracker.journal.foods.FoodResult.Nutrients;
import com.fitnesstracker.journal.nutrition.Meal;
import com.fitnesstracker.journal.parse.DraftBuilder.Built;
import com.fitnesstracker.journal.parse.ParseResult.Draft;
import com.fitnesstracker.journal.parse.ParseResult.Kind;

/**
 * Sample messages run through the real model, to see how well it reads them. Needs Ollama
 * running, so it only runs when asked:
 *
 * PARSE_EVAL=1 ./mvnw test -Dtest=ParseEvalTest
 *
 * Set OLLAMA_MODEL to try another model.
 */
@EnabledIfEnvironmentVariable(named = "PARSE_EVAL", matches = "1")
class ParseEvalTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    static final Map<String, Integer> NAMES = Map.of("Bench press", 20, "Back squat", 15, "Deadlift", 8,
            "Pull-ups", 6, "Overhead press", 5);
    static final Activity TODAYS_LIFT = new Activity("s1", "u", ActivityType.STRENGTH, TODAY, null, "Strength", null,
            null, null, null, null, List.of(), List.of(new Exercise("Bench press",
                    List.of(new ExerciseSet(8, 185), new ExerciseSet(8, 185), new ExerciseSet(8, 185)))),
            "manual", null, null, null);
    static final Activity TODAYS_RUN = new Activity("r1", "u", ActivityType.RUN, TODAY, null, "Run", null, null, 2700,
            5.0, null, List.of(), List.of(), "manual", null, null, null);

    record Case(String message, List<Activity> recent, Predicate<Built> ok) {
    }

    static Draft only(Built b, Kind k) {
        var ds = b.drafts().stream().filter(d -> d.kind() == k).toList();
        return ds.size() == 1 ? ds.get(0) : null;
    }

    static Exercise ex(Draft d, String name) {
        return d == null ? null : d.activity().exercises().stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    static boolean sets(Exercise e, int count, int reps, double lb) {
        return e != null && e.sets().size() == count && e.sets().stream().allMatch(s -> s.reps() == reps && s.weightLb() == lb);
    }

    static final List<Case> CASES = List.of(
            new Case("ran 5 miles this morning, felt great", List.of(), b -> {
                var r = only(b, Kind.RUN);
                return r != null && java.util.Objects.equals(r.activity().distanceMi(), 5.0) && r.activity().notes() != null;
            }),
            new Case("10k in 52 minutes", List.of(), b -> {
                var r = only(b, Kind.RUN);
                return r != null && java.util.Objects.equals(r.activity().distanceMi(), 6.21) && java.util.Objects.equals(r.activity().durationSec(), 3120);
            }),
            new Case("bench 3x8 at 185", List.of(), b -> sets(ex(only(b, Kind.LIFT), "Bench press"), 3, 8, 185)),
            new Case("squats 5x5 225 then deadlift 3 sets of 5 at 275", List.of(), b -> {
                var l = only(b, Kind.LIFT);
                return sets(ex(l, "Back squat"), 5, 5, 225) && sets(ex(l, "Deadlift"), 3, 5, 275);
            }),
            new Case("did 3 sets of 10 pull ups", List.of(), b -> sets(ex(only(b, Kind.LIFT), "Pull-ups"), 3, 10, 0)),
            new Case("ohp 4x6 at 60kg", List.of(), b -> sets(ex(only(b, Kind.LIFT), "Overhead press"), 4, 6, 132.5)),
            new Case("easy 3 mile shakeout then bench 3x5 at 205", List.of(), b -> {
                var r = only(b, Kind.RUN);
                return r != null && java.util.Objects.equals(r.activity().distanceMi(), 3.0) && sets(ex(only(b, Kind.LIFT), "Bench press"), 3, 5, 205);
            }),
            new Case("actually the last bench set was only 6 reps", List.of(TODAYS_LIFT), b -> {
                var e = ex(only(b, Kind.LIFT), "Bench press");
                return e != null && e.sets().size() == 3 && e.sets().get(2).reps() == 6 && e.sets().get(0).reps() == 8;
            }),
            new Case("one more set of bench, 8 at 185", List.of(TODAYS_LIFT),
                    b -> sets(ex(only(b, Kind.LIFT), "Bench press"), 4, 8, 185)),
            new Case("oops the run was 5.5 miles not 5", List.of(TODAYS_RUN), b -> {
                var r = only(b, Kind.RUN);
                return r != null && r.activity().id().equals("r1") && java.util.Objects.equals(r.activity().distanceMi(), 5.5);
            }),
            new Case("yesterday I ran 8 miles", List.of(), b -> {
                var r = only(b, Kind.RUN);
                return r != null && r.date().equals(TODAY.minusDays(1)) && java.util.Objects.equals(r.activity().distanceMi(), 8.0);
            }),
            new Case("had a banana for breakfast", List.of(), b -> {
                var f = only(b, Kind.FOOD);
                return f != null && f.food().meal() == Meal.BREAKFAST && f.food().ingredients().size() == 1;
            }),
            new Case("made a chicken burrito bowl for dinner with rice, black beans, chicken, cheese and salsa", List.of(),
                    b -> {
                        var f = only(b, Kind.FOOD);
                        return f != null && f.food().meal() == Meal.DINNER && f.food().ingredients().size() >= 4;
                    }),
            new Case("lunch was 2 eggs and toast with butter", List.of(), b -> {
                var f = only(b, Kind.FOOD);
                return f != null && f.food().meal() == Meal.LUNCH && f.food().ingredients().size() >= 2;
            }),
            new Case("tempo run 6 miles in 45 min, then lunch was a turkey sandwich", List.of(), b -> {
                var r = only(b, Kind.RUN);
                return r != null && java.util.Objects.equals(r.activity().durationSec(), 2700) && only(b, Kind.FOOD) != null;
            }),
            new Case("nothing today, rest day", List.of(), b -> b.drafts().isEmpty()));

    @Test
    void sampleMessages() {
        String url = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434");
        String name = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:3b");
        var model = new OllamaModel(url, name, new ObjectMapper().findAndRegisterModules());
        var anyFood = List.of(new FoodResult("usda", "1", "Food", null, null, new Nutrients(100, 5, 10, 3), List.of()));

        model.extract("warm up", "Today is Tuesday, 2026-11-17."); // load the model so timings are fair
        List<String> failed = new ArrayList<>();
        long total = 0;
        for (Case c : CASES) {
            long t = System.nanoTime();
            Extraction x = model.extract(c.message(), ParseService.context(TODAY, NAMES, c.recent()));
            long ms = (System.nanoTime() - t) / 1_000_000;
            total += ms;
            // Food lookups always "find" something, so only the model's reading is judged.
            Built b = new DraftBuilder(TODAY, 12, c.recent(), NAMES, q -> anyFood, c.message()).build(x);
            boolean ok = c.ok().test(b);
            System.out.printf("%s %5d ms  %s%n", ok ? "PASS" : "FAIL", ms, c.message());
            if (!ok) {
                failed.add(c.message());
                System.out.println("       model said: " + x);
                b.drafts().forEach(d -> System.out.println("       draft: " + d.changes()));
            }
        }
        System.out.printf("%s: %d of %d passed, %d ms per message on average%n", name,
                CASES.size() - failed.size(), CASES.size(), total / CASES.size());
        assertThat(CASES.size() - failed.size()).as("passed").isGreaterThanOrEqualTo(CASES.size() * 3 / 4);
    }
}
