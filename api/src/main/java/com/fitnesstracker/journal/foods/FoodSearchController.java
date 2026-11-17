package com.fitnesstracker.journal.foods;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.auth.CurrentUser;

@RestController
@RequestMapping("/api/foods")
public class FoodSearchController {

    static final int PER_MINUTE = 30;

    private final FoodSearchService foods;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public FoodSearchController(FoodSearchService foods, Clock clock) {
        this.foods = foods;
        this.clock = clock;
    }

    @GetMapping("/search")
    public FoodSearchService.SearchResult search(@RequestParam String q) {
        String query = q.trim();
        if (query.length() < 2 || query.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search for 2 to 80 characters.");
        }
        throttle();
        return foods.search(query);
    }

    @GetMapping("/barcode/{code}")
    public FoodResult barcode(@PathVariable String code) {
        if (!code.matches("\\d{8,14}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A barcode is 8 to 14 digits.");
        }
        throttle();
        return foods.barcode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "That barcode isn't in Open Food Facts or USDA. Enter the label's numbers instead."));
    }

    /** Both databases are free services; keep one account from hammering them. */
    private void throttle() {
        Deque<Instant> q = recent.computeIfAbsent(CurrentUser.id(), k -> new ArrayDeque<>());
        synchronized (q) {
            Instant now = clock.instant();
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                q.removeFirst();
            }
            if (q.size() >= PER_MINUTE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many searches. Wait a minute.");
            }
            q.addLast(now);
        }
    }
}
