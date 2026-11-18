package com.fitnesstracker.journal.parse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches what someone typed ("bench", "squats") to an exercise name they already use
 * ("Bench press", "Back squat"), so one lift doesn't split into two on the Strength page.
 */
final class ExerciseNames {

    private ExerciseNames() {
    }

    private static final Map<String, String> SHORTHAND = Map.ofEntries(
            Map.entry("bench", "bench press"),
            Map.entry("ohp", "overhead press"),
            Map.entry("rdl", "romanian deadlift"),
            Map.entry("dl", "deadlift"),
            Map.entry("pullup", "pull up"),
            Map.entry("chinup", "chin up"),
            Map.entry("pushup", "push up"),
            Map.entry("situp", "sit up"));

    record Match(String name, boolean known) {
    }

    /**
     * @param counts the person's exercise names, as they wrote them, with how often each was logged
     */
    static Match match(String said, Map<String, Integer> counts) {
        String n = key(said);
        if (n.isEmpty()) {
            return new Match("Exercise", false);
        }
        Set<String> want = Set.of(n.split(" "));
        record Option(String display, int extra, int count) {
        }
        Option best = null;
        for (var e : counts.entrySet()) {
            String k = key(e.getKey());
            if (k.equals(n)) {
                return new Match(e.getKey(), true);
            }
            Set<String> have = Set.of(k.split(" "));
            int extra;
            if (have.containsAll(want)) {
                extra = have.size() - want.size();
            } else if (want.containsAll(have)) {
                extra = want.size() - have.size();
            } else {
                continue;
            }
            Option o = new Option(e.getKey(), extra, e.getValue());
            if (best == null || Comparator.comparingInt(Option::extra).reversed()
                    .thenComparingInt(Option::count).compare(o, best) > 0) {
                best = o;
            }
        }
        if (best != null) {
            return new Match(best.display(), true);
        }
        String raw = said.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "").trim();
        return new Match(titleCase(SHORTHAND.containsKey(raw) ? SHORTHAND.get(raw) : said), false);
    }

    /** Lowercase words, shorthand expanded, plurals dropped: "Pull-ups" and "pull up" match. */
    static String key(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        s = s.replaceAll("\\b(pull|chin|push|sit) (up)s?\\b", "$1$2");
        s = Arrays.stream(s.split(" ")).filter(w -> !w.isEmpty()).map(ExerciseNames::singular)
                .collect(Collectors.joining(" "));
        return SHORTHAND.getOrDefault(s, s);
    }

    private static String singular(String w) {
        if (w.length() > 4 && w.endsWith("sses")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    static String titleCase(String s) {
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
