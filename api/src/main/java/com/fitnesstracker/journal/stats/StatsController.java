package com.fitnesstracker.journal.stats;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitnesstracker.journal.activity.ActivityService;
import com.fitnesstracker.journal.auth.CurrentUser;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final ActivityService activities;
    private final Clock clock;

    public StatsController(ActivityService activities, Clock clock) {
        this.activities = activities;
        this.clock = clock;
    }

    /** Weekly totals, best efforts, lift progression and a daily calendar, as of today. */
    @GetMapping
    public Stats stats(@RequestParam(defaultValue = "16") int weeks, @RequestParam(defaultValue = "140") int days) {
        int w = Math.min(Math.max(weeks, 1), 104);
        int d = Math.min(Math.max(days, 7), 400);
        return StatsCalculator.compute(activities.findAll(CurrentUser.id()), LocalDate.now(clock), w, d);
    }
}
