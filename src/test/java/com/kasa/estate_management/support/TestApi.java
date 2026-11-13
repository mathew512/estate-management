package com.kasa.estate_management.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A tiny HTTP client for tests: real requests against the running application. */
public final class TestApi {

    public record Response(int status, String body) {

        /** The first string value of a JSON field, or null. Enough for the flat responses we check. */
        public String field(String name) {
            Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(body == null ? "" : body);
            return matcher.find() ? matcher.group(1) : null;
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final String baseUrl;

    public TestApi(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Response send(String method, String path, String token, String json) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            builder.method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Request failed: " + method + " " + path, e);
        }
    }

    /** Logs in and returns the token, or fails the test with the status that came back. */
    public String login(String email, String password) {
        Response response = send("POST", "/api/v1/auth/login", null,
            "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        if (response.status() != 200 || response.field("token") == null) {
            throw new AssertionError("Login failed for " + email + " (HTTP " + response.status() + ")");
        }
        return response.field("token");
    }
}