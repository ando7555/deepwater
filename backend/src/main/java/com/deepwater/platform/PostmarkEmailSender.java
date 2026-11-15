package com.deepwater.platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/** Sends email through Postmark's HTTP API. Without a server token and sender address it only logs that nothing was sent. */
class PostmarkEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(PostmarkEmailSender.class);

    private final String serverToken;
    private final String from;
    private final String apiUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    PostmarkEmailSender(String serverToken, String from, String apiUrl) {
        this.serverToken = serverToken == null ? "" : serverToken;
        this.from = from == null ? "" : from;
        this.apiUrl = apiUrl;
    }

    @Override
    public boolean isConfigured() {
        return !serverToken.isBlank() && !from.isBlank();
    }

    @Override
    public void send(String toEmail, String subject, String textBody) {
        if (!isConfigured()) {
            log.warn("Postmark is not configured (POSTMARK_SERVER_TOKEN, MAIL_FROM); no email was sent.");
            return;
        }
        var payload = Map.of("From", from, "To", toEmail, "Subject", subject, "TextBody", textBody, "MessageStream", "outbound");
        try {
            var response = http.send(HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Postmark-Server-Token", serverToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                log.warn("Postmark rejected an email with status {}: {}", response.statusCode(), response.body());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending an email through Postmark.");
        } catch (Exception ex) {
            log.warn("Could not reach Postmark to send an email: {}", ex.getMessage());
        }
    }
}
