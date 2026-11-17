package com.fitnesstracker.journal.foods;

import java.util.List;

/**
 * A food from a nutrition database. Nutrition is per 100 g; portions say how many grams a
 * household serving is, so the app can work out any amount.
 */
public record FoodResult(
        String source,
        String id,
        String name,
        String brand,
        String barcode,
        Nutrients per100g,
        List<Portion> portions) {

    public record Nutrients(double kcal, double proteinG, double carbsG, double fatG) {
    }

    public record Portion(String label, double grams) {
    }
}
