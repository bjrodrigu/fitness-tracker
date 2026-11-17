package com.fitnesstracker.journal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A browser stand-in for tests: keeps cookies and sends the CSRF header like the web app does. */
public class SessionClient {

    public static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    private final TestRestTemplate http;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    public boolean sendCsrf = true;

    public SessionClient(TestRestTemplate http) {
        this.http = http;
        get("/api/auth/config", Map.class); // picks up the XSRF-TOKEN cookie
    }

    public static SessionClient signedUp(TestRestTemplate http, String email) {
        var c = new SessionClient(http);
        var res = c.post("/api/auth/signup", Map.of("email", email, "password", "correct horse", "name", "Test"),
                Map.class);
        if (!res.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("signup failed: " + res);
        }
        return c;
    }

    public <T> ResponseEntity<T> get(String url, Class<T> type) {
        return exchange(url, HttpMethod.GET, null, type);
    }

    public ResponseEntity<List<Map<String, Object>>> getList(String url) {
        var res = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), LIST);
        remember(res);
        return res;
    }

    public <T> ResponseEntity<T> post(String url, Object body, Class<T> type) {
        return exchange(url, HttpMethod.POST, body, type);
    }

    public <T> ResponseEntity<T> put(String url, Object body, Class<T> type) {
        return exchange(url, HttpMethod.PUT, body, type);
    }

    public ResponseEntity<Map> delete(String url) {
        return exchange(url, HttpMethod.DELETE, null, Map.class);
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, Object body, Class<T> type) {
        var res = http.exchange(url, method, new HttpEntity<>(body, headers()), type);
        remember(res);
        return res;
    }

    private HttpHeaders headers() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (!cookies.isEmpty()) {
            h.add(HttpHeaders.COOKIE, cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (sendCsrf && cookies.containsKey("XSRF-TOKEN")) {
            h.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        return h;
    }

    private void remember(ResponseEntity<?> res) {
        List<String> set = res.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (set == null) {
            return;
        }
        for (String c : set) {
            String pair = c.split(";", 2)[0];
            int eq = pair.indexOf('=');
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (value.isEmpty() || c.toLowerCase().contains("max-age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
