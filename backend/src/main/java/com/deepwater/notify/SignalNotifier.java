package com.deepwater.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends Signal messages through a separately running signal-cli-rest-api service (an unofficial, open-source wrapper
 * around signal-cli; Signal has no official bot API). Endpoint: {@code POST {api-url}/v2/send} with the sender's
 * registered number and the recipients' numbers.
 */
class SignalNotifier implements Notifier {
    private static final Logger log = LoggerFactory.getLogger(SignalNotifier.class);

    private final String apiUrl;
    private final String sender;
    private final List<String> recipients;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    SignalNotifier(String apiUrl, String sender, List<String> recipients) {
        String url = apiUrl == null ? "" : apiUrl.strip();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        this.apiUrl = url;
        this.sender = sender == null ? "" : sender.strip();
        this.recipients = List.copyOf(recipients);
    }

    @Override
    public boolean isConfigured() {
        return !apiUrl.isBlank() && !sender.isBlank() && !recipients.isEmpty();
    }

    @Override
    public void send(String text) {
        if (!isConfigured()) {
            log.warn("Signal is not configured (SIGNAL_API_URL, SIGNAL_SENDER, SIGNAL_RECIPIENTS); notification skipped.");
            return;
        }
        var payload = Map.of("message", text, "number", sender, "recipients", recipients);
        try {
            var response = http.send(HttpRequest.newBuilder(URI.create(apiUrl + "/v2/send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                log.warn("The Signal service rejected a notification with status {}: {}", response.statusCode(), response.body());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending a Signal notification.");
        } catch (Exception ex) {
            log.warn("Could not reach the Signal service: {}", ex.getMessage());
        }
    }
}
