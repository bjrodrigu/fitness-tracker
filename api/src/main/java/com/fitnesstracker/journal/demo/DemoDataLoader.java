package com.fitnesstracker.journal.demo;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityRepository;
import com.fitnesstracker.journal.auth.AuthService;
import com.fitnesstracker.journal.user.Role;
import com.fitnesstracker.journal.user.User;
import com.fitnesstracker.journal.user.UserRepository;

/**
 * With app.demo-data=true, keeps a read-only demo account with synthetic entries. Never touches
 * other accounts' data.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final long SEED = 42;

    private final ActivityRepository repo;
    private final UserRepository users;
    private final AuthService auth;
    private final Clock clock;

    public DemoDataLoader(ActivityRepository repo, UserRepository users, AuthService auth, Clock clock) {
        this.repo = repo;
        this.users = users;
        this.auth = auth;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        User demo = auth.demoUser().orElseGet(() -> users.save(
                new User(null, AuthService.DEMO_EMAIL, null, "Demo runner", Role.DEMO, null, now)));
        // Sample entries made before accounts existed.
        auth.claimOwnerless(demo.id(), Criteria.where("source").is("demo"));
        if (repo.countByUserId(demo.id()) > 0) {
            log.info("Demo account already has entries.");
            return;
        }
        var entries = DemoDataGenerator.generate(SEED, LocalDate.now(clock)).stream()
                .map(a -> new Activity(null, demo.id(), a.type(), a.date(), a.startTime(), a.title(), a.notes(),
                        a.effort(), a.durationSec(), a.distanceMi(), a.avgHeartRate(), a.tags(), a.exercises(),
                        a.source(), a.externalId(), now, now))
                .toList();
        repo.saveAll(entries);
        log.info("Loaded {} demo entries.", entries.size());
    }
}
