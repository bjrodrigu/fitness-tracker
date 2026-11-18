package com.fitnesstracker.journal.parse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.auth.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "Easy 5 miles, then bench 3x8 at 185": turns a message into draft entries. Saves nothing, so
 * the demo account can try it too; the page saves the drafts the person keeps.
 */
@RestController
@RequestMapping("/api/parse")
public class ParseController {

    static final int PER_MINUTE = 10;

    private final ParseService parser;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public ParseController(ParseService parser, Clock clock) {
        this.parser = parser;
        this.clock = clock;
    }

    /**
     * @param today the person's own date (their phone's), so "today" is right in any time zone
     * @param hour their local hour, to guess the meal when they don't say
     */
    public record ParseRequest(@NotBlank @Size(max = 1000) String message, @NotNull LocalDate today,
            @Min(0) @Max(23) Integer hour) {
    }

    public record Status(boolean enabled, String model) {
    }

    @GetMapping("/status")
    public Status status() {
        String m = parser.describeModel();
        return new Status(!"off".equals(m), m);
    }

    @PostMapping
    public ParseResult parse(@Valid @RequestBody ParseRequest req) {
        LocalDate serverToday = LocalDate.now(clock);
        if (req.today().isBefore(serverToday.minusDays(2)) || req.today().isAfter(serverToday.plusDays(2))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your device's date looks wrong.");
        }
        throttle();
        try {
            return parser.parse(CurrentUser.id(), req.message().trim(), req.today(),
                    req.hour() == null ? 12 : req.hour());
        } catch (ParseModel.Unavailable e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    /** A local model keeps the laptop busy for a few seconds per message; keep it to a sane rate. */
    private void throttle() {
        Deque<Instant> q = recent.computeIfAbsent(CurrentUser.id(), k -> new ArrayDeque<>());
        synchronized (q) {
            Instant now = clock.instant();
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                q.pollFirst();
            }
            if (q.size() >= PER_MINUTE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "That's a lot of messages. Wait a minute.");
            }
            q.addLast(now);
        }
    }
}
