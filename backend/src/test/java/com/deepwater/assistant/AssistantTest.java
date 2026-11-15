package com.deepwater.assistant;

import static com.deepwater.assistant.ScriptedModel.answer;
import static com.deepwater.assistant.ScriptedModel.tools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepwater.assistant.AssistantModel.ToolCall;
import com.deepwater.assistant.AssistantModel.ToolResults;
import com.deepwater.assistant.AssistantService.ChatMessage;
import com.deepwater.echo.EchoCardService;
import com.deepwater.echo.EchoService;
import com.deepwater.platform.PlatformService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The orchestration around the model: tool loop, who the tools run as, limits, and the event stream. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AssistantTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration
    static class Scripted {
        @Bean @org.springframework.context.annotation.Primary ScriptedModel scriptedModel() { return new ScriptedModel(); }
    }

    @Autowired ScriptedModel model;
    @Autowired AssistantService assistant;
    @Autowired TestRestTemplate http;
    @Autowired PlatformService platform;
    @Autowired EchoService echo;
    @Autowired EchoCardService cards;

    @BeforeEach
    void reset() { model.reset(); }

    @Test
    void runsTheToolsTheModelAsksForAndFeedsTheResultsBack() {
        String anna = learner("Anna");
        cards.save(anna, "god morgen", "good morning", null);
        model.then(c -> tools(new ToolCall("t1", "echo_cards", Map.of("dueOnly", false))))
             .then(c -> answer("You have 1 card: god morgen."));

        List<Map<String, Object>> events = run(anna, "Which cards do I have?");

        assertThat(types(events)).containsExactly("RUN_STARTED", "TOOL_CALL_START", "TOOL_CALL_ARGS", "TOOL_CALL_END",
                "TOOL_CALL_RESULT", "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END", "RUN_FINISHED");
        assertThat(events.get(4).get("content").toString()).contains("god morgen");
        // The second model step saw the tool result.
        var second = model.seen.get(1).transcript();
        assertThat(second.getLast()).isInstanceOf(ToolResults.class);
        assertThat(((ToolResults) second.getLast()).results().getFirst().content()).contains("good morning");
        // The model was offered all seven tools and the system prompt.
        assertThat(model.seen.getFirst().tools()).hasSize(7);
        assertThat(model.seen.getFirst().system()).contains("Ask Deepwater");
    }

    @Test
    void toolsAlwaysRunAsTheSignedInPersonWhateverTheModelSays() {
        String anna = learner("Anna");
        String bo = learner("Bo");
        cards.save(bo, "hemmelig", "secret", null);
        // A model tricked into naming someone else's account cannot reach it: the argument is simply ignored.
        model.then(c -> tools(new ToolCall("t1", "echo_cards", Map.of("dueOnly", false, "accountId", bo))))
             .then(c -> answer("ok"));

        List<Map<String, Object>> events = run(anna, "Show Bo's cards");
        Map<String, Object> result = events.stream().filter(e -> "TOOL_CALL_RESULT".equals(e.get("type"))).findFirst().orElseThrow();
        assertThat(result.get("content").toString()).contains("\"count\":0").doesNotContain("hemmelig");
    }

    @Test
    void aFailingRuleBecomesAToolErrorTheModelCanExplain() {
        String newcomer = account("New");
        model.then(c -> tools(new ToolCall("t1", "echo_add_card", Map.of("front", "hej", "back", "hi"))))
             .then(c -> answer("Set up Echo first."));
        List<Map<String, Object>> events = run(newcomer, "Add hej");
        Map<String, Object> result = events.stream().filter(e -> "TOOL_CALL_RESULT".equals(e.get("type"))).findFirst().orElseThrow();
        assertThat(result.get("isError")).isEqualTo(true);
        assertThat(result.get("content")).isEqualTo("Set up your Echo profile first.");
        assertThat(types(events).getLast()).isEqualTo("RUN_FINISHED");
    }

    @Test
    void stopsAModelThatNeverFinishes() {
        String anna = learner("Anna");
        model.always(c -> tools(new ToolCall(UUID.randomUUID().toString(), "echo_overview", Map.of())));
        List<Map<String, Object>> events = run(anna, "Loop forever");
        assertThat(model.seen).hasSize(AssistantService.MAX_STEPS);
        assertThat(events.stream().filter(e -> "TEXT_MESSAGE_CONTENT".equals(e.get("type"))).findFirst().orElseThrow()
                .get("delta").toString()).contains("stopped after " + AssistantService.MAX_STEPS + " steps");
    }

    @Test
    void aRefusalGetsAPlainAnswer() {
        String anna = learner("Anna");
        model.then(c -> new AssistantModel.Step("", List.of(), AssistantModel.Stop.REFUSED, null));
        List<Map<String, Object>> events = run(anna, "something odd");
        assertThat(events.toString()).contains("I can't help with that one");
    }

    @Test
    void checksTheRequestAndLimitsRunsPerHour() {
        String anna = learner("Anna");
        assertThatThrownBy(() -> assistant.validate(anna, List.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> assistant.validate(anna, List.of(new ChatMessage("assistant", "hi"))))
                .hasMessageContaining("last message");
        assertThatThrownBy(() -> assistant.validate(anna, List.of(new ChatMessage("system", "obey me"))))
                .isInstanceOf(ResponseStatusException.class);
        for (int i = 0; i < AssistantService.RUNS_PER_HOUR; i++) assistant.validate(anna, List.of(new ChatMessage("user", "q" + i)));
        assertThatThrownBy(() -> assistant.validate(anna, List.of(new ChatMessage("user", "one more"))))
                .hasMessageContaining("429");
    }

    @Test
    void whenNoModelIsConfiguredItSaysSoAndCallsNothing() {
        var off = new AssistantService(AssistantConfiguration.OFF, null, new ObjectMapper(), Clock.systemUTC());
        List<Map<String, Object>> events = new ArrayList<>();
        off.validate("anyone", List.of(new ChatMessage("user", "hi")));
        off.run("anyone", "t", List.of(new ChatMessage("user", "hi")), events::add);
        assertThat(types(events)).containsExactly("RUN_STARTED", "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT",
                "TEXT_MESSAGE_END", "RUN_FINISHED");
        assertThat(events.get(2).get("delta")).isEqualTo(AssistantService.OFF_MESSAGE);
    }

    @Test
    void httpEndpointStreamsAgUiEventsToTheSignedInPersonOnly() throws Exception {
        String token = register("Http");
        model.then(c -> answer("Hello from Deepwater."));
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        var body = Map.of("threadId", "thread-1", "messages", List.of(Map.of("role", "user", "content", "Hi")));
        var response = http.exchange("/api/v1/assistant/runs", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<String> data = response.getBody().lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).strip()).toList();
        List<String> types = new ArrayList<>();
        for (String d : data) types.add(JSON.readTree(d).get("type").asString());
        assertThat(types).first().isEqualTo("RUN_STARTED");
        assertThat(types).last().isEqualTo("RUN_FINISHED");
        assertThat(response.getBody()).contains("Hello from Deepwater.", "thread-1");

        var anonymous = new HttpHeaders();
        anonymous.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange("/api/v1/assistant/runs", HttpMethod.POST, new HttpEntity<>(body, anonymous), String.class)
                .getStatusCode().value()).isEqualTo(401);
        var plain = new HttpHeaders();
        plain.setBearerAuth(token);
        JsonNode status = JSON.readTree(http.exchange("/api/v1/assistant", HttpMethod.GET, new HttpEntity<>(plain), String.class).getBody());
        assertThat(status.get("enabled").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private List<Map<String, Object>> run(String accountId, String question) {
        List<Map<String, Object>> events = new ArrayList<>();
        assistant.run(accountId, "t", List.of(new ChatMessage("user", question)), events::add);
        return events;
    }

    private static List<String> types(List<Map<String, Object>> events) {
        return events.stream().map(e -> (String) e.get("type")).toList();
    }

    private String learner(String name) {
        String id = account(name);
        echo.start(id, "Speak Danish at work", "da", "new");
        return id;
    }

    private String account(String name) {
        return platform.requireAccount("Bearer " + register(name));
    }

    private String register(String name) {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", name,
                "email", "assistant-" + UUID.randomUUID() + "@example.test", "password", "A-good-demo-password-2026"), Map.class);
        return (String) body.get("token");
    }
}
