package com.fitnesstracker.journal.parse;

/** The instructions both models get. Examples matter more than rules for a small model. */
final class Prompt {

    private Prompt() {
    }

    static final String SYSTEM = """
            You turn short messages from someone's training and food journal into a list of items.
            Reply with JSON only: {"items": [...]}. Every field shown is required; use null when the message doesn't say.

            Kinds of item:
            - run: one run. Copy the distance and its unit (mi, km or m) and the time in minutes.
            - lift: one exercise with its sets. "3x8 at 185" means 3 sets of 8 reps at 185, so
              {"count":3,"reps":8,"weight":185,"weightUnit":"lb"}. Bodyweight exercises have weight 0. No unit said means lb.
            - fix_set: a correction to a set already logged. setNumber is 1 for the first set, 0 for the last.
            - fix_run: a correction to a run already logged.
            - food: something eaten. dish is a short name for it. ingredients lists its main parts, each with a
              plain food name to look up, the amount as said ("" if not said) and your best guess of the grams.

            dayOffset is 0 for today and -1 for yesterday. notes is how a run felt, in their words.
            Copy numbers exactly as written; never fill in a number the message doesn't give (except grams).
            A message can mention several things: list every one.
            count is how many sets this message adds, not the total for the day: "one more set" is count 1.
            The examples only show the format; never reuse their foods, exercises or numbers.
            A rest day or a message with nothing done gives {"items": []}.

            Examples:
            "easy 5k in 28 min, legs heavy" ->
            {"items":[{"kind":"run","dayOffset":0,"distance":5,"distanceUnit":"km","minutes":28,"notes":"Easy, legs heavy"}]}

            "ran 4 miles then bench 3x5 at 135" ->
            {"items":[{"kind":"run","dayOffset":0,"distance":4,"distanceUnit":"mi","minutes":null,"notes":null},
             {"kind":"lift","dayOffset":0,"exercise":"bench","sets":[{"count":3,"reps":5,"weight":135,"weightUnit":"lb"}]}]}

            "squat 5x5 at 100kg then 3 sets of 12 dips" ->
            {"items":[{"kind":"lift","dayOffset":0,"exercise":"squat","sets":[{"count":5,"reps":5,"weight":100,"weightUnit":"kg"}]},
             {"kind":"lift","dayOffset":0,"exercise":"dips","sets":[{"count":3,"reps":12,"weight":0,"weightUnit":"lb"}]}]}

            "the last curl set was only 6 reps" ->
            {"items":[{"kind":"fix_set","dayOffset":0,"exercise":"curl","setNumber":0,"reps":6,"weight":null,"weightUnit":null}]}

            "yesterday's run was 4 miles not 3" ->
            {"items":[{"kind":"fix_run","dayOffset":-1,"distance":4,"distanceUnit":"mi","minutes":null}]}

            "yesterday I ran 6 miles in 50 min, then had a ham sandwich" ->
            {"items":[{"kind":"run","dayOffset":-1,"distance":6,"distanceUnit":"mi","minutes":50,"notes":null},
             {"kind":"food","dayOffset":-1,"dish":"Ham sandwich","meal":null,"ingredients":[
             {"food":"bread","amount":"2 slices","grams":60},{"food":"ham","amount":"","grams":60}]}]}

            "greek yogurt and granola for breakfast" ->
            {"items":[{"kind":"food","dayOffset":0,"dish":"Greek yogurt with granola","meal":"breakfast","ingredients":[
             {"food":"greek yogurt plain","amount":"","grams":170},{"food":"granola","amount":"","grams":50}]}]}
            """;
}
