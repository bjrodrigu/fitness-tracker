package com.fitnesstracker.journal.foods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fitnesstracker.journal.ApiTestBase;
import com.fitnesstracker.journal.SessionClient;
import com.fitnesstracker.journal.foods.FoodResult.Nutrients;
import com.fitnesstracker.journal.user.UserRepository;

class FoodSearchApiTest extends ApiTestBase {

    @MockitoBean
    UsdaClient usda;

    @MockitoBean
    OpenFoodFactsClient off;

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserRepository users;

    SessionClient me;

    static final FoodResult OATS = new FoodResult("usda", "1", "Oats", null, null, new Nutrients(379, 13, 68, 6.5), List.of());

    @BeforeEach
    void setUp() {
        users.deleteAll();
        me = SessionClient.signedUp(http, "searcher@example.com");
    }

    @Test
    void searchCombinesBothAndCaches() {
        when(usda.search("oats rolled")).thenReturn(List.of(OATS));
        when(off.search("oats rolled")).thenThrow(new RuntimeException("timeout"));
        var res = me.get("/api/foods/search?q=  oats   rolled ", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody().get("foods")).hasSize(1);
        assertThat((List<Object>) res.getBody().get("unavailable")).containsExactly("Open Food Facts");

        // A partial result isn't cached, so the next search tries again.
        doReturn(List.of()).when(off).search("oats rolled");
        me.get("/api/foods/search?q=oats rolled", Map.class);
        me.get("/api/foods/search?q=OATS ROLLED", Map.class);
        verify(usda, times(2)).search("oats rolled");
    }

    @Test
    void barcodeFallsBackToUsda() {
        when(off.barcode("012345678905")).thenReturn(Optional.empty());
        when(usda.barcode("012345678905")).thenReturn(Optional.of(OATS));
        assertThat(me.get("/api/foods/barcode/012345678905", Map.class).getBody()).containsEntry("name", "Oats");

        when(off.barcode("99999999")).thenReturn(Optional.empty());
        when(usda.barcode("99999999")).thenReturn(Optional.empty());
        assertThat(me.get("/api/foods/barcode/99999999", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(me.get("/api/foods/barcode/abc", Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void needsSignInAndSensibleQueries() {
        assertThat(new SessionClient(http).get("/api/foods/search?q=oats", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me.get("/api/foods/search?q=a", Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void servingLabelIsSavedWithAFood() {
        var body = Map.of("date", "2026-11-17", "meal", "BREAKFAST", "name", "Oats", "servings", 0.4,
                "servingLabel", "100 g", "calories", 379, "proteinG", 13, "carbsG", 68, "fatG", 6.5);
        var res = me.post("/api/food", body, Map.class);
        assertThat(res.getBody()).containsEntry("servingLabel", "100 g");
        when(usda.search(any())).thenReturn(List.of());
    }
}
