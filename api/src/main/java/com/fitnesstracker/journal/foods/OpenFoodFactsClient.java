package com.fitnesstracker.journal.foods;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitnesstracker.journal.foods.FoodResult.Nutrients;
import com.fitnesstracker.journal.foods.FoodResult.Portion;

/**
 * Open Food Facts: a free, open database of packaged foods, good for barcodes. No key needed.
 * They ask apps to send a User-Agent naming the app.
 */
@Component
public class OpenFoodFactsClient {

    static final String FIELDS = "code,product_name,brands,serving_size,serving_quantity,nutriments";
    private final RestClient http;
    private final String country;

    public OpenFoodFactsClient(@Value("${app.foods.off-url:https://world.openfoodfacts.org}") String baseUrl,
            @Value("${app.foods.off-country:united-states}") String country) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "TrainingJournal/0.1 (self-hosted training log)").build();
        this.country = country;
    }

    public Optional<FoodResult> barcode(String code) {
        JsonNode body = http.get().uri("/api/v2/product/{code}?fields={f}", code, FIELDS).retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                }).body(JsonNode.class);
        if (body == null || body.path("status").asInt() != 1) {
            return Optional.empty();
        }
        return parse(body.path("product"));
    }

    public List<FoodResult> search(String q) {
        JsonNode body = http.get()
                .uri(b -> {
                    b.path("/cgi/search.pl").queryParam("search_terms", q).queryParam("json", 1)
                            .queryParam("page_size", 15).queryParam("fields", FIELDS);
                    if (country != null && !country.isBlank()) {
                        b.queryParam("tagtype_0", "countries").queryParam("tag_contains_0", "contains")
                                .queryParam("tag_0", country);
                    }
                    return b.build();
                })
                .retrieve().body(JsonNode.class);
        List<FoodResult> out = new ArrayList<>();
        if (body != null) {
            body.path("products").forEach(p -> parse(p).ifPresent(out::add));
        }
        return out;
    }

    /** Products without a name or calories per 100 g aren't much use, so they're skipped. */
    static Optional<FoodResult> parse(JsonNode p) {
        String name = p.path("product_name").asText("").trim();
        JsonNode n = p.path("nutriments");
        if (name.isEmpty() || !n.has("energy-kcal_100g")) {
            return Optional.empty();
        }
        var per100 = new Nutrients(n.path("energy-kcal_100g").asDouble(), n.path("proteins_100g").asDouble(),
                n.path("carbohydrates_100g").asDouble(), n.path("fat_100g").asDouble());
        List<Portion> portions = new ArrayList<>();
        double grams = p.path("serving_quantity").asDouble(0);
        String serving = p.path("serving_size").asText("").trim();
        if (grams > 0 && grams < 2000) {
            portions.add(new Portion(serving.isEmpty() ? Math.round(grams) + " g" : "1 serving (" + serving + ")", grams));
        }
        String brand = p.path("brands").asText("").split(",")[0].trim();
        String code = p.path("code").asText(null);
        return Optional.of(new FoodResult("off", code, name, brand.isEmpty() ? null : brand, code, per100, portions));
    }
}
