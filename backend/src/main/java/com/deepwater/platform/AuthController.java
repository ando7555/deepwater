package com.deepwater.platform;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    static final String STATE_COOKIE = "dw_oauth_state";
    private static final String STATE_COOKIE_PATH = "/api/v1/auth/google";
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final PlatformService platform;
    private final GoogleAuthService google;
    private final EmailVerificationService verification;
    @Value("${deepwater.frontend.base-url:http://localhost:5173}") private String frontendBaseUrl;

    public AuthController(PlatformService platform, GoogleAuthService google, EmailVerificationService verification) {
        this.platform = platform;
        this.google = google;
        this.verification = verification;
    }

    @PostMapping("/register")
    Map<String, String> register(@RequestBody Credentials body) {
        var session = platform.register(body.name(), body.email(), body.password());
        verification.sendVerification(session.get("accountId"));
        return session;
    }

    /** Confirms the email address from the link in the verification email. */
    @PostMapping("/verify-email")
    Map<String, Boolean> verifyEmail(@RequestBody VerifyRequest body) {
        verification.verify(body.token());
        return Map.of("emailVerified", true);
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@RequestHeader(value = "Authorization", required = false) String authorization) {
        verification.resend(platform.requireAccount(authorization));
    }

    @PostMapping("/login")
    Map<String, String> login(@RequestBody Credentials body) {
        return platform.login(body.email(), body.password());
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DeleteRequest body) {
        platform.deleteAccount(authorization, body.confirmEmail());
    }

    /** Starts Google sign-in; the state is bound to this browser through a short-lived HttpOnly cookie. */
    @GetMapping("/google")
    void googleStart(HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        response.addHeader("Set-Cookie", stateCookie(state, 600).toString());
        response.sendRedirect(google.buildAuthorizationUrl(state));
    }

    /** Google redirects here. The browser is sent back to the frontend with a one-time code, never a session token. */
    @GetMapping("/google/callback")
    void googleCallback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.addHeader("Set-Cookie", stateCookie("", 0).toString());
        if (error != null || code == null || !stateMatches(state, request)) {
            frontendRedirect(response, "login_error", error != null ? "cancelled" : "failed");
            return;
        }
        try {
            var user = google.fetchGoogleUser(code);
            if (!user.emailVerified() || user.sub() == null || user.email() == null) {
                frontendRedirect(response, "login_error", "unverified");
                return;
            }
            String accountId = platform.loginOrRegisterWithGoogle(user.sub(), user.email(), user.name());
            frontendRedirect(response, "login_code", platform.issueLoginCode(accountId));
        } catch (Exception ex) {
            log.warn("Google sign-in failed: {}", ex.getMessage());
            frontendRedirect(response, "login_error", "failed");
        }
    }

    /** Exchanges the one-time code from the Google redirect for a normal session. */
    @PostMapping("/google/exchange")
    Map<String, String> googleExchange(@RequestBody LoginCode body) {
        return platform.redeemLoginCode(body.code());
    }

    /** Sends the reason as JSON ({"detail": ...}) so the sign-in page can show it. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }

    private boolean stateMatches(String state, HttpServletRequest request) {
        if (state == null || request.getCookies() == null) return false;
        return Arrays.stream(request.getCookies())
                .filter(c -> STATE_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .anyMatch(v -> MessageDigest.isEqual(v.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8)));
    }

    private ResponseCookie stateCookie(String value, long maxAgeSeconds) {
        // Lax is enough: Google returns to the callback with a top-level GET navigation.
        return ResponseCookie.from(STATE_COOKIE, value).httpOnly(true).secure(google.usesHttps())
                .sameSite("Lax").path(STATE_COOKIE_PATH).maxAge(maxAgeSeconds).build();
    }

    private void frontendRedirect(HttpServletResponse response, String param, String value) throws IOException {
        // The frontend is served from its root; other paths are not routed by the production web server.
        response.sendRedirect(frontendBaseUrl.replaceAll("/+$", "") + "/?" + param + "="
                + java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    record Credentials(String name, String email, String password) {}
    record LoginCode(String code) {}
    record DeleteRequest(String confirmEmail) {}
    record VerifyRequest(String token) {}
}
