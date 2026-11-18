package com.fitnesstracker.journal.parse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A local model served by Ollama (ollama.com) on the same computer. The JSON schema in the
 * request makes Ollama constrain the output, so even a small model returns the right shape.
 */
public class OllamaModel implements ParseModel {

    private final RestClient http;
    private final String model;
    private final ObjectMapper json;

    public OllamaModel(String baseUrl, String model, ObjectMapper json) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // The first message after a quiet spell loads the model into memory, which takes a while.
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.model = model;
        this.json = json;
    }

    @Override
    public String describe() {
        return "Ollama " + model;
    }

    @Override
    public Extraction extract(String message, String context) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "keep_alive", "10m",
                "format", SCHEMA,
                "options", Map.of("temperature", 0, "num_ctx", 4096),
                "messages", List.of(
                        Map.of("role", "system", "content", Prompt.SYSTEM),
                        Map.of("role", "user", "content", context + "\n\nMessage: " + message)));
        JsonNode res;
        try {
            res = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String why = e.getStatusCode().value() == 404
                    ? "Ollama doesn't have the model " + model + ". Run: ollama pull " + model
                    : "Ollama answered " + e.getStatusCode().value();
            throw new Unavailable(why, e);
        } catch (RestClientException e) {
            throw new Unavailable("Can't reach Ollama. Is it running?", e);
        }
        String content = res == null ? null : res.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new Unavailable("Ollama sent an empty answer", null);
        }
        try {
            return json.readValue(content, Extraction.class);
        } catch (JsonProcessingException e) {
            throw new Unavailable("The model's answer wasn't valid JSON", e);
        }
    }

    // One shape per kind, with every field required (null when not said). A small model fills a
    // short, fixed form far more reliably than it picks from a long list of optional fields.
    private static Map<String, Object> shape(String kind, Object... fields) {
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("kind", Map.of("type", "string", "enum", List.of(kind)));
        props.put("dayOffset", INT);
        for (int i = 0; i < fields.length; i += 2) {
            props.put((String) fields[i], fields[i + 1]);
        }
        return Map.of("type", "object", "properties", props, "required", List.copyOf(props.keySet()));
    }

    private static Map<String, Object> obj(Map<String, Object> props) {
        return Map.of("type", "object", "properties", props, "required", List.copyOf(props.keySet()));
    }

    private static Map<String, Object> fields(Object... kv) {
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            props.put((String) kv[i], kv[i + 1]);
        }
        return obj(props);
    }

    private static Map<String, Object> orNull(Map<String, Object> t) {
        return Map.of("anyOf", List.of(t, Map.of("type", "null")));
    }

    private static Map<String, Object> oneOf(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static final Map<String, Object> NUM = Map.of("type", "number");
    private static final Map<String, Object> INT = Map.of("type", "integer");
    private static final Map<String, Object> STR = Map.of("type", "string");

    static final Map<String, Object> SCHEMA = obj(Map.of("items", Map.of("type", "array", "items", Map.of("anyOf", List.of(
            shape("run", "distance", orNull(NUM), "distanceUnit", orNull(oneOf("mi", "km", "m")),
                    "minutes", orNull(NUM), "notes", orNull(STR)),
            shape("lift", "exercise", STR, "sets", Map.of("type", "array", "items", fields("count", INT, "reps", INT, "weight", NUM, "weightUnit", oneOf("lb", "kg")))),
            shape("fix_set", "exercise", STR, "setNumber", INT, "reps", orNull(INT), "weight", orNull(NUM),
                    "weightUnit", orNull(oneOf("lb", "kg"))),
            shape("fix_run", "distance", orNull(NUM), "distanceUnit", orNull(oneOf("mi", "km", "m")),
                    "minutes", orNull(NUM)),
            shape("food", "dish", STR, "meal", orNull(oneOf("breakfast", "lunch", "dinner", "snack")),
                    "ingredients", Map.of("type", "array", "items", fields("food", STR, "amount", STR, "grams", NUM))))))));
}
