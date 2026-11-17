package com.fitnesstracker.journal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import com.fitnesstracker.journal.ApiTestBase;
import com.fitnesstracker.journal.SessionClient;

@TestPropertySource(properties = "app.demo-data=true")
class DemoApiTest extends ApiTestBase {

    @Autowired
    TestRestTemplate http;

    @Test
    void demoAccountCanReadButNotWrite() {
        var c = new SessionClient(http);
        assertThat(c.get("/api/auth/config", Map.class).getBody()).containsEntry("demo", true);
        var me = c.post("/api/auth/demo", null, Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("demo", true);

        assertThat(c.getList("/api/activities").getBody()).hasSizeGreaterThan(50);
        var write = c.post("/api/activities", Map.of("type", "RUN", "date", "2026-11-10", "title", "Mine"), Map.class);
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(write.getBody().get("detail").toString()).contains("read-only");

        // The demo account has no password, so it can't be signed in to that way.
        assertThat(new SessionClient(http).post("/api/auth/login",
                Map.of("email", AuthService.DEMO_EMAIL, "password", ""), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
