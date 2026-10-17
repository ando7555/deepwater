package com.deepwater.platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final PlatformService platform;
    private final GoogleAuthService google;
    @Value("${deepwater.frontend.base-url:http://localhost:5173}") private String frontendBaseUrl;
    private final ConcurrentHashMap<String, Boolean> pendingStates = new ConcurrentHashMap<>();

    public AuthController(PlatformService platform, GoogleAuthService google) {
        this.platform = platform;
        this.google = google;
    }

    @PostMapping("/register")
    Map<String, String> register(@RequestBody Credentials body) {
        return platform.register(body.name(), body.email(), body.password());
    }

    @PostMapping("/login")
    Map<String, String> login(@RequestBody Credentials body) {
        return platform.login(body.email(), body.password());
    }

    @GetMapping("/google")
    void googleStart(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, Boolean.TRUE);
        response.sendRedirect(google.buildAuthorizationUrl(state));
    }

    @GetMapping("/google/callback")
    void googleCallback(@RequestParam String code, @RequestParam String state,
            jakarta.servlet.http.HttpServletResponse response) throws Exception {
        if (!pendingStates.remove(state)) { response.sendError(400, "Invalid OAuth state."); return; }
        var u = google.fetchGoogleUser(code);
        if (!u.emailVerified()) { response.sendError(400, "Google email not verified."); return; }
        var session = platform.loginOrRegisterWithGoogle(u.sub(), u.email(), u.name());
        response.sendRedirect(frontendBaseUrl + "/auth/callback?token=" + session.get("token")
            + "&name=" + java.net.URLEncoder.encode(session.get("name"), java.nio.charset.StandardCharsets.UTF_8)
            + "&email=" + java.net.URLEncoder.encode(session.get("email"), java.nio.charset.StandardCharsets.UTF_8)
            + "&accountId=" + session.get("accountId")
            + "&role=" + session.get("role"));
    }

    record Credentials(String name, String email, String password) {}
}
