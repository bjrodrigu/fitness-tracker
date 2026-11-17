package com.fitnesstracker.journal.nutrition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Daily calorie and macro totals, weight with a 7-day average, and how the goal is going.
 * Pure functions over lists, so it's easy to test.
 */
public final class Progress {

    static final int TREND_DAYS = 7;
    static final int RATE_DAYS = 28;

    private Progress() {
    }

    public record Day(LocalDate date, double calories, double proteinG, double carbsG, double fatG, int entries) {
    }

    /** trendLb is the average of weigh-ins in the 7 days ending on that date. */
    public record WeightPoint(LocalDate date, double weightLb, double trendLb) {
    }

    public record Summary(
            Double currentLb,
            Double startLb,
            Double changeLb,
            /** Change per week over the last 4 weeks of the trend; negative is losing. */
            Double weeklyRateLb,
            Double targetLb,
            Double remainingLb,
            /** When the target is reached at the current rate; null if not heading there. */
            LocalDate projectedDate,
            Integer avgCalories7,
            int daysLogged7) {
    }

    public record Report(LocalDate asOf, NutritionGoal goal, List<Day> days, List<WeightPoint> weights, Summary summary) {
    }

    public static Report compute(List<FoodEntry> food, List<WeightEntry> weights, NutritionGoal goal, LocalDate today,
            int days) {
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, List<FoodEntry>> byDay = food.stream().collect(Collectors.groupingBy(FoodEntry::date));
        List<Day> dayList = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            List<FoodEntry> es = byDay.getOrDefault(d, List.of());
            dayList.add(new Day(d,
                    round1(es.stream().mapToDouble(FoodEntry::totalCalories).sum()),
                    round1(es.stream().mapToDouble(FoodEntry::totalProteinG).sum()),
                    round1(es.stream().mapToDouble(FoodEntry::totalCarbsG).sum()),
                    round1(es.stream().mapToDouble(FoodEntry::totalFatG).sum()),
                    es.size()));
        }

        List<WeightPoint> points = trend(weights);
        List<WeightPoint> shown = points.stream().filter(p -> !p.date().isBefore(from)).toList();

        List<Day> last7 = dayList.subList(Math.max(0, dayList.size() - 7), dayList.size()).stream()
                .filter(d -> d.entries() > 0).toList();
        Integer avg7 = last7.isEmpty() ? null
                : (int) Math.round(last7.stream().mapToDouble(Day::calories).average().orElse(0));

        return new Report(today, goal, dayList, shown, summary(points, goal, today, avg7, last7.size()));
    }

    static List<WeightPoint> trend(List<WeightEntry> weights) {
        TreeMap<LocalDate, Double> byDate = new TreeMap<>();
        weights.stream().sorted(Comparator.comparing(WeightEntry::date))
                .forEach(w -> byDate.put(w.date(), w.weightLb()));
        List<WeightPoint> out = new ArrayList<>();
        byDate.forEach((date, lb) -> {
            var window = byDate.subMap(date.minusDays(TREND_DAYS - 1L), true, date, true).values();
            double avg = window.stream().mapToDouble(Double::doubleValue).average().orElse(lb);
            out.add(new WeightPoint(date, lb, round1(avg)));
        });
        return out;
    }

    static Summary summary(List<WeightPoint> points, NutritionGoal goal, LocalDate today, Integer avg7, int logged7) {
        if (points.isEmpty()) {
            return new Summary(null, null, null, null, goal == null ? null : goal.targetWeightLb(), null, null, avg7,
                    logged7);
        }
        WeightPoint last = points.get(points.size() - 1);
        double current = last.trendLb();
        LocalDate startDate = goal != null && goal.startDate() != null ? goal.startDate() : points.get(0).date();
        double start = points.stream().filter(p -> !p.date().isBefore(startDate)).findFirst()
                .orElse(points.get(0)).trendLb();

        Double rate = slopePerWeek(points.stream()
                .filter(p -> !p.date().isBefore(last.date().minusDays(RATE_DAYS))).toList());

        Double target = goal == null ? null : goal.targetWeightLb();
        Double remaining = target == null ? null : round1(target - current);
        LocalDate projected = null;
        if (remaining != null && rate != null && Math.abs(remaining) >= 0.5 && Math.signum(rate) == Math.signum(remaining)
                && Math.abs(rate) >= 0.1) {
            projected = today.plusDays(Math.round(remaining / rate * 7));
        }
        return new Summary(current, start, round1(current - start), rate == null ? null : round2(rate), target,
                remaining, projected, avg7, logged7);
    }

    /** Least-squares slope of the trend, in pounds per week. Needs two weeks of spread. */
    static Double slopePerWeek(List<WeightPoint> pts) {
        if (pts.size() < 2) {
            return null;
        }
        LocalDate first = pts.get(0).date();
        double[] x = pts.stream().mapToDouble(p -> p.date().toEpochDay() - first.toEpochDay()).toArray();
        if (x[x.length - 1] < 14) {
            return null;
        }
        double[] y = pts.stream().mapToDouble(WeightPoint::trendLb).toArray();
        double mx = java.util.Arrays.stream(x).average().orElse(0);
        double my = java.util.Arrays.stream(y).average().orElse(0);
        double num = 0;
        double den = 0;
        for (int i = 0; i < x.length; i++) {
            num += (x[i] - mx) * (y[i] - my);
            den += (x[i] - mx) * (x[i] - mx);
        }
        return den == 0 ? null : num / den * 7;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
