package com.fitnesstracker.journal.parse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.activity.Exercise;
import com.fitnesstracker.journal.activity.ExerciseSet;
import com.fitnesstracker.journal.foods.FoodResult;
import com.fitnesstracker.journal.nutrition.Meal;
import com.fitnesstracker.journal.parse.ExerciseNames.Match;
import com.fitnesstracker.journal.parse.Extraction.Item;
import com.fitnesstracker.journal.parse.ParseResult.Action;
import com.fitnesstracker.journal.parse.ParseResult.Draft;
import com.fitnesstracker.journal.parse.ParseResult.FoodDraft;
import com.fitnesstracker.journal.parse.ParseResult.IngredientDraft;
import com.fitnesstracker.journal.parse.ParseResult.Kind;

/**
 * Turns what the model read into drafts: matches exercise names, finds the entries a correction
 * is about, adds sets to today's lifting session instead of starting a second one, and looks up
 * each ingredient in the food databases. Plain code, so it's tested without a model.
 */
class DraftBuilder {

    static final double KG_TO_LB = 2.20462;

    /** Food lookups wait on outside services, so they get their own threads rather than the shared pool. */
    private static final ExecutorService LOOKUPS = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "food-lookup");
        t.setDaemon(true);
        return t;
    });
    static final double KM_TO_MI = 0.621371;

    private final LocalDate today;
    private final int hour;
    private final List<Activity> recent;
    private final Map<String, Integer> exerciseCounts;
    private final Function<String, List<FoodResult>> foodSearch;
    private final boolean saysTime;
    private final boolean saysOneMore;

    /** Entries this message touches, keyed by id (or a made-up key for new ones), in order. */
    private final Map<String, Work> work = new LinkedHashMap<>();
    private final List<Draft> food = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private int newCount;

    /** An entry being changed, plus what changed, for the draft card. */
    private static final class Work {
        final Activity original;
        Activity current;
        final List<String> changes = new ArrayList<>();

        Work(Activity original, Activity current) {
            this.original = original;
            this.current = current;
        }
    }

    /**
     * @param recent the person's entries from the last week or so, newest first
     * @param exerciseCounts every exercise name they've logged, with how often
     */
    DraftBuilder(LocalDate today, int hour, List<Activity> recent, Map<String, Integer> exerciseCounts,
            Function<String, List<FoodResult>> foodSearch) {
        this(today, hour, recent, exerciseCounts, foodSearch, null);
    }

    /** @param message what the person wrote, to catch numbers a small model made up */
    DraftBuilder(LocalDate today, int hour, List<Activity> recent, Map<String, Integer> exerciseCounts,
            Function<String, List<FoodResult>> foodSearch, String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        this.saysTime = message == null || TIME.matcher(m).find();
        this.saysOneMore = ONE_MORE.matcher(m).find();
        this.today = today;
        this.hour = hour;
        this.recent = recent;
        this.exerciseCounts = exerciseCounts;
        this.foodSearch = foodSearch;
    }

    record Built(List<Draft> drafts, List<String> notes) {
    }

    Built build(Extraction x) {
        for (Item item : x.items()) {
            if (item == null || item.kind() == null) {
                continue;
            }
            switch (item.kind().toLowerCase(Locale.ROOT)) {
                case "run" -> run(item);
                case "fix_run" -> fixRun(item);
                case "lift" -> lift(item);
                case "fix_set" -> fixSet(item);
                case "food" -> food(item);
                default -> {
                }
            }
        }
        List<Draft> drafts = new ArrayList<>();
        for (Work w : work.values()) {
            if (w.changes.isEmpty()) {
                continue;
            }
            Kind kind = w.current.type() == ActivityType.RUN ? Kind.RUN : Kind.LIFT;
            drafts.add(new Draft(kind, w.original == null ? Action.CREATE : Action.UPDATE, w.current.date(),
                    List.copyOf(w.changes), w.current, null));
        }
        drafts.addAll(food);
        if (drafts.isEmpty() && notes.isEmpty()) {
            notes.add("I couldn't find a run, a lift or something eaten in that. Try naming it, like \"ran 3 miles\".");
        }
        return new Built(drafts, notes);
    }

    // ---- runs ----

    private void run(Item item) {
        LocalDate date = date(item);
        Double miles = miles(item.distance(), item.distanceUnit());
        Integer seconds = seconds(item.minutes());
        String said = trimToNull(item.notes());

        // A run Garmin already synced: add the notes to it rather than logging it twice.
        Work synced = findRun(date, r -> ("garmin".equals(r.source()) || "import".equals(r.source()))
                && (miles == null || r.distanceMi() == null || Math.abs(r.distanceMi() - miles) <= Math.max(0.3, miles * 0.15)));
        if (synced != null) {
            Activity a = synced.current;
            String notesNow = join(a.notes(), said);
            Integer effort = a.effort() != null ? a.effort() : effort(item.effort());
            synced.current = copyRun(a, a.distanceMi(), a.durationSec(), notesNow, effort);
            synced.changes.add("Adds to the " + fmtMi(a.distanceMi()) + " run already logged from " + label(a.source()));
            if (said != null) {
                synced.changes.add("Notes: " + said);
            }
            if (effort != null && a.effort() == null) {
                synced.changes.add("Effort " + effort + "/10");
            }
            return;
        }
        String title = trimToNull(item.title()) != null ? ExerciseNames.titleCase(item.title())
                : miles != null ? fmtMi(miles) + " run" : "Run";
        Activity a = new Activity(null, null, ActivityType.RUN, date, null, cap(title, 120), said,
                effort(item.effort()), seconds, miles, null, List.of(), List.of(), "parsed", null, null, null);
        Work w = put("new-" + (newCount++), new Work(null, a));
        w.changes.add(describeRun(miles, seconds));
        if (said != null) {
            w.changes.add("Notes: " + said);
        }
        if (a.effort() != null) {
            w.changes.add("Effort " + a.effort() + "/10");
        }
    }

    private void fixRun(Item item) {
        LocalDate date = date(item);
        Work w = findRun(date, r -> true);
        if (w == null) {
            if (item.distance() != null || item.minutes() != null) {
                // "Yesterday I ran 8 miles" read as a correction: there's nothing to correct, so log it.
                run(item);
            } else {
                notes.add("There's no run on " + day(date) + " to correct.");
            }
            return;
        }
        Activity a = w.current;
        Double miles = miles(item.distance(), item.distanceUnit());
        Integer seconds = seconds(item.minutes());
        if (miles == null && seconds == null) {
            notes.add("I couldn't tell what to change on the " + day(date) + " run.");
            return;
        }
        if (miles != null) {
            w.changes.add("Distance: " + fmtMi(a.distanceMi()) + " → " + fmtMi(miles));
        }
        if (seconds != null) {
            w.changes.add("Time: " + fmtTime(a.durationSec()) + " → " + fmtTime(seconds));
        }
        w.current = copyRun(a, miles != null ? miles : a.distanceMi(), seconds != null ? seconds : a.durationSec(),
                a.notes(), a.effort());
    }

    /** The latest run that day, from this message first, then from the journal. */
    private Work findRun(LocalDate date, java.util.function.Predicate<Activity> ok) {
        List<Work> mine = new ArrayList<>(work.values());
        java.util.Collections.reverse(mine);
        for (Work w : mine) {
            if (w.current.type() == ActivityType.RUN && w.current.date().equals(date) && ok.test(w.current)) {
                return w;
            }
        }
        for (Activity r : recent) {
            if (r.type() == ActivityType.RUN && r.date().equals(date) && !work.containsKey(r.id()) && ok.test(r)) {
                return put(r.id(), new Work(r, r));
            }
        }
        return null;
    }

    // ---- lifting ----

    private void lift(Item item) {
        LocalDate date = date(item);
        Work w = session(date, true);
        Match m = ExerciseNames.match(Objects.requireNonNullElse(item.exercise(), ""), exerciseCounts);
        List<ExerciseSet> added = new ArrayList<>();
        boolean noWeight = false;
        // "One more set": small models tend to give the day's new total instead of 1.
        boolean single = saysOneMore && item.sets() != null && item.sets().size() == 1;
        if (item.sets() != null) {
            for (var g : item.sets()) {
                if (g == null || g.reps() == null) {
                    continue;
                }
                int count = single ? 1 : clamp(g.count() == null ? 1 : g.count(), 1, 20);
                if (g.weight() == null) {
                    noWeight = true;
                }
                ExerciseSet s = new ExerciseSet(clamp(g.reps(), 0, 1000), pounds(g.weight(), g.weightUnit()));
                for (int i = 0; i < count; i++) {
                    added.add(s);
                }
            }
        }
        if (added.isEmpty()) {
            notes.add("I didn't catch the sets and reps for " + m.name() + ". Try \"3x8 at 135\".");
            return;
        }
        List<Exercise> exercises = new ArrayList<>(w.current.exercises());
        int at = indexOf(exercises, m.name());
        if (at >= 0) {
            List<ExerciseSet> sets = new ArrayList<>(exercises.get(at).sets());
            sets.addAll(added);
            exercises.set(at, new Exercise(exercises.get(at).name(), sets));
        } else {
            exercises.add(new Exercise(m.name(), added));
        }
        w.current = copyLift(w.current, exercises);
        w.changes.add(m.name() + ": " + describeSets(added) + (m.known() ? "" : " (new exercise)"));
        if (noWeight) {
            w.changes.add("No weight was given for " + m.name() + ", so it's saved as bodyweight");
        }
    }

    private void fixSet(Item item) {
        LocalDate date = date(item);
        Work w = session(date, false);
        if (w == null) {
            notes.add("There's no lifting session on " + day(date) + " to correct.");
            return;
        }
        List<Exercise> exercises = new ArrayList<>(w.current.exercises());
        Match m = ExerciseNames.match(Objects.requireNonNullElse(item.exercise(), ""), namesIn(exercises));
        int at = item.exercise() == null || item.exercise().isBlank() ? exercises.size() - 1 : indexOf(exercises, m.name());
        if (at < 0 || exercises.get(at).sets().isEmpty()) {
            notes.add("I couldn't find " + m.name() + " on " + day(date) + " to correct.");
            return;
        }
        Exercise ex = exercises.get(at);
        List<ExerciseSet> sets = new ArrayList<>(ex.sets());
        int n = item.setNumber() == null || item.setNumber() <= 0 || item.setNumber() > sets.size()
                ? sets.size() : item.setNumber();
        ExerciseSet old = sets.get(n - 1);
        ExerciseSet now = new ExerciseSet(item.reps() != null ? clamp(item.reps(), 0, 1000) : old.reps(),
                item.weight() != null ? pounds(item.weight(), item.weightUnit()) : old.weightLb());
        if (now.equals(old)) {
            notes.add("That's already what " + ex.name() + " set " + n + " says.");
            return;
        }
        sets.set(n - 1, now);
        exercises.set(at, new Exercise(ex.name(), sets));
        w.current = copyLift(w.current, exercises);
        w.changes.add(ex.name() + " set " + n + ": " + describeSet(old) + " → " + describeSet(now));
    }

    /** The day's lifting session: one from this message, else the latest in the journal, else a new one. */
    private Work session(LocalDate date, boolean create) {
        for (Work w : work.values()) {
            if (w.current.type() == ActivityType.STRENGTH && w.current.date().equals(date)) {
                return w;
            }
        }
        for (Activity a : recent) {
            if (a.type() == ActivityType.STRENGTH && a.date().equals(date)) {
                return put(a.id(), new Work(a, a));
            }
        }
        if (!create) {
            return null;
        }
        Activity a = new Activity(null, null, ActivityType.STRENGTH, date, null, "Strength", null, null, null, null,
                null, List.of(), List.of(), "parsed", null, null, null);
        return put("new-" + (newCount++), new Work(null, a));
    }

    private static int indexOf(List<Exercise> exercises, String name) {
        String k = ExerciseNames.key(name);
        for (int i = 0; i < exercises.size(); i++) {
            if (ExerciseNames.key(exercises.get(i).name()).equals(k)) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Integer> namesIn(List<Exercise> exercises) {
        Map<String, Integer> m = new LinkedHashMap<>();
        exercises.forEach(e -> m.merge(e.name(), 1, Integer::sum));
        return m;
    }

    // ---- food ----

    private void food(Item item) {
        LocalDate date = date(item);
        String dish = trimToNull(item.dish());
        List<Extraction.Ingredient> said = item.ingredients() == null ? List.of()
                : item.ingredients().stream().filter(i -> i != null && trimToNull(i.food()) != null).limit(12).toList();
        if (said.isEmpty()) {
            if (dish == null) {
                return;
            }
            said = List.of(new Extraction.Ingredient(dish, null, null));
        }
        if (dish == null) {
            dish = ExerciseNames.titleCase(said.get(0).food());
        }
        // Look every ingredient up at once; each lookup waits on two outside services.
        List<CompletableFuture<List<FoodResult>>> lookups = said.stream()
                .map(i -> CompletableFuture.supplyAsync(() -> safeSearch(i.food()), LOOKUPS)
                        .completeOnTimeout(List.of(), 15, TimeUnit.SECONDS))
                .toList();
        List<IngredientDraft> ingredients = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int missing = 0;
        for (int i = 0; i < said.size(); i++) {
            var ing = said.get(i);
            Double fromAmount = grams(ing.amount());
            double grams = fromAmount != null ? fromAmount
                    : ing.grams() != null && ing.grams() > 0 ? Math.min(ing.grams(), 2000) : 100;
            boolean guessed = fromAmount == null;
            List<FoodResult> options = rank(ing.food(), lookups.get(i).join());
            ingredients.add(new IngredientDraft(ing.food().trim(), trimToNull(ing.amount()), round1(grams), guessed, options));
            if (options.isEmpty()) {
                missing++;
            }
            lines.add(ing.food().trim() + ", " + Math.round(grams) + " g" + (options.isEmpty() ? " (not found)" : ""));
        }
        if (missing > 0) {
            lines.add(missing == 1 ? "1 ingredient wasn't found: search for it or remove it"
                    : missing + " ingredients weren't found: search for them or remove them");
        }
        food.add(new Draft(Kind.FOOD, Action.CREATE, date, lines, null,
                new FoodDraft(meal(item.meal()), cap(ExerciseNames.titleCase(dish), 120), ingredients)));
    }

    private List<FoodResult> safeSearch(String q) {
        try {
            return foodSearch.apply(q.trim());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static final Pattern WORD = Pattern.compile("[a-z]+");

    /** Best three matches: most of the words you said, plain foods before branded ones. */
    static List<FoodResult> rank(String query, List<FoodResult> results) {
        List<String> want = words(query);
        record Scored(FoodResult f, double score) {
        }
        return results.stream()
                .filter(f -> f.per100g() != null && f.per100g().kcal() > 0)
                .map(f -> {
                    List<String> have = words(f.name() + " " + (f.brand() == null ? "" : f.brand()));
                    long hit = want.stream().filter(w -> have.stream().anyMatch(h -> h.startsWith(w) || w.startsWith(h))).count();
                    double score = want.isEmpty() ? 0 : (double) hit / want.size();
                    // Plain foods with short names beat branded products and long variations:
                    // "bread" should find bread, not "Bread, fruit" or a brand of naan.
                    score += "usda".equals(f.source()) ? 0.25 : 0;
                    score -= f.brand() != null && !f.brand().isBlank() ? 0.15 : 0;
                    score -= 0.07 * Math.max(0, have.size() - want.size());
                    return new Scored(new FoodResult(f.source(), f.id(), f.name(), f.brand(), f.barcode(), f.per100g(),
                            List.of()), score);
                })
                .filter(s -> s.score() > 0.2)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(3)
                .map(Scored::f)
                .toList();
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String w = m.group();
            if (w.length() > 2 && !List.of("and", "with", "the", "raw", "cooked").contains(w)) {
                out.add(w.endsWith("es") && w.length() > 4 ? w.substring(0, w.length() - 2)
                        : w.endsWith("s") && w.length() > 3 ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }

    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(g|grams?|kg|oz|ounces?|lbs?|pounds?)\\b");

    /** Grams from an amount that says its weight ("200 g", "6 oz"); null for cups, pieces and so on. */
    static Double grams(String amount) {
        if (amount == null) {
            return null;
        }
        Matcher m = AMOUNT.matcher(amount.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return null;
        }
        double n = Double.parseDouble(m.group(1));
        String u = m.group(2);
        double g = u.startsWith("kg") ? n * 1000 : u.startsWith("o") ? n * 28.3495 : u.startsWith("l") || u.startsWith("p") ? n * 453.592 : n;
        return g > 0 && g <= 5000 ? g : null;
    }

    private Meal meal(String said) {
        if (said != null) {
            try {
                return Meal.valueOf(said.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to the time of day
            }
        }
        return hour < 11 ? Meal.BREAKFAST : hour < 16 ? Meal.LUNCH : hour < 21 ? Meal.DINNER : Meal.SNACK;
    }

    // ---- helpers ----

    private Work put(String key, Work w) {
        work.put(key, w);
        return w;
    }

    private LocalDate date(Item item) {
        int off = item.dayOffset() == null ? 0 : clamp(item.dayOffset(), -14, 0);
        return today.plusDays(off);
    }

    private String day(LocalDate d) {
        if (d.equals(today)) {
            return "today";
        }
        if (d.equals(today.minusDays(1))) {
            return "yesterday";
        }
        return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.US);
    }

    static Double miles(Double distance, String unit) {
        if (distance == null || distance <= 0) {
            return null;
        }
        String u = unit == null ? "mi" : unit.toLowerCase(Locale.ROOT);
        double mi = u.startsWith("k") ? distance * KM_TO_MI : u.equals("m") ? distance / 1609.344 : distance;
        return mi > 200 ? null : Math.round(mi * 100) / 100.0;
    }

    private static final Pattern ONE_MORE = Pattern.compile("\\b(one|1|an?) (more|extra|last) set\\b|\\banother set\\b");

    private static final Pattern TIME = Pattern.compile("\\d\\s*(min|m\\b|hr|hour|h\\b|sec|s\\b)|\\d:\\d\\d");

    /** Run time in seconds; dropped when the message never mentions a time (small models invent one). */
    private Integer seconds(Double minutes) {
        if (!saysTime) {
            return null;
        }
        return minutes == null || minutes <= 0 || minutes > 60 * 48 ? null : (int) Math.round(minutes * 60);
    }

    static double pounds(Double weight, String unit) {
        if (weight == null || weight <= 0) {
            return 0;
        }
        double lb = "kg".equalsIgnoreCase(unit) ? weight * KG_TO_LB : weight;
        return Math.min(1000, Math.round(lb * 2) / 2.0);
    }

    private static Integer effort(Integer e) {
        return e == null ? null : clamp(e, 1, 10);
    }

    private static Activity copyRun(Activity a, Double miles, Integer seconds, String notes, Integer effort) {
        return new Activity(a.id(), null, a.type(), a.date(), a.startTime(), a.title(), notes, effort, seconds, miles,
                a.avgHeartRate(), a.tags(), a.exercises(), a.source(), a.externalId(), a.createdAt(), a.updatedAt());
    }

    private static Activity copyLift(Activity a, List<Exercise> exercises) {
        return new Activity(a.id(), null, a.type(), a.date(), a.startTime(), a.title(), a.notes(), a.effort(),
                a.durationSec(), a.distanceMi(), a.avgHeartRate(), a.tags(), exercises, a.source(), a.externalId(),
                a.createdAt(), a.updatedAt());
    }

    private static String describeRun(Double miles, Integer seconds) {
        if (miles == null && seconds == null) {
            return "Run with no distance or time";
        }
        StringBuilder s = new StringBuilder(miles != null ? fmtMi(miles) : "Run");
        if (seconds != null) {
            s.append(" in ").append(fmtTime(seconds));
            if (miles != null && miles > 0) {
                s.append(" (").append(fmtTime((int) Math.round(seconds / miles))).append(" /mi)");
            }
        }
        return s.toString();
    }

    /** "3 × 8 at 185 lb, 1 × 6 at 185 lb": repeated sets grouped. */
    static String describeSets(List<ExerciseSet> sets) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < sets.size()) {
            int j = i;
            while (j < sets.size() && sets.get(j).equals(sets.get(i))) {
                j++;
            }
            parts.add((j - i) + " × " + describeSet(sets.get(i)));
            i = j;
        }
        return String.join(", ", parts);
    }

    private static String describeSet(ExerciseSet s) {
        return s.reps() + (s.weightLb() > 0 ? " at " + fmtNum(s.weightLb()) + " lb" : " reps");
    }

    private static String fmtMi(Double mi) {
        return mi == null ? "no distance" : fmtNum(Math.round(mi * 100) / 100.0) + " mi";
    }

    private static String fmtNum(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String fmtTime(Integer sec) {
        if (sec == null) {
            return "no time";
        }
        int h = sec / 3600;
        int m = sec % 3600 / 60;
        int s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private static String label(String source) {
        return "garmin".equalsIgnoreCase(source) ? "Garmin" : source;
    }

    private static String join(String a, String b) {
        if (b == null) {
            return a;
        }
        return a == null || a.isBlank() ? b : a + "\n\n" + b;
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cap(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }
}
