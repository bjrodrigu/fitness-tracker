package com.fitnesstracker.journal.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real API against a throwaway MongoDB in Docker. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.demo-data=false")
class ActivityApiTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    TestRestTemplate http;

    @Autowired
    ActivityRepository repo;

    static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    Map<String, Object> runBody(String title, String date, double miles, int sec) {
        return Map.of("type", "RUN", "date", date, "title", title, "distanceMi", miles, "durationSec", sec,
                "notes", "Felt good on the hills", "tags", List.of("easy"));
    }

    @Test
    void createReadUpdateDelete() {
        var created = http.postForEntity("/api/activities", runBody("Easy 5", "2026-11-10", 5.0, 2700), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(created.getBody().get("paceSecPerMi")).isEqualTo(540);
        assertThat(created.getBody().get("source")).isEqualTo("manual");
        assertThat(created.getBody().get("date")).isEqualTo("2026-11-10");

        var update = Map.of("type", "RUN", "date", "2026-11-10", "title", "Easy 5 (wet)", "distanceMi", 5.0,
                "durationSec", 2760);
        var put = http.exchange("/api/activities/" + id, HttpMethod.PUT, new HttpEntity<>(update), Map.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put.getBody().get("title")).isEqualTo("Easy 5 (wet)");
        assertThat(put.getBody().get("createdAt")).isEqualTo(created.getBody().get("createdAt"));

        http.delete("/api/activities/" + id);
        assertThat(http.getForEntity("/api/activities/" + id, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void searchMatchesNotesAndExerciseNamesAndFiltersByType() {
        http.postForEntity("/api/activities", runBody("Easy 5", "2026-11-10", 5.0, 2700), Map.class);
        http.postForEntity("/api/activities", Map.of("type", "STRENGTH", "date", "2026-11-11", "title", "Lower body",
                "exercises", List.of(Map.of("name", "Back squat", "sets", List.of(Map.of("reps", 5, "weightLb", 185))))),
                Map.class);

        assertThat(http.exchange("/api/activities?q=HILLS", HttpMethod.GET, null, LIST).getBody())
                .extracting(m -> m.get("title")).containsExactly("Easy 5");
        assertThat(http.exchange("/api/activities?q=squat", HttpMethod.GET, null, LIST).getBody())
                .extracting(m -> m.get("title")).containsExactly("Lower body");
        assertThat(http.exchange("/api/activities?type=RUN", HttpMethod.GET, null, LIST).getBody()).hasSize(1);
        assertThat(http.exchange("/api/activities?from=2026-11-11", HttpMethod.GET, null, LIST).getBody())
                .extracting(m -> m.get("title")).containsExactly("Lower body");
        // Regex characters are treated as plain text.
        assertThat(http.exchange("/api/activities?q=.*", HttpMethod.GET, null, LIST).getBody()).isEmpty();
    }

    @Test
    void invalidEntryIsRejected() {
        var res = http.postForEntity("/api/activities", Map.of("type", "RUN", "title", ""), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importUpsertsByExternalIdAndKeepsJournalNotes() {
        var garmin = Map.of("type", "RUN", "date", "2026-11-12", "title", "Running", "distanceMi", 4.0,
                "durationSec", 2200, "externalId", "garmin:123", "source", "garmin");
        var first = http.postForEntity("/api/activities/import", List.of(garmin), Map.class);
        assertThat(first.getBody()).containsEntry("created", 1).containsEntry("updated", 0);

        String id = repo.findByExternalId("garmin:123").get().id();
        var withNotes = new java.util.HashMap<String, Object>(garmin);
        withNotes.put("title", "Easy 4 by the river");
        withNotes.put("notes", "My own note");
        http.exchange("/api/activities/" + id, HttpMethod.PUT, new HttpEntity<>(withNotes), Map.class);

        var again = http.postForEntity("/api/activities/import", List.of(garmin), Map.class);
        assertThat(again.getBody()).containsEntry("created", 0).containsEntry("updated", 1);
        Activity saved = repo.findById(id).get();
        assertThat(saved.title()).isEqualTo("Easy 4 by the river");
        assertThat(saved.notes()).isEqualTo("My own note");
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void statsEndpointReturnsWeeks() {
        var res = http.getForEntity("/api/stats?weeks=8", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody().get("weeks")).hasSize(8);
    }
}
