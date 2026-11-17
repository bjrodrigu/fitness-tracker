package com.fitnesstracker.journal.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.activity.Exercise;
import com.fitnesstracker.journal.activity.ExerciseSet;

class StatsCalculatorTest {

    // A Wednesday.
    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    static Activity run(String id, LocalDate date, double miles, int sec) {
        return new Activity(id, null, ActivityType.RUN, date, null, "Run", null, null, sec, miles, null, null, null,
                "test", null, null, null);
    }

    static Activity lift(String id, LocalDate date, Exercise... exercises) {
        return new Activity(id, null, ActivityType.STRENGTH, date, null, "Lift", null, null, 3600, null, null, null,
                List.of(exercises), "test", null, null, null);
    }

    static Exercise ex(String name, int reps, double weight) {
        return new Exercise(name, List.of(new ExerciseSet(reps, weight)));
    }

    @Test
    void weeksRunMondayToSundayAndAverageTimeWeightedPace() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        List<Activity> all = List.of(
                run("a", monday, 3, 1800),                // 10:00/mi
                run("b", monday.plusDays(1), 6, 3000),    // 8:20/mi
                run("c", monday.minusDays(1), 10, 5400),  // Sunday: belongs to the previous week
                lift("d", monday, ex("Squat", 5, 200)));

        Stats s = StatsCalculator.compute(all, TODAY, 2, 7);

        Stats.Week thisWeek = s.weeks().get(1);
        assertThat(thisWeek.weekStart()).isEqualTo(monday);
        assertThat(thisWeek.runMiles()).isEqualTo(9.0);
        assertThat(thisWeek.runs()).isEqualTo(2);
        assertThat(thisWeek.avgPaceSecPerMi()).isEqualTo(533); // 4800 s / 9 mi
        assertThat(thisWeek.longRunMi()).isEqualTo(6.0);
        assertThat(thisWeek.strengthSessions()).isEqualTo(1);
        assertThat(thisWeek.strengthVolumeLb()).isEqualTo(1000);

        Stats.Week lastWeek = s.weeks().get(0);
        assertThat(lastWeek.runMiles()).isEqualTo(10.0);
        assertThat(s.summary().weekMiles()).isEqualTo(9.0);
        assertThat(s.summary().lastWeekMiles()).isEqualTo(10.0);
        assertThat(s.summary().activeWeekStreak()).isEqualTo(2);
    }

    @Test
    void futureEntriesAreIgnored() {
        Stats s = StatsCalculator.compute(List.of(run("x", TODAY.plusDays(2), 5, 2500)), TODAY, 4, 7);
        assertThat(s.summary().weekMiles()).isZero();
        assertThat(s.longestRun()).isNull();
    }

    @Test
    void bestEffortUsesRunsAtOrJustOverTheDistance() {
        List<Activity> all = List.of(
                run("short", TODAY.minusDays(3), 3.0, 1200),  // fast but under 5K: not counted
                run("fiveK", TODAY.minusDays(5), 3.12, 1560), // 500 s/mi
                run("slow5k", TODAY.minusDays(9), 3.2, 1800),
                run("long", TODAY.minusDays(1), 8.0, 4400));  // too long for 5K and 10K

        Stats s = StatsCalculator.compute(all, TODAY, 2, 7);

        assertThat(s.bestEfforts()).extracting(Stats.BestEffort::label).containsExactly("5K");
        Stats.BestEffort fiveK = s.bestEfforts().get(0);
        assertThat(fiveK.run().id()).isEqualTo("fiveK");
        assertThat(fiveK.paceSecPerMi()).isEqualTo(500);
        assertThat(fiveK.timeSec()).isEqualTo((int) Math.round(500 * StatsCalculator.FIVE_K));
        assertThat(s.longestRun().id()).isEqualTo("long");
    }

    @Test
    void liftProgressUsesEpleyAndGroupsNamesIgnoringCase() {
        List<Activity> all = List.of(
                lift("1", TODAY.minusWeeks(10), ex("Back squat", 5, 150)),
                lift("2", TODAY.minusWeeks(6), ex("back  squat", 5, 180)),
                lift("3", TODAY.minusWeeks(1), ex("Back Squat", 3, 200), ex("Pull-up", 8, 0)));

        Stats s = StatsCalculator.compute(all, TODAY, 2, 7);

        Stats.LiftProgress squat = s.lifts().stream().filter(l -> l.name().equals("Back squat")).findFirst().get();
        assertThat(squat.sessions()).hasSize(3);
        assertThat(squat.sessions().get(0).e1rmLb()).isEqualTo(175.0); // 150 * (1 + 5/30)
        assertThat(squat.latestE1rmLb()).isEqualTo(220.0);             // 200 * 1.1
        assertThat(squat.bestE1rmLb()).isEqualTo(220.0);
        assertThat(squat.changeLast8WeeksLb()).isEqualTo(10.0);        // 220 - 210 (the 6-weeks-ago session)
        assertThat(squat.bodyweight()).isFalse();

        Stats.LiftProgress pullup = s.lifts().stream().filter(l -> l.name().equals("Pull-up")).findFirst().get();
        assertThat(pullup.bodyweight()).isTrue();
        assertThat(pullup.bestReps()).isEqualTo(8);
    }

    @Test
    void singleRepIsItsOwnMax() {
        assertThat(StatsCalculator.e1rm(new ExerciseSet(1, 225))).isEqualTo(225);
        assertThat(StatsCalculator.e1rm(new ExerciseSet(0, 225))).isZero();
    }

    @Test
    void daysCoverTheRequestedWindow() {
        Stats s = StatsCalculator.compute(List.of(run("a", TODAY, 4, 2000), lift("b", TODAY.minusDays(1))), TODAY, 2, 7);
        assertThat(s.days()).hasSize(7);
        assertThat(s.days().get(6).date()).isEqualTo(TODAY);
        assertThat(s.days().get(6).runMiles()).isEqualTo(4.0);
        assertThat(s.days().get(5).strength()).isTrue();
    }
}
