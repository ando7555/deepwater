package com.deepwater.platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Sends verification emails through Postmark's HTTP API. Without a server token it only logs that nothing was sent. */
@Component
class PostmarkMailer implements VerificationMailer {
    private static final Logger log = LoggerFactory.getLogger(PostmarkMailer.class);

    @Value("${deepwater.postmark.server-token:}") private String serverToken;
    @Value("${deepwater.postmark.from:}") private String from;
    @Value("${deepwater.postmark.api-url:https://api.postmarkapp.com/email}") private String apiUrl;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public void sendVerification(String toEmail, String name, String verifyLink) {
        if (serverToken.isBlank() || from.isBlank()) {
            log.warn("Postmark is not configured (POSTMARK_SERVER_TOKEN, MAIL_FROM); no verification email was sent.");
            return;
        }
        String body = "Hi " + name + ",\n\nConfirm your email address for Deepwater Echo by opening this link:\n\n"
                + verifyLink + "\n\nThe link works once and expires in 24 hours. If you did not sign up, you can ignore this email.\n";
        var payload = Map.of("From", from, "To", toEmail, "Subject", "Confirm your email for Deepwater Echo",
                "TextBody", body, "MessageStream", "outbound");
        try {
            var response = http.send(HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Postmark-Server-Token", serverToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                log.warn("Postmark rejected the verification email with status {}: {}", response.statusCode(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending a verification email through Postmark.");
        } catch (Exception ex) {
            log.warn("Could not reach Postmark to send a verification email: {}", ex.getMessage());
        }
    }
}
