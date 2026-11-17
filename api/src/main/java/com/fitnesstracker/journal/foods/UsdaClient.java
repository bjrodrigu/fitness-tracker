package com.fitnesstracker.journal.foods;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitnesstracker.journal.foods.FoodResult.Nutrients;
import com.fitnesstracker.journal.foods.FoodResult.Portion;

/**
 * USDA FoodData Central: good for whole foods (a banana, chicken breast). Needs a free key from
 * api.data.gov in FDC_API_KEY; without one it uses DEMO_KEY, which allows only a few searches an hour.
 */
@Component
public class UsdaClient {

    static final String GENERIC = "Foundation,SR Legacy,Survey (FNDDS)";
    private final RestClient http;
    private final String key;

    public UsdaClient(@Value("${app.foods.usda-url:https://api.nal.usda.gov/fdc/v1}") String baseUrl,
            @Value("${app.foods.usda-key:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.key = key == null || key.isBlank() ? "DEMO_KEY" : key.trim();
    }

    public boolean hasOwnKey() {
        return !"DEMO_KEY".equals(key);
    }

    public List<FoodResult> search(String q) {
        return run(q, GENERIC, 12);
    }

    /** Branded foods carry the package barcode (GTIN/UPC). */
    public Optional<FoodResult> barcode(String code) {
        String want = code.replaceFirst("^0+", "");
        return run(code, "Branded", 5).stream()
                .filter(f -> f.barcode() != null && f.barcode().replaceFirst("^0+", "").equals(want))
                .findFirst();
    }

    private List<FoodResult> run(String q, String dataTypes, int size) {
        JsonNode body = http.get()
                .uri(b -> b.path("/foods/search").queryParam("api_key", key).queryParam("query", q)
                        .queryParam("dataType", dataTypes).queryParam("pageSize", size).build())
                .retrieve().body(JsonNode.class);
        List<FoodResult> out = new ArrayList<>();
        if (body != null) {
            body.path("foods").forEach(f -> parse(f).ifPresent(out::add));
        }
        return out;
    }

    static Optional<FoodResult> parse(JsonNode f) {
        Double kcal = null;
        double protein = 0;
        double carbs = 0;
        double fat = 0;
        Double atwater = null;
        for (JsonNode n : f.path("foodNutrients")) {
            String num = n.path("nutrientNumber").asText("");
            String unit = n.path("unitName").asText("").toUpperCase(Locale.ROOT);
            double v = n.path("value").asDouble();
            switch (num) {
                case "208" -> {
                    if (unit.equals("KCAL")) {
                        kcal = v;
                    }
                }
                case "957", "958" -> atwater = atwater == null ? v : atwater; // energy for some Foundation foods
                case "203" -> protein = v;
                case "205" -> carbs = v;
                case "204" -> fat = v;
                default -> {
                }
            }
        }
        if (kcal == null) {
            kcal = atwater;
        }
        String name = f.path("description").asText("").trim();
        if (kcal == null || name.isEmpty()) {
            return Optional.empty();
        }
        List<Portion> portions = new ArrayList<>();
        for (JsonNode m : f.path("foodMeasures")) {
            double g = m.path("gramWeight").asDouble(0);
            String label = m.path("disseminationText").asText("").trim();
            if (g > 0 && !label.isEmpty() && !label.equalsIgnoreCase("Quantity not specified") && portions.size() < 6) {
                portions.add(new Portion(label + " (" + Math.round(g) + " g)", g));
            }
        }
        double serving = f.path("servingSize").asDouble(0);
        if (serving > 0 && "g".equalsIgnoreCase(f.path("servingSizeUnit").asText(""))) {
            String household = f.path("householdServingFullText").asText("").trim();
            portions.add(0, new Portion(
                    (household.isEmpty() ? "1 serving" : household) + " (" + Math.round(serving) + " g)", serving));
        }
        String brand = f.path("brandName").asText(f.path("brandOwner").asText("")).trim();
        return Optional.of(new FoodResult("usda", f.path("fdcId").asText(), tidy(name), brand.isEmpty() ? null : brand,
                f.path("gtinUpc").asText(null), new Nutrients(kcal, protein, carbs, fat), portions));
    }

    /** USDA names are often in capitals ("GREEK YOGURT"); make them readable. */
    static String tidy(String name) {
        if (!name.equals(name.toUpperCase(Locale.ROOT))) {
            return name;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
