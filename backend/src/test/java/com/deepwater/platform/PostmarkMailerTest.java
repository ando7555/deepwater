package com.deepwater.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PostmarkMailerTest {
    @Test
    void postsTheVerificationEmailToPostmarkWithTheServerToken() throws Exception {
        var token = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/email", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Postmark-Server-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var mailer = new PostmarkMailer();
            ReflectionTestUtils.setField(mailer, "serverToken", "server-token-123");
            ReflectionTestUtils.setField(mailer, "from", "hello@deepwater.test");
            ReflectionTestUtils.setField(mailer, "apiUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/email");
            mailer.sendVerification("learner@example.test", "Ana", "https://app.example.test/?verify_email=abc");
        } finally {
            server.stop(0);
        }
        assertThat(token.get()).isEqualTo("server-token-123");
        JsonNode sent = new ObjectMapper().readTree(body.get());
        assertThat(sent.get("From").asText()).isEqualTo("hello@deepwater.test");
        assertThat(sent.get("To").asText()).isEqualTo("learner@example.test");
        assertThat(sent.get("MessageStream").asText()).isEqualTo("outbound");
        assertThat(sent.get("TextBody").asText()).contains("Hi Ana", "https://app.example.test/?verify_email=abc");
    }

    @Test
    void doesNothingWhenPostmarkIsNotConfigured() {
        var mailer = new PostmarkMailer();
        ReflectionTestUtils.setField(mailer, "serverToken", "");
        ReflectionTestUtils.setField(mailer, "from", "");
        ReflectionTestUtils.setField(mailer, "apiUrl", "http://127.0.0.1:1/email");
        assertThatNoException().isThrownBy(() ->
                mailer.sendVerification("learner@example.test", "Ana", "https://app.example.test/?verify_email=abc"));
    }
}
