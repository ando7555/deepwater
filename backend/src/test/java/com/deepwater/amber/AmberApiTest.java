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
import org.springframework.jdbc.core.JdbcTemplate;

/** Acceptance tests named after AC-AMBER-1..6 in docs/product/DEEPWATER_PRODUCT_SCOPE_AND_ACCEPTANCE.md. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AmberApiTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void ac1_anAccountHasNoAmberPresenceUntilItsOwnerJoins() throws Exception {
        String area = uniqueArea();
        String outsider = register();
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/profile", outsider, null)).get("joined").asBoolean()).isFalse();
        assertThat(send(HttpMethod.GET, "/api/v1/amber/suggestions", outsider, null).getStatusCode().value()).isEqualTo(409);

        String anna = join("Anna", area, List.of("DATING"), List.of("Hiking"));
        assertThat(suggestions(anna)).doesNotContain("Amber Tester");

        // Adults only, and the person must confirm it.
        var young = profile("Kid", 17, area, List.of("DATING"), List.of());
        assertThat(send(HttpMethod.PUT, "/api/v1/amber/profile", outsider, young).getStatusCode().value()).isEqualTo(400);
        var unconfirmed = profile("Sam", 30, area, List.of("DATING"), List.of());
        unconfirmed.put("confirmAdult", false);
        assertThat(send(HttpMethod.PUT, "/api/v1/amber/profile", outsider, unconfirmed).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ac2_suggestionsShowTheirReasonsAndPassOrHideIsPrivate() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"), List.of("Hiking", "Jazz", "Cooking"));
        String mia = join("Mia", area, List.of("DATING", "FRIENDS"), List.of("jazz", "hiking", "Running"));
        String ola = join("Ola", area, List.of("FRIENDS"), List.of("Hiking", "Jazz"));

        JsonNode list = json(send(HttpMethod.GET, "/api/v1/amber/suggestions", anna, null));
        JsonNode miaCard = find(list, "Mia");
        assertThat(miaCard).isNotNull();
        assertThat(miaCard.get("reasons").toString()).contains("You are both looking for dating", "You both chose " + area,
                "2 shared interests: hiking, jazz");
        assertThat(list.toString()).doesNotContain("Ola", "accountId", "@example.test"); // Ola wants friends only

        String miaId = miaCard.get("person").get("memberId").asText();
        assertThat(send(HttpMethod.POST, "/api/v1/amber/suggestions/" + miaId + "/pass", anna, null).getStatusCode().value()).isEqualTo(204);
        assertThat(suggestions(anna)).doesNotContain(memberId(mia));
        // Mia is not told and still sees Anna.
        assertThat(suggestions(mia)).contains(memberId(anna));
        assertThat(suggestions(ola)).doesNotContain(memberId(anna));
    }

    @Test
    void ac3_noConversationUntilBothSayYesAndEitherCanLeave() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"), List.of("Jazz"));
        String mia = join("Mia", area, List.of("DATING"), List.of("Jazz"));
        String invitation = invite(anna, memberId(mia));

        // Before Mia accepts, nobody can read or write.
        assertThat(send(HttpMethod.GET, "/api/v1/amber/connections/" + invitation + "/messages", anna, null).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna, Map.of("body", "Hi")).getStatusCode().value()).isEqualTo(404);
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/connections", anna, null))).isEmpty();
        // Only the recipient can accept.
        assertThat(send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", anna, null).getStatusCode().value()).isEqualTo(404);

        JsonNode incoming = json(send(HttpMethod.GET, "/api/v1/amber/invitations", mia, null)).get("incoming");
        assertThat(incoming.toString()).contains("\"Anna\"", "You are both looking for dating");
        assertThat(send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", mia, null).getStatusCode().value()).isEqualTo(204);

        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna,
                Map.of("body", "Which hike would you recommend?")).getStatusCode().value()).isEqualTo(200);
        send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", mia, Map.of("body", "Dyrehaven!"));
        JsonNode messages = json(send(HttpMethod.GET, "/api/v1/amber/connections/" + invitation + "/messages", mia, null));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("mine").asBoolean()).isFalse();
        assertThat(messages.get(1).get("mine").asBoolean()).isTrue();

        // A third person cannot read it.
        String eve = join("Eve", area, List.of("DATING"), List.of());
        assertThat(send(HttpMethod.GET, "/api/v1/amber/connections/" + invitation + "/messages", eve, null).getStatusCode().value()).isEqualTo(404);

        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/leave", mia, null).getStatusCode().value()).isEqualTo(204);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna, Map.of("body", "Hello?")).getStatusCode().value()).isEqualTo(404);
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/connections", anna, null))).isEmpty();
    }

    @Test
    void ac4_aDeclineIsFinalAndShowsTheSenderNoReason() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"), List.of());
        String mia = join("Mia", area, List.of("DATING"), List.of());
        String invitation = invite(anna, memberId(mia));
        assertThat(send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/decline", mia, null).getStatusCode().value()).isEqualTo(204);

        JsonNode outgoing = json(send(HttpMethod.GET, "/api/v1/amber/invitations", anna, null)).get("outgoing");
        assertThat(outgoing.toString()).contains("\"status\":\"CLOSED\"").doesNotContain("DECLINED", "respondedAt");
        assertThat(suggestions(anna)).doesNotContain(memberId(mia));
        assertThat(send(HttpMethod.POST, "/api/v1/amber/invitations", anna, Map.of("memberId", memberId(mia))).getStatusCode().value()).isEqualTo(409);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", mia, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void ac5_blockAndReportStopContactAndStayPrivate() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"), List.of());
        String mia = join("Mia", area, List.of("DATING"), List.of());
        String invitation = invite(mia, memberId(anna));
        send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", anna, null);

        var report = send(HttpMethod.POST, "/api/v1/amber/safety/report", anna,
                Map.of("memberId", memberId(mia), "reason", "UNWANTED_MESSAGES", "note", "Rude after the first message"));
        assertThat(report.getStatusCode().value()).isEqualTo(204);
        assertThat(db.queryForObject("select count(*) from amber_reports where status='OPEN' and reason='UNWANTED_MESSAGES' and reporter_id=" +
                "(select account_id from amber_profiles where member_id=?)", Integer.class, memberId(anna))).isEqualTo(1);

        // Contact stops both ways; Mia sees nothing that tells her about the report.
        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", mia, Map.of("body", "Hey")).getStatusCode().value()).isEqualTo(404);
        assertThat(suggestions(mia)).doesNotContain(memberId(anna));
        assertThat(suggestions(anna)).doesNotContain(memberId(mia));
        var again = send(HttpMethod.POST, "/api/v1/amber/invitations", mia, Map.of("memberId", memberId(anna)));
        assertThat(again.getStatusCode().value()).isEqualTo(404);
        assertThat(again.getBody()).contains("This person is not available.").doesNotContain("report", "block");
        assertThat(send(HttpMethod.GET, "/api/v1/amber/invitations", mia, null).getBody()).doesNotContain("Anna");

        // Leaving and rejoining Amber does not undo Anna's block.
        send(HttpMethod.DELETE, "/api/v1/amber/profile", mia, null);
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/profile", mia, null)).get("joined").asBoolean()).isFalse();
        send(HttpMethod.PUT, "/api/v1/amber/profile", mia, profile("Mia", 29, area, List.of("DATING"), List.of()));
        assertThat(suggestions(anna)).doesNotContain(memberId(mia));
        assertThat(suggestions(mia)).doesNotContain(memberId(anna));
    }

    @Test
    void ac6_echoLearningIsSharedOnlyWhenSwitchedOn() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"), List.of());
        String mia = join("Mia", area, List.of("DATING"), List.of());
        String miaAccount = db.queryForObject("select account_id from amber_profiles where member_id=?", String.class, memberId(mia));
        db.update("insert into echo_learner_profiles(account_id,language_code,goal,self_reported_level,diagnostic_score,starting_route) " +
                "values(?,'da','Talk at work','BEGINNER',0,'GUIDED')", miaAccount);

        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/profile", mia, null)).get("echoLearning").asText()).isEqualTo("Danish");
        assertThat(suggestions(anna)).doesNotContain("Danish");

        var sharing = profile("Mia", 29, area, List.of("DATING"), List.of());
        sharing.put("shareLearning", true);
        send(HttpMethod.PUT, "/api/v1/amber/profile", mia, sharing);
        assertThat(find(json(send(HttpMethod.GET, "/api/v1/amber/suggestions", anna, null)), "Mia").get("person").get("learning").asText())
                .isEqualTo("Danish");
    }

    @Test
    void deletingTheAccountRemovesItsAmberData() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"), List.of());
        String mia = join("Mia", area, List.of("DATING"), List.of());
        String invitation = invite(anna, memberId(mia));
        send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", mia, null);
        send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna, Map.of("body", "Hi"));
        String annaAccount = db.queryForObject("select account_id from amber_profiles where member_id=?", String.class, memberId(anna));
        String email = db.queryForObject("select email from accounts where id=?", String.class, annaAccount);

        var deleted = http.exchange("/api/v1/auth/account", HttpMethod.DELETE, new HttpEntity<>(Map.of("confirmEmail", email), headers(anna)), String.class);
        assertThat(deleted.getStatusCode().is2xxSuccessful()).as(deleted.getBody()).isTrue();
        assertThat(db.queryForObject("select count(*) from amber_profiles where account_id=?", Integer.class, annaAccount)).isZero();
        assertThat(db.queryForObject("select count(*) from amber_invitations where id=?", Integer.class, invitation)).isZero();
        assertThat(db.queryForObject("select count(*) from amber_messages where invitation_id=?", Integer.class, invitation)).isZero();
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/connections", mia, null))).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private String join(String name, String area, List<String> intents, List<String> interests) {
        String token = register();
        var saved = send(HttpMethod.PUT, "/api/v1/amber/profile", token, profile(name, 29, area, intents, interests));
        assertThat(saved.getStatusCode().value()).as(saved.getBody()).isEqualTo(200);
        return token;
    }

    private static Map<String, Object> profile(String name, int age, String area, List<String> intents, List<String> interests) {
        var body = new HashMap<String, Object>();
        body.put("displayName", name);
        body.put("age", age);
        body.put("confirmAdult", true);
        body.put("area", area);
        body.put("intents", intents);
        body.put("interests", interests);
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

    private String suggestions(String token) {
        var response = send(HttpMethod.GET, "/api/v1/amber/suggestions", token, null);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return response.getBody();
    }


    private static JsonNode find(JsonNode suggestions, String name) {
        for (JsonNode item : suggestions) if (name.equals(item.get("person").get("displayName").asText())) return item;
        return null;
    }

    private static String uniqueArea() { return "Town-" + UUID.randomUUID().toString().substring(0, 8); }

    private String register() {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Amber Tester",
                "email", "amber-" + UUID.randomUUID() + "@example.test", "password", PASSWORD), Map.class);
        return (String) body.get("token");
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
