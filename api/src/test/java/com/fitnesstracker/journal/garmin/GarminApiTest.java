package com.fitnesstracker.journal.garmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fitnesstracker.journal.ApiTestBase;
import com.fitnesstracker.journal.SessionClient;
import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityRepository;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.garmin.GarminSyncClient.FetchResult;
import com.fitnesstracker.journal.garmin.GarminSyncClient.GarminAuthException;
import com.fitnesstracker.journal.garmin.GarminSyncClient.LinkResult;
import com.fitnesstracker.journal.user.UserRepository;

/** Garmin linking and sync, with the Garmin side faked. */
class GarminApiTest extends ApiTestBase {

    static final String TOKEN = "garmin-oauth-token-abc123";

    @MockitoBean
    GarminSyncClient garmin;

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserRepository users;

    @Autowired
    ActivityRepository activities;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    GarminService service;

    SessionClient me;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        activities.deleteAll();
        me = SessionClient.signedUp(http, "runner@example.com");
    }

    static Activity run(String externalId, String date, double miles) {
        return new Activity(null, null, ActivityType.RUN, LocalDate.parse(date), null, "Run " + miles + " mi", null,
                null, (int) (miles * 540), miles, null, null, null, "garmin", externalId, null, null);
    }

    @Test
    void linkingSavesAnEncryptedTokenAndImportsRuns() {
        when(garmin.link("me@garmin.example", "garmin-password")).thenReturn(new LinkResult("linked", TOKEN, null));
        when(garmin.fetch(eq(TOKEN), any())).thenReturn(new FetchResult(
                List.of(run("garmin:1", "2026-11-01", 5.0), run("garmin:2", "2026-11-03", 3.1)), TOKEN + "-refreshed"));

        var res = me.post("/api/garmin/link", Map.of("email", "me@garmin.example", "password", "garmin-password"),
                Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("needsCode", false);
        Map<String, Object> status = (Map<String, Object>) res.getBody().get("status");
        assertThat(status).containsEntry("linked", true).containsEntry("lastSyncStatus", "OK")
                .containsEntry("lastSyncNew", 2);
        assertThat(me.getList("/api/activities").getBody()).hasSize(2);
        // First sync looks back a year.
        verify(garmin).fetch(eq(TOKEN), eq(LocalDate.now().minusDays(GarminService.FIRST_SYNC_DAYS)));

        // Neither the token nor the Garmin password is stored in readable form.
        String raw = mongo.getCollection("users").find().first().toJson();
        assertThat(raw).doesNotContain("garmin-password").doesNotContain(TOKEN).contains("v1:");
        assertThat(me.get("/api/garmin", Map.class).getBody().toString()).doesNotContain("v1:");
    }

    @Test
    void linkingWithATwoStepCode() {
        when(garmin.link(any(), any())).thenReturn(new LinkResult("needs_code", null, "mfa-1"));
        when(garmin.finishLink("mfa-1", "123456")).thenReturn(new LinkResult("linked", TOKEN, null));
        when(garmin.fetch(any(), any())).thenReturn(new FetchResult(List.of(), null));

        var first = me.post("/api/garmin/link", Map.of("email", "a@b.co", "password", "pw"), Map.class);
        assertThat(first.getBody()).containsEntry("needsCode", true);
        assertThat(first.getBody().toString()).doesNotContain("mfa-1");

        var second = me.post("/api/garmin/link/code", Map.of("code", " 123456 "), Map.class);
        assertThat(((Map<String, Object>) second.getBody().get("status"))).containsEntry("linked", true);
    }

    @Test
    void wrongGarminPasswordIsABadRequest() {
        when(garmin.link(any(), any())).thenThrow(new GarminAuthException("Garmin didn't accept that email and password."));
        var res = me.post("/api/garmin/link", Map.of("email", "a@b.co", "password", "nope"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(me.get("/api/garmin", Map.class).getBody()).containsEntry("linked", false);
    }

    @Test
    void rejectedTokenAsksTheUserToLinkAgain() {
        when(garmin.link(any(), any())).thenReturn(new LinkResult("linked", TOKEN, null));
        when(garmin.fetch(any(), any())).thenReturn(new FetchResult(List.of(run("garmin:1", "2026-11-01", 5.0)), null));
        me.post("/api/garmin/link", Map.of("email", "a@b.co", "password", "pw"), Map.class);

        when(garmin.fetch(any(), any())).thenThrow(new GarminAuthException("expired"));
        service.sync(users.findByEmail("runner@example.com").get());
        var status = me.get("/api/garmin", Map.class).getBody();
        assertThat(status).containsEntry("lastSyncStatus", "NEEDS_RELINK");
        assertThat(me.getList("/api/activities").getBody()).hasSize(1);
    }

    @Test
    void unlinkForgetsTheToken() {
        when(garmin.link(any(), any())).thenReturn(new LinkResult("linked", TOKEN, null));
        when(garmin.fetch(any(), any())).thenReturn(new FetchResult(List.of(), null));
        me.post("/api/garmin/link", Map.of("email", "a@b.co", "password", "pw"), Map.class);
        assertThat(me.delete("/api/garmin/link").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Document raw = mongo.getCollection("users").find().first();
        assertThat(raw.get("garmin")).isNull();
    }
}
