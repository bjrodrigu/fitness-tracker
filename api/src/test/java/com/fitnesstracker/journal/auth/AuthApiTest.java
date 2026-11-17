package com.fitnesstracker.journal.auth;

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
import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.activity.ActivityRepository;
import com.fitnesstracker.journal.activity.ActivityType;
import com.fitnesstracker.journal.user.UserRepository;

class AuthApiTest extends ApiTestBase {

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserRepository users;

    @Autowired
    ActivityRepository activities;

    @Autowired
    LoginThrottle throttle;

    @BeforeEach
    void clean() {
        throttle.reset();
        users.deleteAll();
        activities.deleteAll();
    }

    Map<String, String> creds(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    @Test
    void signedOutRequestsAreRejected() {
        var c = new SessionClient(http);
        assertThat(c.get("/api/activities", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(c.get("/api/stats", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(c.get("/api/auth/me", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void signupLoginLogout() {
        var c = new SessionClient(http);
        var signup = c.post("/api/auth/signup",
                Map.of("email", " Runner@Example.com ", "password", "correct horse", "name", "Sam"), Map.class);
        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signup.getBody()).containsEntry("email", "runner@example.com").containsEntry("demo", false);
        assertThat(c.getList("/api/activities").getStatusCode()).isEqualTo(HttpStatus.OK);

        // Stored hashed.
        assertThat(users.findByEmail("runner@example.com").get().passwordHash()).startsWith("{bcrypt}");

        assertThat(c.post("/api/auth/logout", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(c.get("/api/auth/me", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Signing straight back in works without reloading.
        assertThat(c.post("/api/auth/login", creds("runner@example.com", "correct horse"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        var fresh = new SessionClient(http);
        assertThat(fresh.post("/api/auth/login", creds("runner@example.com", "wrong password"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var ok = fresh.post("/api/auth/login", creds("RUNNER@example.com", "correct horse"), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fresh.get("/api/auth/me", Map.class).getBody()).containsEntry("name", "Sam");
    }

    @Test
    void signupValidation() {
        var c = new SessionClient(http);
        assertThat(c.post("/api/auth/signup", Map.of("email", "a@b.co", "password", "short", "name", "A"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(c.post("/api/auth/signup", Map.of("email", "not-an-email", "password", "long enough", "name", "A"),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        SessionClient.signedUp(http, "taken@example.com");
        assertThat(new SessionClient(http).post("/api/auth/signup",
                Map.of("email", "taken@example.com", "password", "long enough", "name", "B"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void writesWithoutCsrfTokenAreRejected() {
        var c = SessionClient.signedUp(http, "runner@example.com");
        c.sendCsrf = false;
        var res = c.post("/api/activities", Map.of("type", "RUN", "date", "2026-11-10", "title", "Easy"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(activities.count()).isZero();
    }

    @Test
    void tooManyWrongPasswordsLockTheEmailForAWhile() {
        SessionClient.signedUp(http, "runner@example.com");
        var c = new SessionClient(http);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            c.post("/api/auth/login", creds("runner@example.com", "guess " + i), Map.class);
        }
        assertThat(c.post("/api/auth/login", creds("runner@example.com", "correct horse"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void firstAccountTakesOverEntriesFromBeforeAccounts() {
        activities.save(new Activity(null, null, ActivityType.RUN, LocalDate.of(2026, 11, 1), null, "Old run", null,
                null, 1800, 3.0, null, null, null, "garmin", "garmin:1", null, null));
        var first = SessionClient.signedUp(http, "first@example.com");
        var second = SessionClient.signedUp(http, "second@example.com");
        assertThat(first.getList("/api/activities").getBody()).hasSize(1);
        assertThat(second.getList("/api/activities").getBody()).isEmpty();
    }
}
