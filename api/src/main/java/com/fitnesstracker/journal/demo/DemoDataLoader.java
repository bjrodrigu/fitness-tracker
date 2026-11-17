package com.fitnesstracker.journal.demo;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fitnesstracker.journal.activity.ActivityRepository;

/** With app.demo-data=true, fills an empty journal with synthetic entries. Never touches existing data. */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final long SEED = 42;

    private final ActivityRepository repo;
    private final Clock clock;

    public DemoDataLoader(ActivityRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) {
            log.info("Journal already has entries; skipping demo data.");
            return;
        }
        var now = clock.instant();
        var entries = DemoDataGenerator.generate(SEED, LocalDate.now(clock)).stream()
                .map(a -> new com.fitnesstracker.journal.activity.Activity(null, a.type(), a.date(), a.startTime(),
                        a.title(), a.notes(), a.effort(), a.durationSec(), a.distanceMi(), a.avgHeartRate(), a.tags(),
                        a.exercises(), a.source(), a.externalId(), now, now))
                .toList();
        repo.saveAll(entries);
        log.info("Loaded {} demo entries.", entries.size());
    }
}
