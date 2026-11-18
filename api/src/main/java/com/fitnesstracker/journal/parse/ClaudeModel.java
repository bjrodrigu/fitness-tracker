package com.fitnesstracker.journal.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Claude through the Anthropic API, for when the journal is hosted somewhere without room for
 * a local model. Uses structured outputs so the reply always matches the same schema Ollama gets.
 */
public class ClaudeModel implements ParseModel {

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper json;
    private final JsonOutputFormat.Schema schema;

    public ClaudeModel(String apiKey, String model, ObjectMapper json) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
        this.json = json;
        @SuppressWarnings("unchecked")
        Map<String, Object> strict = (Map<String, Object>) closed(OllamaModel.SCHEMA);
        var b = JsonOutputFormat.Schema.builder();
        strict.forEach((k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)));
        this.schema = b.build();
    }

    @Override
    public String describe() {
        return "Claude " + model;
    }

    @Override
    public Extraction extract(String message, String context) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(Prompt.SYSTEM)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schema).build())
                        .build())
                .addUserMessage(context + "\n\nMessage: " + message)
                .build();
        Message res;
        try {
            res = client.messages().create(params);
        } catch (AnthropicException e) {
            throw new Unavailable("The Anthropic API call failed: " + e.getMessage(), e);
        }
        if (res.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false)) {
            throw new Unavailable("Claude declined to read this message", null);
        }
        String text = res.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .reduce("", String::concat);
        try {
            return json.readValue(text, Extraction.class);
        } catch (JsonProcessingException e) {
            throw new Unavailable("Claude's answer wasn't valid JSON", e);
        }
    }

    /** Structured outputs need additionalProperties: false on every object in the schema. */
    static Object closed(Object node) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put((String) k, closed(v)));
            if ("object".equals(out.get("type"))) {
                out.put("additionalProperties", false);
            }
            return out;
        }
        if (node instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(v -> out.add(closed(v)));
            return out;
        }
        return node;
    }
}
