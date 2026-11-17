package com.fitnesstracker.journal.stats;

import java.time.LocalDate;
import java.util.List;

/** Everything the dashboard needs, computed in one pass over the journal. */
public record Stats(
        LocalDate asOf,
        Summary summary,
        List<Week> weeks,
        List<BestEffort> bestEfforts,
        RunRef longestRun,
        List<LiftProgress> lifts,
        List<Day> days) {

    public record Summary(
            double weekMiles,
            double lastWeekMiles,
            double avgMilesLast4Weeks,
            double yearMiles,
            int yearRuns,
            int weekStrengthSessions,
            int activeWeekStreak) {
    }

    /** Totals for one Monday-to-Sunday week. avgPaceSecPerMi is null when there were no runs. */
    public record Week(
            LocalDate weekStart,
            double runMiles,
            int runs,
            int runSec,
            Integer avgPaceSecPerMi,
            double longRunMi,
            int strengthSessions,
            double strengthVolumeLb) {
    }

    /**
     * Fastest run close to a standard distance, with its time scaled to exactly that distance.
     * Based on whole-run pace, since there are no GPS splits.
     */
    public record BestEffort(String label, double distanceMi, int timeSec, int paceSecPerMi, RunRef run) {
    }

    public record RunRef(String id, LocalDate date, String title, double distanceMi, int durationSec) {
    }

    public record LiftProgress(
            String name,
            boolean bodyweight,
            List<LiftSession> sessions,
            double bestE1rmLb,
            double latestE1rmLb,
            double changeLast8WeeksLb,
            int bestReps) {
    }

    /** Best set of one session. e1rm uses the Epley formula: weight x (1 + reps / 30). */
    public record LiftSession(String activityId, LocalDate date, double topWeightLb, int topReps, double e1rmLb) {
    }

    public record Day(LocalDate date, double runMiles, boolean strength) {
    }
}
