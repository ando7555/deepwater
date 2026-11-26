package com.deepwater.assistant;

import static org.assertj.core.api.Assertions.assertThat;

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

/** The OpenAI-style adapter (Gemini, Groq, ...) against a stand-in server answering in the /chat/completions shape. */
class OpenAiCompatibleAssistantModelTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<String> auth = new ArrayList<>();
    private final List<String> replies = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
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

    private OpenAiCompatibleAssistantModel model() {
        return new OpenAiCompatibleAssistantModel("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "key-1",
                "gemini-3.5-flash-lite", "Google", Duration.ofSeconds(5));
    }

    private static final List<ToolSpec> TOOLS = List.of(new ToolSpec("echo_cards", "The person's phrase cards",
            Map.of("type", "object", "properties", Map.of("dueOnly", Map.of("type", "boolean")))));

    @Test
    void readsToolCallsWithStringArgumentsAndSendsKeyAndTools() {
        replies.add("""
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                 "tool_calls":[{"id":"call_1","type":"function","function":{"name":"echo_cards","arguments":"{\\"dueOnly\\":true}"}}]}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":3}}""");
        var step = model().next(new Conversation("Be brief.", TOOLS, List.of(new UserText("Which cards are due?"))));

        assertThat(step.stop()).isEqualTo(Stop.TOOLS);
        assertThat(step.toolCalls().getFirst().id()).isEqualTo("call_1");
        assertThat(step.toolCalls().getFirst().input()).containsEntry("dueOnly", true);
        assertThat(auth.getFirst()).isEqualTo("Bearer key-1");
        JsonNode sent = requests.getFirst();
        assertThat(sent.get("model").asString()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(sent.at("/messages/0/role").asString()).isEqualTo("system");
        assertThat(sent.at("/tools/0/function/name").asString()).isEqualTo("echo_cards");
    }

    @Test
    void sendsToolResultsByCallIdAndReadsTheAnswer() {
        replies.add("""
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"You have 2 cards due."}}]}""");
        var call = new ToolCall("call_1", "echo_cards", Map.of("dueOnly", true));
        var previous = new AssistantModel.Step("", List.of(call), Stop.TOOLS, null);
        var step = model().next(new Conversation("s", TOOLS, List.of(new UserText("q"), new ModelStep(previous),
                new ToolResults(List.of(new ToolResult("call_1", "echo_cards", "{\"count\":2}", false))))));

        assertThat(step.stop()).isEqualTo(Stop.DONE);
        assertThat(step.text()).isEqualTo("You have 2 cards due.");
        JsonNode messages = requests.getFirst().get("messages");
        assertThat(messages.get(2).at("/tool_calls/0/id").asString()).isEqualTo("call_1");
        assertThat(messages.get(2).at("/tool_calls/0/function/arguments").asString()).isEqualTo("{\"dueOnly\":true}");
        assertThat(messages.get(3).get("role").asString()).isEqualTo("tool");
        assertThat(messages.get(3).get("tool_call_id").asString()).isEqualTo("call_1");
    }

    @Test
    void lengthIsCutOffAndTheCompanyIsNamedForConsent() {
        replies.add("""
                {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"You"}}]}""");
        var model = model();
        assertThat(model.next(new Conversation("s", TOOLS, List.of(new UserText("q")))).stop()).isEqualTo(Stop.CUT_OFF);
        assertThat(model.sharesDataWith()).isEqualTo("Google");
    }

    @Test
    void geminiAndGroqNeedOnlyAKey() {
        var config = new AssistantConfiguration();
        assertThat(config.assistantModel("gemini", "", "m", "medium", "http://x", "h", 5, "", "k", "", "").description())
                .isEqualTo("google gemini-3.5-flash-lite");
        assertThat(config.assistantModel("groq", "", "m", "medium", "http://x", "h", 5, "", "k", "", "").sharesDataWith())
                .isEqualTo("Groq");
        assertThat(config.assistantModel("gemini", "", "m", "medium", "http://x", "h", 5, "", "", "", "").enabled())
                .as("no key: off, not a crash").isFalse();
    }
}
