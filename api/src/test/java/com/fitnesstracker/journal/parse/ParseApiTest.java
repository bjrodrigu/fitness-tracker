package com.fitnesstracker.journal.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fitnesstracker.journal.ApiTestBase;
import com.fitnesstracker.journal.SessionClient;
import com.fitnesstracker.journal.activity.ActivityRepository;
import com.fitnesstracker.journal.foods.OpenFoodFactsClient;
import com.fitnesstracker.journal.foods.UsdaClient;
import com.fitnesstracker.journal.parse.Extraction.Item;
import com.fitnesstracker.journal.parse.Extraction.SetGroup;
import com.fitnesstracker.journal.user.UserRepository;

class ParseApiTest extends ApiTestBase {

    @MockitoBean
    ParseModel model;

    @MockitoBean
    UsdaClient usda;

    @MockitoBean
    OpenFoodFactsClient off;

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserRepository users;

    @Autowired
    ActivityRepository activities;

    SessionClient me;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        users.deleteAll();
        activities.deleteAll();
        me = SessionClient.signedUp(http, "talker@example.com");
        when(model.describe()).thenReturn("test model");
    }

    static Extraction bench(int count, int reps, double weight) {
        return new Extraction(List.of(new Item("lift", 0, null, null, null, null, null, null, "bench",
                List.of(new SetGroup(count, reps, weight, "lb")), null, null, null, null, null, null, null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void draftsAddToTodaysSessionAndSaveThroughTheNormalEndpoint() {
        var created = me.post("/api/activities", Map.of("type", "STRENGTH", "date", today.toString(), "title", "Push day",
                "exercises", List.of(Map.of("name", "Bench press", "sets", List.of(Map.of("reps", 8, "weightLb", 185))))),
                Map.class);
        String id = (String) created.getBody().get("id");

        when(model.extract(anyString(), anyString())).thenReturn(bench(2, 8, 185));
        var res = me.post("/api/parse", Map.of("message", "two more sets of bench 8 at 185", "today", today.toString(),
                "hour", 18), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var drafts = (List<Map<String, Object>>) res.getBody().get("drafts");
        assertThat(drafts).hasSize(1);
        var d = drafts.get(0);
        assertThat(d).containsEntry("kind", "LIFT").containsEntry("action", "UPDATE");
        var activity = (Map<String, Object>) d.get("activity");
        assertThat(activity).containsEntry("id", id).doesNotContainKey("userId");

        // Saving is the page's job: the draft goes back unchanged to the normal update endpoint.
        var saved = me.put("/api/activities/" + id, activity, Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        var sets = (List<Object>) ((List<Map<String, Object>>) saved.getBody().get("exercises")).get(0).get("sets");
        assertThat(sets).hasSize(3);
        assertThat(saved.getBody()).containsEntry("title", "Push day");
    }

    @Test
    void modelThatIsOffOrDownIsA503WithTheReason() {
        when(model.extract(anyString(), anyString()))
                .thenThrow(new ParseModel.Unavailable("Can't reach Ollama. Is it running?", null));
        var res = me.post("/api/parse", Map.of("message", "ran 3 miles", "today", today.toString()), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().get("detail")).isEqualTo("Can't reach Ollama. Is it running?");
    }

    @Test
    void badInputIsRejectedBeforeTheModelRuns() {
        var empty = me.post("/api/parse", Map.of("message", " ", "today", today.toString()), Map.class);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var wrongDate = me.post("/api/parse", Map.of("message", "ran", "today", today.minusDays(10).toString()), Map.class);
        assertThat(wrongDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var signedOut = new SessionClient(http).post("/api/parse", Map.of("message", "ran", "today", today.toString()),
                Map.class);
        assertThat(signedOut.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(me.get("/api/parse/status", Map.class).getBody())
                .containsEntry("enabled", true).containsEntry("model", "test model");
    }

    @Test
    void tooManyMessagesInAMinuteAreTurnedAway() {
        when(model.extract(anyString(), anyString())).thenReturn(new Extraction(List.of()));
        HttpStatus last = null;
        for (int i = 0; i <= ParseController.PER_MINUTE; i++) {
            last = (HttpStatus) me.post("/api/parse", Map.of("message", "hi " + i, "today", today.toString()), Map.class)
                    .getStatusCode();
        }
        assertThat(last).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
