package com.deepwater.amber;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** App Store guideline 1.2 for Amber: objectionable words are filtered, and a reviewer acts on reports. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "deepwater.teacher.email=amber-reviewer@example.test")
class AmberSafetyReviewTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired TestRestTemplate http;

    @Test
    void objectionableWordsAreRefusedInProfilesAndMessages() throws Exception {
        String area = uniqueArea();
        var rude = profile("Anna", area);
        rude.put("about", "Looking for walks and good coffee");
        assertThat(send(HttpMethod.PUT, "/api/v1/amber/profile", register(), rude).getStatusCode().value()).isEqualTo(200);
        rude.put("about", "Just h00kers welcome");
        var refused = send(HttpMethod.PUT, "/api/v1/amber/profile", register(), rude);
        assertThat(refused.getStatusCode().value()).isEqualTo(400);
        assertThat(refused.getBody()).contains("Amber does not allow that wording");

        String anna = join("Anna", area);
        String mia = join("Mia", area);
        String invitation = invite(anna, memberId(mia));
        send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", mia, null);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna, Map.of("body", "What the FUCK")).getStatusCode().value())
                .isEqualTo(400);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna, Map.of("body", "Coffee in Scunthorpe?")).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void onlyTheReviewerSeesReportsAndCanRemoveSomeoneForGood() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area);
        String mia = join("Mia", area);
        String invitation = invite(mia, memberId(anna));
        send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", anna, null);
        send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", mia, Map.of("body", "Answer me now"));
        send(HttpMethod.POST, "/api/v1/amber/safety/report", anna, Map.of("memberId", memberId(mia), "reason", "UNWANTED_MESSAGES", "note", "Pushy"));

        assertThat(send(HttpMethod.GET, "/api/v1/amber/moderation/reports", anna, null).getStatusCode().value()).isEqualTo(403);

        String reviewer = register("amber-reviewer@example.test");
        JsonNode report = findReport(json(send(HttpMethod.GET, "/api/v1/amber/moderation/reports", reviewer, null)), "Pushy");
        assertThat(report).isNotNull();
        assertThat(report.get("displayName").asText()).isEqualTo("Mia");
        assertThat(report.get("messages").toString()).contains("Answer me now");

        assertThat(send(HttpMethod.POST, "/api/v1/amber/moderation/reports/" + report.get("id").asText(), anna, Map.of("action", "REMOVE"))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/moderation/reports/" + report.get("id").asText(), reviewer, Map.of("action", "REMOVE"))
                .getStatusCode().value()).isEqualTo(204);

        assertThat(findReport(json(send(HttpMethod.GET, "/api/v1/amber/moderation/reports", reviewer, null)), "Pushy")).isNull();
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/profile", mia, null)).get("joined").asBoolean()).isFalse();
        var rejoin = send(HttpMethod.PUT, "/api/v1/amber/profile", mia, profile("Mia", area));
        assertThat(rejoin.getStatusCode().value()).isEqualTo(403);
        // Echo and the Deepwater account are untouched.
        assertThat(send(HttpMethod.GET, "/api/v1/amber/profile", mia, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aDismissedReportLeavesThePersonInAmber() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area);
        String mia = join("Mia", area);
        send(HttpMethod.POST, "/api/v1/amber/safety/report", anna, Map.of("memberId", memberId(mia), "reason", "OTHER", "note", "Not sure"));
        String reviewer = register("amber-reviewer@example.test");
        JsonNode report = findReport(json(send(HttpMethod.GET, "/api/v1/amber/moderation/reports", reviewer, null)), "Not sure");
        assertThat(send(HttpMethod.POST, "/api/v1/amber/moderation/reports/" + report.get("id").asText(), reviewer, Map.of("action", "DISMISS"))
                .getStatusCode().value()).isEqualTo(204);
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/profile", mia, null)).get("joined").asBoolean()).isTrue();
        assertThat(send(HttpMethod.POST, "/api/v1/amber/moderation/reports/" + report.get("id").asText(), reviewer, Map.of("action", "DISMISS"))
                .getStatusCode().value()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode findReport(JsonNode reports, String note) {
        for (JsonNode item : reports) if (note.equals(item.path("note").asText())) return item;
        return null;
    }

    private String join(String name, String area) {
        String token = register();
        var saved = send(HttpMethod.PUT, "/api/v1/amber/profile", token, profile(name, area));
        assertThat(saved.getStatusCode().value()).as(saved.getBody()).isEqualTo(200);
        return token;
    }

    private static Map<String, Object> profile(String name, String area) {
        var body = new HashMap<String, Object>();
        body.put("displayName", name);
        body.put("age", 29);
        body.put("confirmAdult", true);
        body.put("area", area);
        body.put("intents", List.of("DATING"));
        body.put("interests", List.of());
        body.put("visible", true);
        return body;
    }

    private String invite(String token, String memberId) throws Exception {
        var response = send(HttpMethod.POST, "/api/v1/amber/invitations", token, Map.of("memberId", memberId));
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return json(response).get("id").asText();
    }

    private String memberId(String token) throws Exception {
        return json(send(HttpMethod.GET, "/api/v1/amber/profile", token, null)).get("memberId").asText();
    }

    private static String uniqueArea() { return "Town-" + UUID.randomUUID().toString().substring(0, 8); }

    private String register() { return register("amber-" + UUID.randomUUID() + "@example.test"); }

    private String register(String email) {
        var response = http.postForObject("/api/v1/auth/register", Map.of("name", "Amber Tester", "email", email, "password", PASSWORD), Map.class);
        if (response.get("token") == null) response = http.postForObject("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        return (String) response.get("token");
    }

    private static JsonNode json(ResponseEntity<String> response) throws Exception { return JSON.readTree(response.getBody()); }

    private static HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String token, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, headers(token)), String.class);
    }
}
