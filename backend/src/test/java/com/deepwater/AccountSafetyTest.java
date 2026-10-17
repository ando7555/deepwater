package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepwater.platform.PlatformService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "deepwater.teacher.email=safety-teacher@example.test")
class AccountSafetyTest {
    private static final String PASSWORD = "a-long-password-1";
    @Autowired MockMvc mvc;
    @Autowired PlatformService platform;
    @Autowired JdbcTemplate db;

    @Test
    void repeatedFailedLoginsAreBlockedEvenWithTheRightPassword() throws Exception {
        String email = "throttle-" + UUID.randomUUID() + "@example.test";
        platform.register("Throttled", email, PASSWORD);
        for (int i = 0; i < 5; i++) login(email, "wrong-password-123").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many sign-in attempts. Wait 15 minutes and try again."));
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.test";
        platform.register("Resets", email, PASSWORD);
        for (int i = 0; i < 4; i++) login(email, "wrong-password-123").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) login(email, "wrong-password-123").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void learnerCanDeleteTheirAccountAfterConfirmingTheirEmail() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@example.test";
        var session = platform.register("Leaving", email, PASSWORD);
        String accountId = session.get("accountId");
        db.update("insert into echo_learner_profiles(account_id,goal,self_reported_level,diagnostic_score,starting_route) values(?,?,?,?,?)",
                accountId, "Order coffee", "new", 0, "GUIDED");

        deleteAccount(session.get("token"), "someone-else@example.test").andExpect(status().isBadRequest());
        deleteAccount(session.get("token"), email.toUpperCase()).andExpect(status().isNoContent());

        assertThat(db.queryForObject("select count(*) from accounts where id=?", Integer.class, accountId)).isZero();
        assertThat(db.queryForObject("select count(*) from echo_learner_profiles where account_id=?", Integer.class, accountId)).isZero();
        assertThatThrownBy(() -> platform.requireAccount("Bearer " + session.get("token"))).isInstanceOf(ResponseStatusException.class);
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        // The address can be registered again from scratch.
        assertThat(platform.register("Back again", email, PASSWORD).get("accountId")).isNotEqualTo(accountId);
    }

    @Test
    void deletionNeedsASignedInNonTeacherAccount() throws Exception {
        deleteAccount("not-a-token", "x@example.test").andExpect(status().isUnauthorized());
        var teacher = platform.register("Teacher", "safety-teacher@example.test", PASSWORD);
        deleteAccount(teacher.get("token"), "safety-teacher@example.test").andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions deleteAccount(String token, String confirmEmail) throws Exception {
        return mvc.perform(delete("/api/v1/auth/account").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmEmail", confirmEmail))));
    }

    private static String json(Map<String, String> values) {
        return values.entrySet().stream().map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
