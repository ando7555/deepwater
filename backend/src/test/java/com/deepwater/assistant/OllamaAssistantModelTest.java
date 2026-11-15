package com.deepwater.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepwater.assistant.AssistantModel.AssistantText;
import com.deepwater.assistant.AssistantModel.Conversation;
import com.deepwater.assistant.AssistantModel.ModelStep;
import com.deepwater.assistant.AssistantModel.Stop;
import com.deepwater.assistant.AssistantModel.ToolCall;
import com.deepwater.assistant.AssistantModel.ToolResult;
import com.deepwater.assistant.AssistantModel.ToolResults;
import com.deepwater.assistant.AssistantModel.ToolSpec;
import com.deepwater.assistant.AssistantModel.UserText;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Ollama adapter against a stand-in Ollama server that answers in the documented /api/chat shape.
 * No real Ollama runs in CI; this checks what we send and how we read the reply.
 */
class OllamaAssistantModelTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<String> replies = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            requests.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] body = replies.removeFirst().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private OllamaAssistantModel model() {
        return new OllamaAssistantModel("http://127.0.0.1:" + server.getAddress().getPort(), "hermes3", Duration.ofSeconds(5));
    }

    private static final List<ToolSpec> TOOLS = List.of(new ToolSpec("echo_cards", "The person's phrase cards",
            Map.of("type", "object", "properties", Map.of("dueOnly", Map.of("type", "boolean")))));

    @Test
    void readsToolCallsAndSendsTheDocumentedRequest() {
        replies.add("""
                {"model":"hermes3","message":{"role":"assistant","content":"",
                 "tool_calls":[{"function":{"name":"echo_cards","arguments":{"dueOnly":true}}}]},
                 "done_reason":"stop","done":true}""");
        var step = model().next(new Conversation("Be brief.", TOOLS,
                List.of(new UserText("hi"), new AssistantText("Hello!"), new UserText("Which cards are due?"))));

        assertThat(step.stop()).isEqualTo(Stop.TOOLS);
        assertThat(step.toolCalls()).hasSize(1);
        assertThat(step.toolCalls().getFirst().name()).isEqualTo("echo_cards");
        assertThat(step.toolCalls().getFirst().input()).containsEntry("dueOnly", true);
        assertThat(step.toolCalls().getFirst().id()).isNotBlank(); // Ollama sends none; we make one

        JsonNode sent = requests.getFirst();
        assertThat(sent.get("model").asString()).isEqualTo("hermes3");
        assertThat(sent.get("stream").asBoolean()).isFalse();
        assertThat(sent.at("/messages/0/role").asString()).isEqualTo("system");
        assertThat(sent.at("/messages/0/content").asString()).isEqualTo("Be brief.");
        assertThat(sent.at("/messages/2/role").asString()).isEqualTo("assistant");
        assertThat(sent.at("/tools/0/type").asString()).isEqualTo("function");
        assertThat(sent.at("/tools/0/function/name").asString()).isEqualTo("echo_cards");
        assertThat(sent.at("/tools/0/function/parameters/type").asString()).isEqualTo("object");
    }

    @Test
    void sendsToolResultsBackByToolNameAndReadsTheAnswer() {
        replies.add("""
                {"model":"hermes3","message":{"role":"assistant","content":"You have 2 cards due."},"done_reason":"stop","done":true}""");
        var call = new ToolCall("c1", "echo_cards", Map.of("dueOnly", true));
        var previous = new AssistantModel.Step("", List.of(call), Stop.TOOLS, null);
        var step = model().next(new Conversation("Be brief.", TOOLS, List.of(new UserText("Which cards are due?"),
                new ModelStep(previous), new ToolResults(List.of(new ToolResult("c1", "echo_cards", "{\"count\":2}", false))))));

        assertThat(step.stop()).isEqualTo(Stop.DONE);
        assertThat(step.text()).isEqualTo("You have 2 cards due.");
        JsonNode messages = requests.getFirst().get("messages");
        assertThat(messages.get(2).at("/tool_calls/0/function/name").asString()).isEqualTo("echo_cards");
        assertThat(messages.get(2).at("/tool_calls/0/function/arguments/dueOnly").asBoolean()).isTrue();
        assertThat(messages.get(3).get("role").asString()).isEqualTo("tool");
        assertThat(messages.get(3).get("tool_name").asString()).isEqualTo("echo_cards");
        assertThat(messages.get(3).get("content").asString()).isEqualTo("{\"count\":2}");
    }

    @Test
    void aLengthStopIsReportedAsCutOff() {
        replies.add("""
                {"model":"hermes3","message":{"role":"assistant","content":"You have"},"done_reason":"length","done":true}""");
        assertThat(model().next(new Conversation("s", TOOLS, List.of(new UserText("q")))).stop()).isEqualTo(Stop.CUT_OFF);
    }

    @Test
    void providerIsASetting() {
        var config = new AssistantConfiguration();
        assertThat(config.assistantModel("off", "", "m", "medium", "http://x", "hermes3", 5).enabled()).isFalse();
        assertThat(config.assistantModel("auto", "", "m", "medium", "http://x", "hermes3", 5).enabled()).isFalse();
        assertThat(config.assistantModel("ollama", "", "m", "medium", "http://x", "hermes3", 5).description()).isEqualTo("ollama hermes3");
        assertThat(config.assistantModel("auto", "k", "claude-opus-5-5", "low", "http://x", "hermes3", 5).description())
                .isEqualTo("anthropic claude-opus-5-5");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> config.assistantModel("gpt", "", "m", "medium", "http://x", "h", 5))
                .hasMessageContaining("Unknown ASSISTANT_PROVIDER");
    }
}
