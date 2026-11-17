package com.fitnesstracker.journal.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import com.fitnesstracker.journal.ApiTestBase;
import com.fitnesstracker.journal.SessionClient;
import com.fitnesstracker.journal.user.UserRepository;

/** Runs the real API against a throwaway MongoDB in Docker. */
class ActivityApiTest extends ApiTestBase {

    @Autowired
    TestRestTemplate http;

    @Autowired
    ActivityRepository repo;

    @Autowired
    UserRepository users;

    SessionClient me;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        users.deleteAll();
        me = SessionClient.signedUp(http, "runner@example.com");
    }

    Map<String, Object> runBody(String title, String date, double miles, int sec) {
        return Map.of("type", "RUN", "date", date, "title", title, "distanceMi", miles, "durationSec", sec,
                "notes", "Felt good on the hills", "tags", List.of("easy"));
    }

    @Test
    void createReadUpdateDelete() {
        var created = me.post("/api/activities", runBody("Easy 5", "2026-11-10", 5.0, 2700), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(created.getBody().get("paceSecPerMi")).isEqualTo(540);
        assertThat(created.getBody().get("source")).isEqualTo("manual");
        assertThat(created.getBody().get("date")).isEqualTo("2026-11-10");

        var update = Map.of("type", "RUN", "date", "2026-11-10", "title", "Easy 5 (wet)", "distanceMi", 5.0,
                "durationSec", 2760);
        var put = me.put("/api/activities/" + id, update, Map.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put.getBody().get("title")).isEqualTo("Easy 5 (wet)");
        assertThat(put.getBody().get("createdAt")).isEqualTo(created.getBody().get("createdAt"));

        me.delete("/api/activities/" + id);
        assertThat(me.get("/api/activities/" + id, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void searchMatchesNotesAndExerciseNamesAndFiltersByType() {
        me.post("/api/activities", runBody("Easy 5", "2026-11-10", 5.0, 2700), Map.class);
        me.post("/api/activities", Map.of("type", "STRENGTH", "date", "2026-11-11", "title", "Lower body",
                "exercises", List.of(Map.of("name", "Back squat", "sets", List.of(Map.of("reps", 5, "weightLb", 185))))),
                Map.class);

        assertThat(me.getList("/api/activities?q=HILLS").getBody())
                .extracting(m -> m.get("title")).containsExactly("Easy 5");
        assertThat(me.getList("/api/activities?q=squat").getBody())
                .extracting(m -> m.get("title")).containsExactly("Lower body");
        assertThat(me.getList("/api/activities?type=RUN").getBody()).hasSize(1);
        assertThat(me.getList("/api/activities?from=2026-11-11").getBody())
                .extracting(m -> m.get("title")).containsExactly("Lower body");
        // Regex characters are treated as plain text.
        assertThat(me.getList("/api/activities?q=.*").getBody()).isEmpty();
    }

    @Test
    void invalidEntryIsRejected() {
        var res = me.post("/api/activities", Map.of("type", "RUN", "title", ""), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importUpsertsByExternalIdAndKeepsJournalNotes() {
        var garmin = Map.of("type", "RUN", "date", "2026-11-12", "title", "Running", "distanceMi", 4.0,
                "durationSec", 2200, "externalId", "garmin:123", "source", "garmin");
        var first = me.post("/api/activities/import", List.of(garmin), Map.class);
        assertThat(first.getBody()).containsEntry("created", 1).containsEntry("updated", 0);

        String id = repo.findAll().get(0).id();
        var withNotes = new java.util.HashMap<String, Object>(garmin);
        withNotes.put("title", "Easy 4 by the river");
        withNotes.put("notes", "My own note");
        me.put("/api/activities/" + id, withNotes, Map.class);

        var again = me.post("/api/activities/import", List.of(garmin), Map.class);
        assertThat(again.getBody()).containsEntry("created", 0).containsEntry("updated", 1);
        Activity saved = repo.findById(id).get();
        assertThat(saved.title()).isEqualTo("Easy 4 by the river");
        assertThat(saved.notes()).isEqualTo("My own note");
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void entriesArePrivateToTheirAccount() {
        var mine = me.post("/api/activities", runBody("Easy 5", "2026-11-10", 5.0, 2700), Map.class);
        String id = (String) mine.getBody().get("id");
        assertThat(mine.getBody()).doesNotContainKey("userId");

        var other = SessionClient.signedUp(http, "someone@example.com");
        assertThat(other.getList("/api/activities").getBody()).isEmpty();
        assertThat(other.get("/api/activities/" + id, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.put("/api/activities/" + id, runBody("Mine now", "2026-11-10", 1.0, 600), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.delete("/api/activities/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(repo.findById(id).get().title()).isEqualTo("Easy 5");

        // The same Garmin activity can be imported by two accounts.
        var garmin = Map.of("type", "RUN", "date", "2026-11-12", "title", "Running", "distanceMi", 4.0,
                "durationSec", 2200, "externalId", "garmin:9", "source", "garmin");
        assertThat(me.post("/api/activities/import", List.of(garmin), Map.class).getBody()).containsEntry("created", 1);
        assertThat(other.post("/api/activities/import", List.of(garmin), Map.class).getBody())
                .containsEntry("created", 1);
    }

    @Test
    void statsEndpointReturnsWeeks() {
        var res = me.get("/api/stats?weeks=8", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody().get("weeks")).hasSize(8);
    }
}
