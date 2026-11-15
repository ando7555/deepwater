package com.deepwater.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.deepwater.assistant.AssistantService.ChatMessage;
import com.deepwater.echo.EchoService;
import com.deepwater.platform.PlatformService;
import com.deepwater.tools.DeepwaterTools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The live eval: real questions against the real model, scored by rules (which tool it chose, which it must
 * not use, words the answer must not contain). It calls a real model (API money for Claude), so it runs only
 * when ASSISTANT_EVAL=true; CI skips it. Results are written to build/assistant-evals.jsonl, one line per case.
 * Claude:  ASSISTANT_EVAL=true ANTHROPIC_API_KEY=... gradle :backend:test --tests '*AssistantEvalTest'
 * Ollama:  ASSISTANT_EVAL=true ASSISTANT_PROVIDER=ollama OLLAMA_MODEL=hermes3 gradle :backend:test --tests '*AssistantEvalTest'
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ASSISTANT_EVAL", matches = "true")
class AssistantEvalTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired DeepwaterTools tools;
    @Autowired PlatformService platform;
    @Autowired EchoService echo;

    @Test
    void theModelPicksTheRightToolsAndKeepsToTheRules() throws Exception {
        // ASSISTANT_PROVIDER=ollama runs the same eval against a local model (OLLAMA_BASE_URL, OLLAMA_MODEL).
        AssistantModel model = "ollama".equalsIgnoreCase(System.getenv("ASSISTANT_PROVIDER"))
                ? new OllamaAssistantModel(System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434"),
                        System.getenv().getOrDefault("OLLAMA_MODEL", "hermes3"), java.time.Duration.ofSeconds(300))
                : new ClaudeAssistantModel(AnthropicOkHttpClient.fromEnv(),
                        System.getenv().getOrDefault("ASSISTANT_AI_MODEL", "claude-opus-5-5"),
                        System.getenv().getOrDefault("ASSISTANT_EFFORT", "medium"));
        var assistant = new AssistantService(model, tools, JSON, Clock.systemUTC(), null);
        JsonNode cases = JSON.readTree(new ClassPathResource("assistant-evals.json").getInputStream());
        List<String> lines = new ArrayList<>();
        int passed = 0;
        for (JsonNode c : cases) {
            String account = learner();
            List<Map<String, Object>> events = new ArrayList<>();
            assistant.run(account, "eval", List.of(new ChatMessage("user", c.get("question").asString())), events::add);
            List<String> used = events.stream().filter(e -> "TOOL_CALL_START".equals(e.get("type")))
                    .map(e -> (String) e.get("toolCallName")).toList();
            String answer = String.join("\n", events.stream().filter(e -> "TEXT_MESSAGE_CONTENT".equals(e.get("type")))
                    .map(e -> (String) e.get("delta")).toList());
            List<String> expect = strings(c.get("expectAnyTool"));
            boolean toolOk = expect.isEmpty() || used.stream().anyMatch(expect::contains);
            boolean forbiddenOk = used.stream().noneMatch(strings(c.get("forbidTools"))::contains);
            boolean textOk = strings(c.get("answerMustNotContain")).stream().noneMatch(answer::contains);
            boolean finished = "RUN_FINISHED".equals(events.getLast().get("type"));
            boolean pass = toolOk && forbiddenOk && textOk && finished;
            if (pass) passed++;
            lines.add(JSON.writeValueAsString(Map.of("id", c.get("id").asString(), "pass", pass, "toolsUsed", used,
                    "toolOk", toolOk, "forbiddenOk", forbiddenOk, "textOk", textOk, "answer", answer)));
        }
        Path out = Path.of("build", "assistant-evals.jsonl");
        Files.createDirectories(out.getParent());
        Files.write(out, lines);
        // A first bar, to be raised once we have a baseline: at least 9 of 11 cases.
        assertThat(passed).as(String.join("\n", lines)).isGreaterThanOrEqualTo(cases.size() - 2);
    }

    private String learner() {
        var session = platform.register("Eval Learner", "eval-" + UUID.randomUUID() + "@example.test", "A-good-demo-password-2026");
        echo.start(session.get("accountId"), "Get a job in Denmark", "da", "some");
        return session.get("accountId");
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null) node.forEach(n -> out.add(n.asString()));
        return out;
    }
}
