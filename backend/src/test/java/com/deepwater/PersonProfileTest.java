package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PersonProfileTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static final String PROFILE = "query { myProfile { name email phone city country nativeLanguage about } }";
    private static final String UPDATE = "mutation($input:PersonProfileInput!){updateMyProfile(input:$input){name phone city country nativeLanguage about}}";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void phoneGivenAtSignUpIsNormalisedAndShownOnTheProfile() {
        String email = "phone-" + UUID.randomUUID() + "@example.test";
        String token = register(email, "+45 12-34 (56) 78");
        assertThat(graph(PROFILE, Map.of(), token)).contains("\"phone\":\"+4512345678\"", "\"city\":null", email);
    }

    @Test
    void signUpWithABadPhoneIsRejectedBeforeTheAccountIsCreated() {
        String email = "bad-phone-" + UUID.randomUUID() + "@example.test";
        var response = http.postForEntity("/api/v1/auth/register",
                Map.of("name", "Bad Phone", "email", email, "password", PASSWORD, "phone", "call me"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(db.queryForObject("select count(*) from accounts where email=?", Integer.class, email)).isZero();
    }

    @Test
    void learnerUpdatesTheirDetailsAndBlankFieldsAreCleared() {
        String token = register("profile-" + UUID.randomUUID() + "@example.test", null);
        var input = new HashMap<String, Object>(Map.of("name", "Ana Learner", "phone", "+49 30 1234567", "city", "Aarhus",
                "country", "Denmark", "nativeLanguage", "Portuguese", "about", "I want to talk with my neighbours."));
        assertThat(graph(UPDATE, Map.of("input", input), token))
                .contains("\"name\":\"Ana Learner\"", "\"phone\":\"+49301234567\"", "\"city\":\"Aarhus\"", "\"nativeLanguage\":\"Portuguese\"");

        input.put("phone", " "); input.put("about", "");
        assertThat(graph(UPDATE, Map.of("input", input), token)).contains("\"phone\":null", "\"about\":null", "\"city\":\"Aarhus\"");
        assertThat(postGraph(UPDATE, Map.of("input", Map.of("name", " ")), token)).contains("Enter your name in 1–160 characters.");
        assertThat(postGraph(UPDATE, Map.of("input", Map.of("name", "Ana", "city", "x".repeat(121))), token))
                .contains("City can be at most 120 characters.");
    }

    @Test
    void profileIsPrivateAndDeletedWithTheAccount() {
        assertThat(postGraph(PROFILE, Map.of(), null)).contains("Sign in to continue.");
        String email = "leaving-" + UUID.randomUUID() + "@example.test";
        String token = register(email, "+4512345678");
        String accountId = db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        http.exchange("/api/v1/auth/account", HttpMethod.DELETE, new HttpEntity<>(Map.of("confirmEmail", email), headers), String.class);
        assertThat(db.queryForObject("select count(*) from person_profiles where account_id=?", Integer.class, accountId)).isZero();
    }

    private String register(String email, String phone) {
        var body = new HashMap<String, String>(Map.of("name", "Profile Learner", "email", email, "password", PASSWORD));
        if (phone != null) body.put("phone", phone);
        var response = http.postForObject("/api/v1/auth/register", body, Map.class);
        return (String) response.get("token");
    }

    private String graph(String query, Map<String, ?> variables, String token) {
        String result = postGraph(query, variables, token);
        assertThat(result).doesNotContain("\"errors\"");
        return result;
    }

    private String postGraph(String query, Map<String, ?> variables, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.postForObject("/graphql", new HttpEntity<>(Map.of("query", query, "variables", variables), headers), String.class);
    }
}
