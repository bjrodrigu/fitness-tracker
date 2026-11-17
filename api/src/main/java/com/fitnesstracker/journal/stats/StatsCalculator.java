package com.fitnesstracker.journal.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.activity.Exercise;
import com.fitnesstracker.journal.activity.ExerciseSet;

/** Pure functions over a list of entries. No database, so it's easy to test. */
public final class StatsCalculator {

    static final double MILE = 1.0;
    static final double FIVE_K = 3.10686;
    static final double TEN_K = 6.21371;
    static final double HALF = 13.1094;
    static final double MARATHON = 26.2188;

    private StatsCalculator() {
    }

    public static Stats compute(List<Activity> all, LocalDate today, int weekCount, int dayCount) {
        List<Activity> past = all.stream().filter(a -> !a.date().isAfter(today)).toList();
        List<Activity> runs = past.stream().filter(StatsCalculator::isRun).toList();

        List<Stats.Week> weeks = weeks(past, today, Math.max(weekCount, 5));
        return new Stats(
                today,
                summary(past, runs, weeks, today),
                weeks.subList(weeks.size() - weekCount, weeks.size()),
                bestEfforts(runs),
                runs.stream().max(Comparator.comparingDouble(Activity::distanceMi)).map(StatsCalculator::ref)
                        .orElse(null),
                lifts(past, today),
                days(past, today, dayCount));
    }

    static boolean isRun(Activity a) {
        return a.type() == ActivityType.RUN && a.distanceMi() != null && a.distanceMi() > 0;
    }

    static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static List<Stats.Week> weeks(List<Activity> past, LocalDate today, int count) {
        LocalDate first = weekStart(today).minusWeeks(count - 1L);
        List<Stats.Week> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate start = first.plusWeeks(i);
            LocalDate end = start.plusDays(6);
            double miles = 0;
            int runCount = 0;
            int sec = 0;
            int pacedSec = 0;
            double pacedMiles = 0;
            double longRun = 0;
            int strength = 0;
            double volume = 0;
            for (Activity a : past) {
                if (a.date().isBefore(start) || a.date().isAfter(end)) {
                    continue;
                }
                if (isRun(a)) {
                    miles += a.distanceMi();
                    runCount++;
                    longRun = Math.max(longRun, a.distanceMi());
                    if (a.durationSec() != null && a.durationSec() > 0) {
                        sec += a.durationSec();
                        pacedSec += a.durationSec();
                        pacedMiles += a.distanceMi();
                    }
                } else if (a.type() == ActivityType.STRENGTH) {
                    strength++;
                    volume += a.volumeLb();
                }
            }
            Integer pace = pacedMiles > 0 ? (int) Math.round(pacedSec / pacedMiles) : null;
            out.add(new Stats.Week(start, round1(miles), runCount, sec, pace, round1(longRun), strength,
                    Math.round(volume)));
        }
        return out;
    }

    static Stats.Summary summary(List<Activity> past, List<Activity> runs, List<Stats.Week> weeks, LocalDate today) {
        Stats.Week thisWeek = weeks.get(weeks.size() - 1);
        Stats.Week lastWeek = weeks.get(weeks.size() - 2);
        double last4 = weeks.subList(weeks.size() - 5, weeks.size() - 1).stream()
                .mapToDouble(Stats.Week::runMiles).sum() / 4.0;

        List<Activity> yearRuns = runs.stream().filter(a -> a.date().getYear() == today.getYear()).toList();
        double yearMiles = yearRuns.stream().mapToDouble(Activity::distanceMi).sum();

        // Count back from the current week (or last week, if nothing is logged yet this week).
        int streak = 0;
        LocalDate wk = weekStart(today);
        if (!hasActivityInWeek(past, wk)) {
            wk = wk.minusWeeks(1);
        }
        while (hasActivityInWeek(past, wk)) {
            streak++;
            wk = wk.minusWeeks(1);
        }
        return new Stats.Summary(thisWeek.runMiles(), lastWeek.runMiles(), round1(last4), round1(yearMiles),
                yearRuns.size(), thisWeek.strengthSessions(), streak);
    }

    private static boolean hasActivityInWeek(List<Activity> past, LocalDate start) {
        LocalDate end = start.plusDays(6);
        return past.stream().anyMatch(a -> !a.date().isBefore(start) && !a.date().isAfter(end));
    }

    static List<Stats.BestEffort> bestEfforts(List<Activity> runs) {
        Map<String, Double> targets = new LinkedHashMap<>();
        targets.put("1 mile", MILE);
        targets.put("5K", FIVE_K);
        targets.put("10K", TEN_K);
        targets.put("Half marathon", HALF);
        targets.put("Marathon", MARATHON);

        List<Stats.BestEffort> out = new ArrayList<>();
        targets.forEach((label, d) -> runs.stream()
                // Runs between the distance and 8% over it. A 6.5 mi run counts toward 10K, a 3 mi run doesn't count toward 5K.
                .filter(r -> r.paceSecPerMi() != null && r.distanceMi() >= d * 0.995 && r.distanceMi() <= d * 1.08)
                .min(Comparator.comparingInt(Activity::paceSecPerMi))
                .ifPresent(r -> out.add(new Stats.BestEffort(label, d, (int) Math.round(r.paceSecPerMi() * d),
                        r.paceSecPerMi(), ref(r)))));
        return out;
    }

    static double e1rm(ExerciseSet s) {
        if (s.reps() <= 0 || s.weightLb() <= 0) {
            return 0;
        }
        return s.reps() == 1 ? s.weightLb() : s.weightLb() * (1 + s.reps() / 30.0);
    }

    static List<Stats.LiftProgress> lifts(List<Activity> past, LocalDate today) {
        // Group by exercise name, ignoring case and extra spaces; keep the first spelling seen.
        Map<String, String> displayName = new LinkedHashMap<>();
        Map<String, TreeMap<LocalDate, Stats.LiftSession>> byLift = new LinkedHashMap<>();
        Map<String, Integer> bestReps = new LinkedHashMap<>();
        Map<String, Boolean> anyWeight = new LinkedHashMap<>();

        past.stream()
                .filter(a -> a.type() == ActivityType.STRENGTH)
                .sorted(Comparator.comparing(Activity::date))
                .forEach(a -> {
                    for (Exercise ex : a.exercises()) {
                        if (ex.sets().isEmpty()) {
                            continue;
                        }
                        String key = ex.name().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                        displayName.putIfAbsent(key, ex.name().trim());
                        Optional<ExerciseSet> top = ex.sets().stream()
                                .max(Comparator.comparingDouble(StatsCalculator::e1rm)
                                        .thenComparingDouble(ExerciseSet::weightLb)
                                        .thenComparingInt(ExerciseSet::reps));
                        ExerciseSet t = top.get();
                        int reps = ex.sets().stream().mapToInt(ExerciseSet::reps).max().orElse(0);
                        bestReps.merge(key, reps, Math::max);
                        anyWeight.merge(key, ex.sets().stream().anyMatch(s -> s.weightLb() > 0), Boolean::logicalOr);
                        Stats.LiftSession session = new Stats.LiftSession(a.id(), a.date(), t.weightLb(), t.reps(),
                                round1(e1rm(t)));
                        // Two entries with the same lift on one day: keep the better one.
                        byLift.computeIfAbsent(key, k -> new TreeMap<>())
                                .merge(a.date(), session, (x, y) -> x.e1rmLb() >= y.e1rmLb() ? x : y);
                    }
                });

        LocalDate eightWeeksAgo = today.minusWeeks(8);
        List<Stats.LiftProgress> out = new ArrayList<>();
        byLift.forEach((key, map) -> {
            List<Stats.LiftSession> sessions = List.copyOf(map.values());
            double best = sessions.stream().mapToDouble(Stats.LiftSession::e1rmLb).max().orElse(0);
            double latest = sessions.get(sessions.size() - 1).e1rmLb();
            double change = sessions.stream().filter(s -> !s.date().isBefore(eightWeeksAgo)).findFirst()
                    .map(first -> latest - first.e1rmLb()).orElse(0.0);
            out.add(new Stats.LiftProgress(displayName.get(key), !anyWeight.get(key), sessions, best, latest,
                    round1(change), bestReps.get(key)));
        });
        // Most-logged lifts first.
        out.sort(Comparator.comparingInt((Stats.LiftProgress l) -> l.sessions().size()).reversed());
        return out;
    }

    static List<Stats.Day> days(List<Activity> past, LocalDate today, int count) {
        LocalDate first = today.minusDays(count - 1L);
        TreeMap<LocalDate, double[]> miles = new TreeMap<>();
        TreeMap<LocalDate, Boolean> strength = new TreeMap<>();
        for (Activity a : past) {
            if (a.date().isBefore(first)) {
                continue;
            }
            if (isRun(a)) {
                miles.computeIfAbsent(a.date(), d -> new double[1])[0] += a.distanceMi();
            } else if (a.type() == ActivityType.STRENGTH) {
                strength.put(a.date(), true);
            }
        }
        List<Stats.Day> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate d = first.plusDays(i);
            out.add(new Stats.Day(d, round1(miles.getOrDefault(d, new double[1])[0]), strength.getOrDefault(d, false)));
        }
        return out;
    }

    static Stats.RunRef ref(Activity a) {
        return new Stats.RunRef(a.id(), a.date(), a.title(), a.distanceMi(),
                a.durationSec() == null ? 0 : a.durationSec());
    }

    static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
