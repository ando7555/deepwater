package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepwater.platform.GoogleAuthService;
import com.deepwater.platform.GoogleAuthService.GoogleUser;
import com.deepwater.platform.PlatformService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "deepwater.frontend.base-url=https://app.example.test")
class GoogleSignInTest {
    @Autowired MockMvc mvc;
    @Autowired PlatformService platform;
    @Autowired JdbcTemplate db;
    @MockitoBean GoogleAuthService google;

    @Test
    void signInSetsStateCookieAndReturnsOneTimeCodeThatYieldsASession() throws Exception {
        String email = "google-" + UUID.randomUUID() + "@example.test";
        when(google.buildAuthorizationUrl(anyString())).thenAnswer(i -> "https://accounts.google.com/auth?state=" + i.getArgument(0));
        when(google.fetchGoogleUser("good-code")).thenReturn(new GoogleUser("sub-" + email, email, null, true));

        var start = mvc.perform(get("/api/v1/auth/google")).andExpect(status().isFound()).andReturn().getResponse();
        Cookie state = start.getCookie("dw_oauth_state");
        assertThat(state.isHttpOnly()).isTrue();
        assertThat(start.getRedirectedUrl()).endsWith("state=" + state.getValue());

        String redirect = mvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "good-code").param("state", state.getValue()).cookie(state))
                .andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith("https://app.example.test/?login_code=").doesNotContain("token");
        String code = redirect.substring(redirect.indexOf('=') + 1);

        mvc.perform(post("/api/v1/auth/google/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value(email.substring(0, email.indexOf('@'))))
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/v1/auth/google/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackRejectsMissingOrMismatchedStateAndCancellation() throws Exception {
        String noCookie = mvc.perform(get("/api/v1/auth/google/callback").param("code", "c").param("state", "s"))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(noCookie).isEqualTo("https://app.example.test/?login_error=failed");

        String mismatch = mvc.perform(get("/api/v1/auth/google/callback").param("code", "c").param("state", "s")
                        .cookie(new Cookie("dw_oauth_state", "other")))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(mismatch).isEqualTo("https://app.example.test/?login_error=failed");

        String cancelled = mvc.perform(get("/api/v1/auth/google/callback").param("error", "access_denied")
                        .param("state", "s").cookie(new Cookie("dw_oauth_state", "s")))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(cancelled).isEqualTo("https://app.example.test/?login_error=cancelled");
    }

    @Test
    void googleIdentityTakesOverUnverifiedPasswordAccountWithTheSameEmail() {
        String email = "both-" + UUID.randomUUID() + "@example.test";
        var registered = platform.register("Both Ways", email, "a-long-password-1");
        String accountId = registered.get("accountId");

        assertThat(platform.loginOrRegisterWithGoogle("sub-" + email, email.toUpperCase(), "Both Ways")).isEqualTo(accountId);
        assertThat(platform.loginOrRegisterWithGoogle("sub-" + email, email, "Both Ways")).isEqualTo(accountId);
        // Whoever set the password never proved they own the email, so the password and its sessions stop working.
        assertThatThrownBy(() -> platform.login(email, "a-long-password-1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> platform.requireAccount("Bearer " + registered.get("token"))).isInstanceOf(ResponseStatusException.class);
        assertThat(db.queryForObject("select google_sub from accounts where id=?", String.class, accountId)).isEqualTo("sub-" + email);

        String googleOnly = "google-only-" + UUID.randomUUID() + "@example.test";
        platform.loginOrRegisterWithGoogle("sub-" + googleOnly, googleOnly, "Google Only");
        assertThatThrownBy(() -> platform.login(googleOnly, "a-long-password-1"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
    }
}
