package com.fitnesstracker.journal.parse;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Picks the model from PARSE_PROVIDER: "ollama" (default, a local model), "anthropic"
 * (Claude, needs ANTHROPIC_API_KEY) or "off".
 */
@Configuration
public class ParseConfig {

    @Bean
    ParseModel parseModel(@Value("${app.parse.provider:ollama}") String provider,
            @Value("${app.parse.ollama-url:http://localhost:11434}") String ollamaUrl,
            @Value("${app.parse.ollama-model:qwen2.5:3b}") String ollamaModel,
            @Value("${app.parse.anthropic-key:}") String anthropicKey,
            @Value("${app.parse.anthropic-model:claude-opus-5-5}") String anthropicModel,
            ObjectMapper json) {
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "anthropic", "claude" -> anthropicKey.isBlank()
                    ? off("PARSE_PROVIDER is anthropic but ANTHROPIC_API_KEY isn't set.")
                    : new ClaudeModel(anthropicKey.trim(), anthropicModel, json);
            case "off", "" -> off("Describing entries in words is turned off on this server.");
            default -> new OllamaModel(ollamaUrl, ollamaModel, json);
        };
    }

    private static ParseModel off(String why) {
        return new ParseModel() {
            @Override
            public String describe() {
                return "off";
            }

            @Override
            public Extraction extract(String message, String context) {
                throw new Unavailable(why, null);
            }
        };
    }
}
