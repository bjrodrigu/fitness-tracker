package com.fitnesstracker.journal.parse;

/**
 * A language model that turns a message into an {@link Extraction}. Which one runs is a
 * setting (PARSE_PROVIDER): a local model through Ollama, or Claude through the Anthropic API.
 */
public interface ParseModel {

    /** For the status endpoint and logs, e.g. "ollama qwen2.5:3b". */
    String describe();

    /**
     * @param context today's date, the person's exercise names and today's entries, as text
     * @throws Unavailable when the model can't be reached or doesn't answer in time
     */
    Extraction extract(String message, String context);

    class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
