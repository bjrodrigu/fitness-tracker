package com.fitnesstracker.journal.demo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.activity.Exercise;
import com.fitnesstracker.journal.activity.ExerciseSet;

/**
 * Makes up 26 weeks of plausible training ending today: a build to a half marathon, a recovery
 * week, then a second build. Every number and note here is synthetic; none of it comes from a
 * real watch or account. The same seed and date always give the same journal.
 */
public final class DemoDataGenerator {

    /** Planned weekly miles, oldest week first. The last entry is the current week. */
    static final int[] WEEKLY_MILES = {
        16, 18, 20, 15, 22, 24, 26, 19, 27, 29, 31, 23, 32, 33, 34, 26,
        28, 22, 24, 10, 18, 21, 24, 19, 26, 28
    };
    static final int RACE_WEEK = 18;
    static final int RECOVERY_WEEK = 19;

    private final Random rnd;
    private final LocalDate today;
    private final LocalDate firstMonday;
    private final List<Activity> out = new ArrayList<>();

    private DemoDataGenerator(long seed, LocalDate today) {
        this.rnd = new Random(seed);
        this.today = today;
        this.firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(WEEKLY_MILES.length - 1L);
    }

    public static List<Activity> generate(long seed, LocalDate today) {
        DemoDataGenerator g = new DemoDataGenerator(seed, today);
        for (int w = 0; w < WEEKLY_MILES.length; w++) {
            g.week(w);
        }
        return g.out.stream().filter(a -> !a.date().isAfter(today)).toList();
    }

    // ---- runs ---------------------------------------------------------------------------------

    /** Easy pace in seconds per mile: about 9:45 at the start, 9:00 by the end. */
    private double easyPace(int week) {
        return 585 - 45.0 * week / (WEEKLY_MILES.length - 1);
    }

    private void week(int w) {
        LocalDate mon = firstMonday.plusWeeks(w);
        int miles = WEEKLY_MILES[w];
        boolean cutback = w != RACE_WEEK && w != RECOVERY_WEEK && w > 0 && miles < WEEKLY_MILES[w - 1] - 3;

        if (w == RACE_WEEK) {
            run(mon.plusDays(1), w, 4.0, Kind.EASY, "Easy 4 with strides", List.of("easy", "race week"),
                    "Four strides at the end. Legs feel springy, which is the point of a taper.");
            run(mon.plusDays(3), w, 3.0, Kind.EASY, "Shakeout 3", List.of("easy", "race week"), null);
            run(mon.plusDays(5), w, 2.0, Kind.EASY, "Pre-race jog", List.of("easy", "race week"),
                    "Ten minutes and a couple of pickups. Laid out kit, set two alarms.");
            race(mon.plusDays(6), w);
            strength(mon, w, Lift.LOWER, true);
            return;
        }
        if (w == RECOVERY_WEEK) {
            run(mon.plusDays(2), w, 3.0, Kind.EASY, "Recovery jog", List.of("easy", "recovery"),
                    "First run since the race. Quads still sore on the downhills. Kept it very slow.");
            run(mon.plusDays(4), w, 3.0, Kind.EASY, "Easy 3", List.of("easy", "recovery"), null);
            run(mon.plusDays(6), w, 4.0, Kind.EASY, "Easy 4", List.of("easy", "recovery"),
                    "Back to normal. Starting the next block on Monday.");
            strength(mon.plusDays(3), w, Lift.UPPER, true);
            return;
        }

        double longRun = round(miles * (w >= 14 && w <= 17 ? 0.34 : 0.3) * 2) / 2.0;
        double workout = round(miles * 0.2 * 2) / 2.0;
        double easyTotal = miles - longRun - workout;
        double easyA = round(easyTotal * 0.4 * 2) / 2.0;
        double easyB = round(easyTotal * 0.3 * 2) / 2.0;
        double easyC = Math.max(2, round((easyTotal - easyA - easyB) * 2) / 2.0);
        List<String> wk = cutback ? List.of("cutback") : List.of();

        strength(mon, w, Lift.LOWER, cutback);
        if (w == 7 || w == 22) {
            run(mon.plusDays(1), w, 3.1, Kind.TIME_TRIAL, "5K time trial", tags("workout", List.of("time trial")),
                    w == 7 ? "Solo on the track. Went out too fast, held on. A baseline for the block."
                            : "Paced it better than last time: even splits, kicked the last 400.");
        } else if (!skip(0.06)) {
            workout(mon.plusDays(1), w, workout, wk);
        }
        if (!skip(0.12)) {
            run(mon.plusDays(2), w, easyA, Kind.EASY, easyTitle(easyA), tags("easy", wk), easyNote());
        }
        if (!skip(0.12)) {
            strength(mon.plusDays(3), w, Lift.UPPER, cutback);
        }
        if (!skip(0.2)) {
            run(mon.plusDays(4), w, easyB, Kind.EASY, easyTitle(easyB), tags("easy", wk), easyNote());
        }
        if (!skip(0.04)) {
            run(mon.plusDays(5), w, longRun, Kind.LONG, "Long run " + fmt(longRun), tags("long", wk), longNote(w));
        }
        if (!skip(0.25)) {
            run(mon.plusDays(6), w, easyC, Kind.EASY, easyC <= 3 ? "Recovery jog" : easyTitle(easyC),
                    tags("easy", wk), rnd.nextDouble() < 0.3 ? easyNote() : null);
        }
    }

    private enum Kind { EASY, LONG, WORKOUT, TIME_TRIAL, RACE }

    private void run(LocalDate date, int week, double miles, Kind kind, String title, List<String> tags, String notes) {
        double pace = switch (kind) {
            case EASY -> easyPace(week) + rnd.nextGaussian() * 9;
            case LONG -> easyPace(week) + 6 + rnd.nextGaussian() * 8;
            case WORKOUT -> easyPace(week) - 70 + rnd.nextGaussian() * 8;
            case TIME_TRIAL -> easyPace(week) - 105;
            case RACE -> 478;
        };
        double distance = round((miles + (kind == Kind.RACE || kind == Kind.TIME_TRIAL ? 0.02 : rnd.nextDouble() * 0.12 - 0.04)) * 100) / 100.0;
        int hr = switch (kind) {
            case EASY -> 141 + rnd.nextInt(9);
            case LONG -> 147 + rnd.nextInt(7);
            case WORKOUT -> 158 + rnd.nextInt(8);
            case TIME_TRIAL -> 171;
            case RACE -> 172;
        };
        int effort = switch (kind) {
            case EASY -> 3 + rnd.nextInt(2);
            case LONG -> 5 + rnd.nextInt(2);
            case WORKOUT -> 7 + rnd.nextInt(2);
            case TIME_TRIAL -> 9;
            case RACE -> 9;
        };
        LocalTime start = date.getDayOfWeek().getValue() >= 6
                ? LocalTime.of(7, 30).plusMinutes(rnd.nextInt(50))
                : rnd.nextDouble() < 0.7 ? LocalTime.of(6, 5).plusMinutes(rnd.nextInt(40))
                        : LocalTime.of(17, 45).plusMinutes(rnd.nextInt(60));
        out.add(new Activity(null, null, ActivityType.RUN, date, start, title, notes, effort,
                (int) Math.round(pace * distance), distance, hr, tags, List.of(), "demo", null, null, null));
    }

    private void workout(LocalDate date, int week, double miles, List<String> extra) {
        String[][] workouts = {
            {"Tempo 3 mi", "Two mile warmup, three at tempo, cooldown. Held it together through the last mile."},
            {"6 × 800m", "Track session. Splits drifted a few seconds on the last two, but recovery jogs stayed honest."},
            {"5 × 1K", "Ran these on the bike path. Wind on the way out made the odd reps harder."},
            {"Progression run", "Started easy and dropped about 15 seconds a mile each mile. Last mile felt like tempo."},
            {"Hills 8 × 60s", "Hill repeats. Drove the arms, jogged down. Calves noticed."},
            {"Cruise intervals 4 × 1 mi", "One minute jog between. Steady, didn't race the first one this time."},
        };
        String[] pick = workouts[(week + rnd.nextInt(2)) % workouts.length];
        String note = rnd.nextDouble() < 0.8 ? pick[1] : null;
        run(date, week, miles, Kind.WORKOUT, pick[0], tags("workout", extra), note);
    }

    private void race(LocalDate date, int week) {
        String notes = """
                Goal was under 1:45. Ran 1:44 and change.

                - Miles 1–3 felt slow on purpose, around 8:05
                - Settled at 7:55 through 10
                - Gel at 4 and 8, water at every station
                - Last 5K hurt, but passed people instead of getting passed

                Next time: start a little closer to the front so the first mile isn't so crowded.""";
        run(date, week, 13.1, Kind.RACE, "Half marathon (race)", List.of("race"), notes);
    }

    private String easyTitle(double miles) {
        return rnd.nextDouble() < 0.15 ? "Easy " + fmt(miles) + " + strides" : "Easy " + fmt(miles);
    }

    private String easyNote() {
        if (rnd.nextDouble() < 0.45) {
            return null;
        }
        String[] notes = {
            "Conversational pace the whole way.",
            "Slept badly, kept it short and slow.",
            "Treadmill, 1% incline. Podcast run.",
            "Ran with the Wednesday group. Ended up a little quicker than planned.",
            "Left shin a bit tight in the first mile, gone by mile two.",
            "Hot one. Took water at the park fountain.",
            "Felt flat. Not every run has to feel good.",
            "Easy loop, legs fresh after the rest day.",
            "Rain the whole time. Shoes still drying.",
            "Kept heart rate under 150, which meant walking the big hill.",
        };
        return notes[rnd.nextInt(notes.length)];
    }

    private String longNote(int week) {
        if (rnd.nextDouble() < 0.3) {
            return null;
        }
        String[] notes = {
            "Practiced fueling: gel at 45 minutes, again at 90.",
            "Last two miles at goal half pace. Felt manageable.",
            "Out-and-back on the trail. Negative split by about a minute.",
            "Tired legs from Thursday. Slowed down instead of cutting it short.",
            "Longest run of the block so far. Ate a real breakfast first, which helped.",
            "Cool morning, ideal conditions. Nothing to report, which is good.",
        };
        return notes[(week + rnd.nextInt(3)) % notes.length];
    }

    // ---- strength -----------------------------------------------------------------------------

    private enum Lift { LOWER, UPPER }

    private void strength(LocalDate date, int week, Lift day, boolean light) {
        // Slow linear progression, about 2.5 lb a week on the main lifts.
        double p = week;
        double factor = light ? 0.88 : 1.0;
        List<Exercise> exercises = new ArrayList<>();
        String title;
        String notes = null;
        if (day == Lift.LOWER) {
            title = light ? "Lower body (light)" : "Lower body";
            exercises.add(main("Back squat", 155 + p * 2.6, 5, 3, factor));
            if (week % 2 == 1) {
                exercises.add(main("Deadlift", 205 + p * 3.2, 5, 1, factor));
            } else {
                exercises.add(main("Romanian deadlift", 135 + p * 2, 8, 3, factor));
            }
            exercises.add(accessory("Walking lunge", 30 + 5 * (week / 6), 10, 3));
            exercises.add(accessory("Calf raise", 0, 15, 3));
            if (!light && rnd.nextDouble() < 0.35) {
                notes = new String[] {
                    "Squats moved well. Kept the bar path over midfoot.",
                    "Heavier than it should have felt the day before a workout. Will swap the order next week.",
                    "Paused the first rep of each squat set to stay honest on depth.",
                    "Short session before work. Skipped the warmup sets past 135 and regretted it.",
                }[rnd.nextInt(4)];
            }
        } else {
            title = light ? "Upper body (light)" : "Upper body";
            exercises.add(main("Bench press", 135 + p * 1.6, 5, 3, factor));
            exercises.add(main("Overhead press", 85 + p * 0.8, 6, 3, factor));
            int pullups = 6 + week / 5;
            exercises.add(new Exercise("Pull-up", List.of(
                    new ExerciseSet(pullups, 0), new ExerciseSet(pullups, 0), new ExerciseSet(Math.max(3, pullups - 2), 0))));
            exercises.add(accessory("Dumbbell row", 50 + 5 * (week / 7), 10, 3));
            if (!light && rnd.nextDouble() < 0.3) {
                notes = new String[] {
                    "Bench felt strong. Last set could have had another rep.",
                    "Shoulders tight from sleeping wrong. Kept the press light.",
                    "Superset rows with pull-ups to get out in 40 minutes.",
                }[rnd.nextInt(3)];
            }
        }
        if (light && notes == null) {
            notes = week == RACE_WEEK ? "Kept it short. Nothing heavy this close to race day."
                    : "Deload week. Same movements, lighter weights.";
        }
        LocalTime start = LocalTime.of(18, 10).plusMinutes(rnd.nextInt(45));
        int minutes = 40 + rnd.nextInt(25);
        List<String> tags = new ArrayList<>(List.of(day == Lift.LOWER ? "lower" : "upper"));
        if (light) {
            tags.add("deload");
        }
        out.add(new Activity(null, null, ActivityType.STRENGTH, date, start, title, notes, light ? 5 : 7 + rnd.nextInt(2),
                minutes * 60, null, null, tags, exercises, "demo", null, null, null));
    }

    private Exercise main(String name, double weight, int reps, int sets, double factor) {
        double w = roundTo5(weight * factor);
        List<ExerciseSet> list = new ArrayList<>();
        for (int i = 0; i < sets; i++) {
            // Sometimes the last set comes up a rep short.
            int r = i == sets - 1 && rnd.nextDouble() < 0.15 ? reps - 1 : reps;
            list.add(new ExerciseSet(r, w));
        }
        return new Exercise(name, list);
    }

    private Exercise accessory(String name, double weight, int reps, int sets) {
        List<ExerciseSet> list = new ArrayList<>();
        for (int i = 0; i < sets; i++) {
            list.add(new ExerciseSet(reps, weight));
        }
        return new Exercise(name, list);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private boolean skip(double probability) {
        return rnd.nextDouble() < probability;
    }

    private static List<String> tags(String first, List<String> extra) {
        List<String> t = new ArrayList<>();
        t.add(first);
        t.addAll(extra);
        return t;
    }

    private static double round(double v) {
        return Math.round(v);
    }

    private static double roundTo5(double v) {
        return Math.round(v / 5) * 5.0;
    }

    private static String fmt(double miles) {
        return miles == Math.floor(miles) ? String.valueOf((int) miles) : String.valueOf(miles);
    }
}
