package com.fitnesstracker.journal.foods;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitnesstracker.journal.foods.FoodResult.Nutrients;

/** Parsing real-shaped responses from Open Food Facts and USDA (trimmed). */
class FoodParsingTest {

    final ObjectMapper json = new ObjectMapper();

    @Test
    void openFoodFactsProduct() throws Exception {
        var p = json.readTree("""
                {"code":"0012345678905","product_name":"Greek Yogurt, Plain","brands":"Acme, Acme Foods",
                 "serving_size":"170 g","serving_quantity":170,
                 "nutriments":{"energy-kcal_100g":59,"proteins_100g":10.3,"carbohydrates_100g":3.6,"fat_100g":0.4}}
                """);
        FoodResult f = OpenFoodFactsClient.parse(p).orElseThrow();
        assertThat(f.name()).isEqualTo("Greek Yogurt, Plain");
        assertThat(f.brand()).isEqualTo("Acme");
        assertThat(f.barcode()).isEqualTo("0012345678905");
        assertThat(f.per100g()).isEqualTo(new Nutrients(59, 10.3, 3.6, 0.4));
        assertThat(f.portions()).singleElement().satisfies(x -> {
            assertThat(x.label()).isEqualTo("1 serving (170 g)");
            assertThat(x.grams()).isEqualTo(170);
        });
    }

    @Test
    void openFoodFactsWithoutCaloriesIsSkipped() throws Exception {
        assertThat(OpenFoodFactsClient.parse(json.readTree("""
                {"product_name":"Mystery","nutriments":{}}"""))).isEmpty();
        assertThat(OpenFoodFactsClient.parse(json.readTree("""
                {"product_name":"","nutriments":{"energy-kcal_100g":10}}"""))).isEmpty();
    }

    @Test
    void usdaGenericFoodWithPortions() throws Exception {
        var f = UsdaClient.parse(json.readTree("""
                {"fdcId":1105314,"description":"Banana, raw","dataType":"Survey (FNDDS)",
                 "foodNutrients":[
                   {"nutrientNumber":"203","unitName":"G","value":1.09},
                   {"nutrientNumber":"204","unitName":"G","value":0.33},
                   {"nutrientNumber":"205","unitName":"G","value":22.8},
                   {"nutrientNumber":"208","unitName":"KCAL","value":89}],
                 "foodMeasures":[
                   {"disseminationText":"1 medium (7\\" to 7-7/8\\" long)","gramWeight":118},
                   {"disseminationText":"Quantity not specified","gramWeight":118},
                   {"disseminationText":"1 cup, sliced","gramWeight":150}]}
                """)).orElseThrow();
        assertThat(f.source()).isEqualTo("usda");
        assertThat(f.per100g().kcal()).isEqualTo(89);
        assertThat(f.portions()).extracting(FoodResult.Portion::grams).containsExactly(118.0, 150.0);
        assertThat(f.portions().get(1).label()).isEqualTo("1 cup, sliced (150 g)");
    }

    @Test
    void usdaFoundationFoodUsesAtwaterEnergyAndBrandedServing() throws Exception {
        var foundation = UsdaClient.parse(json.readTree("""
                {"fdcId":1,"description":"Chicken breast, cooked","foodNutrients":[
                  {"nutrientNumber":"958","unitName":"KCAL","value":165},
                  {"nutrientNumber":"203","unitName":"G","value":31}]}""")).orElseThrow();
        assertThat(foundation.per100g().kcal()).isEqualTo(165);

        var branded = UsdaClient.parse(json.readTree("""
                {"fdcId":2,"description":"PROTEIN BAR","brandName":"BARCO","gtinUpc":"012345678905",
                 "servingSize":60,"servingSizeUnit":"g","householdServingFullText":"1 bar",
                 "foodNutrients":[{"nutrientNumber":"208","unitName":"KCAL","value":350}]}""")).orElseThrow();
        assertThat(branded.name()).isEqualTo("Protein bar");
        assertThat(branded.portions()).first().satisfies(p -> assertThat(p.label()).isEqualTo("1 bar (60 g)"));
    }

    @Test
    void mergePutsWholeFoodsFirstAndDropsRepeats() {
        var n = new Nutrients(1, 1, 1, 1);
        var a = new FoodResult("usda", "1", "Oats", null, null, n, List.of());
        var b = new FoodResult("off", "2", "oats", null, null, n, List.of());
        var c = new FoodResult("off", "3", "Oats", "Quaker", null, n, List.of());
        assertThat(FoodSearchService.merge(List.of(a), List.of(b, c))).containsExactly(a, c);
        assertThat(FoodSearchService.merge(null, List.of(b))).containsExactly(b);
    }
}
