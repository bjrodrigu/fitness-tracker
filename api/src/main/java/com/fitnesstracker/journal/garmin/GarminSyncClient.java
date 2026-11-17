package com.fitnesstracker.journal.garmin;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fitnesstracker.journal.activity.Activity;

/**
 * Talks to the garmin-sync service (loader/sync_server.py), which does the actual Garmin
 * Connect calls. That service is only reachable inside the Docker network.
 */
@Component
public class GarminSyncClient {

    private final RestClient http;

    public GarminSyncClient(@Value("${app.garmin.sync-url:http://garmin-sync:8000}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMinutes(3));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** status is "linked" (token set) or "needs_code" (mfaId set). */
    public record LinkResult(String status, String token, String mfaId) {
    }

    public record FetchResult(List<Activity> entries, String token) {
    }

    record ErrorBody(String error) {
    }

    /** Garmin turned down the login or the saved token. */
    public static class GarminAuthException extends RuntimeException {
        public GarminAuthException(String message) {
            super(message);
        }
    }

    /** Garmin or the sync service couldn't be reached, or asked us to slow down. */
    public static class GarminUnavailableException extends RuntimeException {
        public GarminUnavailableException(String message) {
            super(message);
        }
    }

    public LinkResult link(String email, String password) {
        return post("/link", Map.of("email", email, "password", password), LinkResult.class);
    }

    public LinkResult finishLink(String mfaId, String code) {
        return post("/link/code", Map.of("mfaId", mfaId, "code", code), LinkResult.class);
    }

    public FetchResult fetch(String token, LocalDate since) {
        return post("/fetch", Map.of("token", token, "since", since.toString()), FetchResult.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return http.post().uri(path).body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String msg;
                        try {
                            ErrorBody b = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(res.getBody(), ErrorBody.class);
                            msg = b.error();
                        } catch (Exception e) {
                            msg = null;
                        }
                        if (res.getStatusCode().value() == 401) {
                            throw new GarminAuthException(msg != null ? msg : "Garmin didn't accept the login.");
                        }
                        throw new GarminUnavailableException(
                                msg != null ? msg : "Garmin Connect didn't answer. Try again later.");
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            throw new GarminUnavailableException("The Garmin sync service isn't running.");
        }
    }
}
