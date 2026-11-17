package com.fitnesstracker.journal.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.stats.Stats;
import com.fitnesstracker.journal.stats.StatsCalculator;

class DemoDataGeneratorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void sameSeedAndDateGiveTheSameJournal() {
        assertThat(DemoDataGenerator.generate(42, TODAY)).isEqualTo(DemoDataGenerator.generate(42, TODAY));
    }

    @Test
    void entriesAreInThePastAndLookReasonable() {
        List<Activity> all = DemoDataGenerator.generate(42, TODAY);

        assertThat(all).hasSizeGreaterThan(120);
        assertThat(all).allSatisfy(a -> {
            assertThat(a.date()).isBeforeOrEqualTo(TODAY);
            assertThat(a.source()).isEqualTo("demo");
            assertThat(a.externalId()).isNull();
        });
        assertThat(all).filteredOn(a -> a.type() == ActivityType.RUN).allSatisfy(r -> {
            assertThat(r.paceSecPerMi()).isBetween(420, 660);
            assertThat(r.distanceMi()).isBetween(1.5, 14.0);
        });
        assertThat(all).filteredOn(a -> a.type() == ActivityType.STRENGTH)
                .allSatisfy(s -> assertThat(s.exercises()).isNotEmpty());
        assertThat(all).filteredOn(a -> a.tags().contains("race")).hasSize(1);
    }

    @Test
    void statsShowAHalfMarathonAndStrengthGains() {
        Stats s = StatsCalculator.compute(DemoDataGenerator.generate(42, TODAY), TODAY, 16, 140);

        assertThat(s.bestEfforts()).extracting(Stats.BestEffort::label).contains("5K", "10K", "Half marathon");
        Stats.LiftProgress squat = s.lifts().stream().filter(l -> l.name().equals("Back squat")).findFirst().get();
        assertThat(squat.latestE1rmLb()).isGreaterThan(squat.sessions().get(0).e1rmLb());
    }
}
