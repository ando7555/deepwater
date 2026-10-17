package com.deepwater.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleAuthService {
    @Value("${deepwater.google.client-id:}") private String clientId;
    @Value("${deepwater.google.client-secret:}") private String clientSecret;
    @Value("${deepwater.google.redirect-uri:}") private String redirectUri;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public String buildAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth?"
            + "client_id=" + enc(clientId)
            + "&redirect_uri=" + enc(redirectUri)
            + "&response_type=code"
            + "&scope=" + enc("openid email profile")
            + "&state=" + enc(state)
            + "&prompt=select_account";
    }

    /** The state cookie is marked Secure whenever Google redirects back over HTTPS. */
    public boolean usesHttps() {
        return redirectUri != null && redirectUri.startsWith("https://");
    }

    public GoogleUser fetchGoogleUser(String code) throws Exception {
        String form = "code=" + enc(code) + "&client_id=" + enc(clientId)
            + "&client_secret=" + enc(clientSecret) + "&redirect_uri=" + enc(redirectUri)
            + "&grant_type=authorization_code";
        var tokenResp = http.send(HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
            HttpResponse.BodyHandlers.ofString());
        if (tokenResp.statusCode() != 200)
            throw new IllegalStateException("Google token exchange failed with status " + tokenResp.statusCode());
        String accessToken = (String) json.readValue(tokenResp.body(), Map.class).get("access_token");
        if (accessToken == null) throw new IllegalStateException("Google token response had no access token");
        var userResp = http.send(HttpRequest.newBuilder()
            .uri(URI.create("https://openidconnect.googleapis.com/v1/userinfo"))
            .header("Authorization", "Bearer " + accessToken).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if (userResp.statusCode() != 200)
            throw new IllegalStateException("Google userinfo failed with status " + userResp.statusCode());
        Map<?,?> u = json.readValue(userResp.body(), Map.class);
        return new GoogleUser((String)u.get("sub"), (String)u.get("email"),
            (String)u.get("name"), Boolean.TRUE.equals(u.get("email_verified")));
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    public record GoogleUser(String sub, String email, String name, boolean emailVerified) {}
}
