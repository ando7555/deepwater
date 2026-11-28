package com.deepwater.platform;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;

/** Account management: the person's data download, signing out everywhere, and deletion of the shared profile. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AccountManagementApiTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static final String UPDATE = "mutation($input:PersonProfileInput!){updateMyProfile(input:$input){publicName languages{language level}}}";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void theDataDownloadHoldsThePersonsDataButNoSecrets() {
        String email = "export-" + UUID.randomUUID() + "@example.test";
        String token = register(email);
        graph(UPDATE, Map.of("input", Map.of("name", "Export Person", "publicName", "Ex", "bio", "Hello there",
                "languages", List.of(Map.of("language", "Armenian", "level", "NATIVE")))), token);

        var response = http.exchange("/api/v1/account/export", HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment", "deepwater-data-");
        assertThat(response.getBody()).contains(email, "Export Person", "Hello there", "Armenian", "NATIVE", "amber_messages_written")
                .doesNotContain("password_hash", "$2a$", token);
        assertThat(http.exchange("/api/v1/account/export", HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void signingOutEverywhereEndsEverySession() {
        String email = "devices-" + UUID.randomUUID() + "@example.test";
        String phone = register(email);
        String laptop = (String) http.postForObject("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class).get("token");
        var ended = http.exchange("/api/v1/account/sign-out-everywhere", HttpMethod.POST, new HttpEntity<>(headers(laptop)), Map.class);
        assertThat(ended.getBody()).containsEntry("sessionsEnded", 2);
        assertThat(http.exchange("/api/v1/account/photos", HttpMethod.GET, new HttpEntity<>(headers(phone)), String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(http.exchange("/api/v1/account/photos", HttpMethod.GET, new HttpEntity<>(headers(laptop)), String.class).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void languagesAreValidatedAndAnOlderClientKeepsTheSavedList() {
        String token = register("langs-" + UUID.randomUUID() + "@example.test");
        assertThat(graph(UPDATE, Map.of("input", Map.of("name", "L", "languages", List.of(Map.of("language", "Polish", "level", "GOOD")))), token))
                .contains("Polish", "GOOD");
        // A screen that does not send languages leaves them as they are.
        assertThat(graph(UPDATE, Map.of("input", Map.of("name", "L")), token)).contains("Polish");
        assertThat(postGraph(UPDATE, Map.of("input", Map.of("name", "L", "languages", List.of(Map.of("language", "Polish", "level", "PERFECT")))), token))
                .contains("Choose how well you know Polish.");
        assertThat(postGraph(UPDATE, Map.of("input", Map.of("name", "L", "birthDate", "2999-01-01")), token)).contains("Check your birth date.");
        assertThat(postGraph(UPDATE, Map.of("input", Map.of("name", "L", "birthDate", "27/05/1990")), token)).contains("year-month-day");
    }

    @Test
    void deletingTheAccountRemovesPhotosAndLanguages() throws Exception {
        String email = "gone-" + UUID.randomUUID() + "@example.test";
        String token = register(email);
        String accountId = db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
        graph(UPDATE, Map.of("input", Map.of("name", "Gone", "languages", List.of(Map.of("language", "German", "level", "BASIC")))), token);
        db.update("insert into person_photos(id,account_id,position,content_type,bytes) values(?,?,0,'image/jpeg',?)",
                UUID.randomUUID().toString(), accountId, new byte[]{1, 2, 3});
        var deleted = http.exchange("/api/v1/auth/account", HttpMethod.DELETE, new HttpEntity<>(Map.of("confirmEmail", email), headers(token)), String.class);
        assertThat(deleted.getStatusCode().is2xxSuccessful()).as(deleted.getBody()).isTrue();
        assertThat(db.queryForObject("select count(*) from person_photos where account_id=?", Integer.class, accountId)).isZero();
        assertThat(db.queryForObject("select count(*) from person_languages where account_id=?", Integer.class, accountId)).isZero();
    }

    private String register(String email) {
        return (String) http.postForObject("/api/v1/auth/register", Map.of("name", "Account Person", "email", email, "password", PASSWORD), Map.class)
                .get("token");
    }

    private String graph(String query, Map<String, ?> variables, String token) {
        String result = postGraph(query, variables, token);
        assertThat(result).doesNotContain("\"errors\"");
        return result;
    }

    private String postGraph(String query, Map<String, ?> variables, String token) {
        return http.postForObject("/graphql", new HttpEntity<>(Map.of("query", query, "variables", variables), headers(token)), String.class);
    }

    private static HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
