package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepwater.platform.PlatformService;
import com.deepwater.platform.VerificationMailer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "deepwater.frontend.base-url=https://app.example.test/")
class EmailVerificationTest {
    private static final String PASSWORD = "a-long-password-1";
    @Autowired MockMvc mvc;
    @Autowired PlatformService platform;
    @Autowired JdbcTemplate db;
    @MockitoBean VerificationMailer mailer;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void signUpEmailsASingleUseLinkThatConfirmsTheAddress() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@example.test";
        String body = register(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value("false")).andReturn().getResponse().getContentAsString();
        String accountId = json.readTree(body).get("accountId").asText();

        String token = sentToken(email);
        assertThat(db.queryForObject("select token_hash from email_verification_tokens where account_id=?", String.class, accountId))
                .doesNotContain(token);

        verifyEmail(token).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(db.queryForObject("select email_verified from accounts where id=?", Boolean.class, accountId)).isTrue();
        verifyEmail(token).andExpect(status().isBadRequest());
        assertThat(platform.login(email, PASSWORD).get("emailVerified")).isEqualTo("true");
    }

    @Test
    void expiredOrUnknownLinksAreRejected() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.test";
        register(email).andExpect(status().isOk());
        String token = sentToken(email);
        db.update("update email_verification_tokens set expires_at=current_timestamp - interval '1' hour where account_id=(select id from accounts where email=?)", email);
        verifyEmail(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This confirmation link is invalid or has expired. Request a new one."));
        verifyEmail("made-up-token").andExpect(status().isBadRequest());
    }

    @Test
    void resendIsLimitedToOncePerMinute() throws Exception {
        String email = "resend-" + UUID.randomUUID() + "@example.test";
        String body = register(email).andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();
        mvc.perform(post("/api/v1/auth/verify-email/resend").header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
        db.update("update email_verification_tokens set created_at=current_timestamp - interval '2' minute where account_id=(select id from accounts where email=?)", email);
        clearInvocations(mailer);
        mvc.perform(post("/api/v1/auth/verify-email/resend").header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());
        verify(mailer).sendVerification(eq(email), anyString(), anyString());
        mvc.perform(post("/api/v1/auth/verify-email/resend")).andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignInKeepsThePasswordOfAVerifiedAccount() throws Exception {
        String email = "kept-" + UUID.randomUUID() + "@example.test";
        register(email);
        verifyEmail(sentToken(email)).andExpect(status().isOk());
        String accountId = platform.loginOrRegisterWithGoogle("sub-" + email, email, "Kept");
        assertThat(platform.login(email, PASSWORD).get("accountId")).isEqualTo(accountId);
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Verifier\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private String sentToken(String email) {
        var link = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).sendVerification(eq(email), eq("Verifier"), link.capture());
        assertThat(link.getValue()).startsWith("https://app.example.test/?verify_email=");
        return link.getValue().substring(link.getValue().indexOf('=') + 1);
    }
}
