package com.fitnesstracker.journal.parse;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityService;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.foods.FoodSearchService;

/** Reads a message with the configured model and builds drafts. Saves nothing. */
@Service
public class ParseService {

    private static final Logger log = LoggerFactory.getLogger(ParseService.class);

    private final ParseModel model;
    private final ActivityService activities;
    private final FoodSearchService foods;

    public ParseService(ParseModel model, ActivityService activities, FoodSearchService foods) {
        this.model = model;
        this.activities = activities;
        this.foods = foods;
    }

    public String describeModel() {
        return model.describe();
    }

    public ParseResult parse(String userId, String message, LocalDate today, int hour) {
        List<Activity> all = activities.findAll(userId);
        Map<String, Integer> names = new LinkedHashMap<>();
        all.stream().filter(a -> a.type() == ActivityType.STRENGTH)
                .flatMap(a -> a.exercises().stream())
                .forEach(e -> names.merge(e.name().trim(), 1, Integer::sum));
        List<Activity> recent = all.stream()
                .filter(a -> !a.date().isAfter(today) && !a.date().isBefore(today.minusDays(14)))
                .toList();

        long started = System.nanoTime();
        String context = context(today, names, recent);
        List<String> parts = sentences(message);
        List<Extraction.Item> items = new ArrayList<>();
        Integer lastDay = null;
        for (String part : parts) {
            List<Extraction.Item> got = model.extract(part, context).items();
            // "Yesterday I ran 5. Then had pizza." The second sentence is about yesterday too.
            if (lastDay != null && lastDay != 0 && !SAYS_DAY.matcher(part.toLowerCase(Locale.ROOT)).find()) {
                final int day = lastDay;
                got = got.stream().map(i -> withDay(i, day)).toList();
            }
            for (var i : got) {
                if (i != null && i.dayOffset() != null) {
                    lastDay = i.dayOffset();
                }
            }
            items.addAll(got);
        }
        Extraction x = new Extraction(items);
        log.info("Parsed a message ({} sentences) with {} in {} ms: {} items", parts.size(), model.describe(),
                (System.nanoTime() - started) / 1_000_000, x.items().size());

        var built = new DraftBuilder(today, hour, recent, names, q -> foods.search(q).foods(), message).build(x);
        return new ParseResult(built.drafts(), built.notes(), model.describe());
    }

    private static final Pattern SAYS_DAY = Pattern.compile(
            "\\b(today|tonight|this (morning|afternoon|evening)|yesterday|last night|ago|monday|tuesday|wednesday"
                    + "|thursday|friday|saturday|sunday)\\b");

    /**
     * Splits a message into sentences, so a small model reads one thing at a time: given four
     * things in one go, it tends to drop the last. Short fragments with no numbers stay with the sentence
     * before.
     */
    static List<String> sentences(String message) {
        List<String> out = new ArrayList<>();
        for (String raw : message.split("(?<=[.!?])\\s+|\\n+")) {
            String t = raw.trim();
            if (t.isEmpty()) {
                continue;
            }
            boolean fragment = t.split("\\s+").length < 3 && !t.matches(".*\\d.*"); // "Felt good."
            if (!out.isEmpty() && (fragment || out.size() >= 6)) {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + t);
            } else {
                out.add(t);
            }
        }
        return out.isEmpty() ? List.of(message.trim()) : out;
    }

    private static Extraction.Item withDay(Extraction.Item i, int day) {
        return new Extraction.Item(i.kind(), day, i.distance(), i.distanceUnit(), i.minutes(), i.effort(), i.title(),
                i.notes(), i.exercise(), i.sets(), i.setNumber(), i.reps(), i.weight(), i.weightUnit(), i.dish(),
                i.meal(), i.ingredients());
    }

    /** What the model is told besides the message: the date, and today's entries for corrections. */
    static String context(LocalDate today, Map<String, Integer> names, List<Activity> recent) {
        StringBuilder s = new StringBuilder();
        s.append("Today is ").append(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US))
                .append(", ").append(today).append('.');
        String common = names.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20).map(Map.Entry::getKey).collect(Collectors.joining(", "));
        if (!common.isEmpty()) {
            s.append("\nExercises they usually log: ").append(common).append('.');
        }
        List<Activity> todays = recent.stream().filter(a -> a.date().equals(today)).toList();
        if (!todays.isEmpty()) {
            s.append("\nAlready logged today:");
            for (Activity a : todays) {
                if (a.type() == ActivityType.RUN) {
                    s.append("\n- run").append(a.distanceMi() == null ? "" : ", " + a.distanceMi() + " mi");
                } else {
                    s.append("\n- lifting: ").append(a.exercises().stream()
                            .map(e -> e.name() + " " + e.sets().size() + " sets")
                            .collect(Collectors.joining(", ")));
                }
            }
        }
        return s.toString();
    }
}
