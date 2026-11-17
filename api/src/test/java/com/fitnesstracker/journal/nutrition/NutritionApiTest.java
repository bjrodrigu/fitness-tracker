package com.fitnesstracker.journal.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import com.fitnesstracker.journal.ApiTestBase;
import com.fitnesstracker.journal.SessionClient;
import com.fitnesstracker.journal.user.UserRepository;

class NutritionApiTest extends ApiTestBase {

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserRepository users;

    @Autowired
    FoodRepository food;

    @Autowired
    WeightRepository weights;

    @Autowired
    GoalRepository goals;

    SessionClient me;
    final String today = LocalDate.now().toString();

    @BeforeEach
    void setUp() {
        users.deleteAll();
        food.deleteAll();
        weights.deleteAll();
        goals.deleteAll();
        me = SessionClient.signedUp(http, "eater@example.com");
    }

    Map<String, Object> oats(double servings) {
        return Map.of("date", today, "meal", "BREAKFAST", "name", "Oatmeal", "servings", servings, "calories", 150,
                "proteinG", 5, "carbsG", 27, "fatG", 3);
    }

    @Test
    void logFoodAndSeeTotals() {
        var res = me.post("/api/food", oats(2), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsEntry("totalCalories", 300.0).doesNotContainKey("userId");
        String id = (String) res.getBody().get("id");

        var put = new java.util.HashMap<>(oats(1));
        put.put("name", "Oatmeal with milk");
        assertThat(me.put("/api/food/" + id, put, Map.class).getBody()).containsEntry("totalCalories", 150.0);

        assertThat(me.getList("/api/food?date=" + today).getBody()).hasSize(1);
        assertThat(me.getList("/api/food/recent").getBody()).extracting(m -> m.get("name"))
                .containsExactly("Oatmeal with milk");

        var report = me.get("/api/nutrition?days=7", Map.class).getBody();
        var days = (java.util.List<Map<String, Object>>) report.get("days");
        assertThat(days.get(6)).containsEntry("calories", 150.0);

        assertThat(me.delete("/api/food/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(me.getList("/api/food?date=" + today).getBody()).isEmpty();
    }

    @Test
    void foodIsPrivate() {
        String id = (String) me.post("/api/food", oats(1), Map.class).getBody().get("id");
        var other = SessionClient.signedUp(http, "other@example.com");
        assertThat(other.getList("/api/food?date=" + today).getBody()).isEmpty();
        assertThat(other.delete("/api/food/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.getList("/api/food/recent").getBody()).isEmpty();
    }

    @Test
    void badFoodIsRejected() {
        assertThat(me.post("/api/food", Map.of("date", today, "meal", "LUNCH", "name", "", "servings", 1, "calories", 10),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(me.post("/api/food", Map.of("date", today, "meal", "LUNCH", "name", "X", "servings", 1, "calories", -5),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void weighInsReplaceTheSameDay() {
        me.put("/api/weights/" + today, Map.of("weightLb", 180.04), Map.class);
        me.put("/api/weights/" + today, Map.of("weightLb", 179.6), Map.class);
        assertThat(weights.count()).isEqualTo(1);
        var report = me.get("/api/nutrition", Map.class).getBody();
        var summary = (Map<String, Object>) report.get("summary");
        assertThat(summary).containsEntry("currentLb", 179.6);
        assertThat(me.delete("/api/weights/" + today).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(weights.count()).isZero();
    }

    @Test
    void saveGoalAndSuggest() {
        var suggestion = me.post("/api/nutrition/suggest", Map.of("sex", "MALE", "age", 30, "heightIn", 70,
                "weightLb", 180, "activity", "MODERATE", "direction", "LOSE", "weeklyRateLb", 1), Map.class).getBody();
        assertThat(suggestion).containsEntry("calories", 2260);

        var goal = Map.of("direction", "LOSE", "targetWeightLb", 170, "weeklyRateLb", 1, "calories", 2260,
                "proteinG", 162, "carbsG", 220, "fatG", 70);
        var saved = me.put("/api/nutrition/goal", goal, Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("startDate", today).doesNotContainKey("userId");
        var report = me.get("/api/nutrition", Map.class).getBody();
        assertThat((Map<String, Object>) report.get("goal")).containsEntry("calories", 2260);

        var tooLow = new java.util.HashMap<>(goal);
        tooLow.put("calories", 500);
        assertThat(me.put("/api/nutrition/goal", tooLow, Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
