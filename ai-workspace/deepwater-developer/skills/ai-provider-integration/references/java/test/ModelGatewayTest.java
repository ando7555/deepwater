package dev.course.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import dev.course.ai.ModelGateway.CompletionRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelGatewayTest {

    @Test
    void fakeRecordsWhatTheApplicationSent() {
        FakeModelGateway fake = FakeModelGateway.alwaysAnswering("Hej!");
        var result = fake.complete(CompletionRequest.ask("You are a Danish tutor.", "Say hello.", 100));
        assertEquals("Hej!", result.text());
        assertEquals("You are a Danish tutor.", fake.received().getFirst().system());
    }

    @Test
    void openAiCompatibleAdapterSpeaksTheChatCompletionsFormat() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String[] seenBody = new String[1];
        String[] seenAuth = new String[1];
        server.createContext("/v1/chat/completions", exchange -> {
            seenBody[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seenAuth[0] = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] reply = """
                {"model":"local-model","choices":[{"message":{"role":"assistant","content":"Hej!"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":12,"completion_tokens":3}}""".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new OpenAiCompatibleGateway("test", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "local-model", "test-key", Duration.ofSeconds(5));
            var result = gateway.complete(CompletionRequest.ask("Be brief.", "Say hello in Danish.", 50));
            assertEquals("Hej!", result.text());
            assertEquals(12, result.inputTokens());
            assertEquals("stop", result.stopReason());
            assertEquals("Bearer test-key", seenAuth[0]);
            assertEquals(true, seenBody[0].contains("\"role\":\"system\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rateLimitIsReportedAsRetryable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> { exchange.sendResponseHeaders(429, -1); exchange.close(); });
        server.start();
        try {
            var gateway = new OpenAiCompatibleGateway("test", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "m", null, Duration.ofSeconds(5));
            var error = assertThrows(ModelGateway.ModelGatewayException.class,
                    () -> gateway.complete(CompletionRequest.ask(null, "hi", 10)));
            assertEquals(true, error.retryable());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerIsChosenByConfiguration() {
        assertInstanceOf(FakeModelGateway.class, ModelGateways.fromEnvironment(Map.of()));
        assertInstanceOf(OpenAiCompatibleGateway.class, ModelGateways.fromEnvironment(Map.of(
                "AI_PROVIDER", "openai-compatible", "AI_BASE_URL", "http://localhost:11434/v1", "AI_MODEL", "llama3.2")));
        assertThrows(IllegalStateException.class, () -> ModelGateways.fromEnvironment(Map.of(
                "AI_PROVIDER", "openai-compatible", "AI_BASE_URL", "http://x/v1", "AI_MODEL", "m", "AI_API_KEY_ENV", "MISSING_KEY")));
    }
}
