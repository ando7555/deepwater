package com.deepwater.assistant;

import static com.deepwater.assistant.ScriptedModel.answer;
import static com.deepwater.assistant.ScriptedModel.tools;
import static org.assertj.core.api.Assertions.assertThat;

import com.deepwater.assistant.AssistantModel.ToolCall;
import com.deepwater.assistant.AssistantModel.ToolResults;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The AI workspace voice room (POST /api/v1/workspace/roles/{roleId}/talk): the owner talks with one role. The model is
 * scripted; the tools are real. Lives in this package to use {@link ScriptedModel}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "deepwater.workspace.owner-email=voice-owner@deepwater.test")
class WorkspaceVoiceRoomTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static String ownerToken;

    @TestConfiguration
    static class Scripted {
        @Bean @org.springframework.context.annotation.Primary ScriptedModel scriptedModel() { return new ScriptedModel(); }
    }

    @Autowired ScriptedModel model;
    @Autowired TestRestTemplate http;

    @BeforeEach
    void reset() { model.reset(); }

    @Test
    void eachRoleSpeaksFromItsContractWithOnlyItsOwnToolsAndCannotActAsAnotherRole() throws Exception {
        // The model tries to pass another role id; our code overwrites it with the role in the room.
        model.then(c -> tools(new ToolCall("t1", "workspace_propose_task", Map.of("roleId", "algorithm-developer",
                        "title", "Stream assistant replies", "product", "ASK_DEEPWATER", "assignTo", "ml-engineer",
                        "objective", "Answers appear while the model writes.", "acceptanceCriteria", List.of("Text appears within one second")))))
             .then(c -> answer("I proposed it for the ML engineer. It waits for your approval on the board."));

        ResponseEntity<String> response = talk(owner(), "product-manager", "Please plan streaming for Ask Deepwater.");
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        JsonNode reply = JSON.readTree(response.getBody());
        assertThat(reply.get("roleName").asString()).isEqualTo("Product manager");
        assertThat(reply.get("text").asString()).contains("waits for your approval");
        assertThat(reply.get("toolsUsed").toString()).contains("workspace_propose_task");

        // Spoken in the role's voice, with only the role's tools and no roleId to fill in.
        var first = model.seen.getFirst();
        assertThat(first.system()).contains("You are the Product manager", "by voice", "cannot approve");
        assertThat(first.tools()).extracting(AssistantModel.ToolSpec::name).containsExactly("workspace_tasks", "workspace_propose_task");
        assertThat(first.tools().get(1).inputSchema().toString()).doesNotContain("roleId");
        assertThat(((ToolResults) model.seen.get(1).transcript().getLast()).results().getFirst().content())
                .contains("\"proposedBy\":\"agent:product-manager\"", "\"status\":\"PROPOSED\"");
    }

    @Test
    void aProviderLimitIsSaidPlainlyNotAsAServerProblem() throws Exception {
        model.then(c -> {
            throw org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        });
        ResponseEntity<String> response = talk(owner(), "product-manager", "Hello");
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody()).contains("free limit is used up");
    }

    @Test
    void aRoleWithoutTheToolCannotUseItAndOthersCannotEnter() throws Exception {
        model.then(c -> tools(new ToolCall("t1", "workspace_propose_task", Map.of("title", "x"))))
             .then(c -> answer("I can't propose tasks; the product manager can."));
        JsonNode reply = JSON.readTree(talk(owner(), "ml-engineer", "Add a task").getBody());
        assertThat(((ToolResults) model.seen.get(1).transcript().getLast()).results().getFirst().content()).contains("Unknown tool");
        assertThat(reply.get("text").asString()).contains("can't propose");

        String stranger = register("stranger-" + UUID.randomUUID() + "@example.test");
        assertThat(talk(stranger, "ml-engineer", "hi").getStatusCode().value()).isEqualTo(403);
        assertThat(talk(owner(), "astrologer", "hi").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void theConversationIsSavedSoItCanBeContinuedAndCanBeStartedAgain() throws Exception {
        var headers = new HttpHeaders();
        headers.setBearerAuth(owner());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/v1/workspace/roles/reliability-engineer/talk";
        http.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

        model.then(c -> answer("Uptime was fine this week."));
        assertThat(http.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of("text", "How was uptime?"), headers), String.class)
                .getStatusCode().value()).isEqualTo(200);
        model.then(c -> answer("Yes, the backend restarted twice."));
        http.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of("text", "Any restarts?"), headers), String.class);

        // The second turn was answered with the first one as context, from the server's saved conversation.
        assertThat(model.seen.getLast().transcript()).hasSize(3);
        JsonNode turns = JSON.readTree(http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody()).get("turns");
        assertThat(turns).hasSize(4);
        assertThat(turns.get(0).get("role").asString()).isEqualTo("user");
        assertThat(turns.get(0).get("text").asString()).isEqualTo("How was uptime?");
        assertThat(turns.get(3).get("text").asString()).isEqualTo("Yes, the backend restarted twice.");

        assertThat(http.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class).getStatusCode().value()).isEqualTo(204);
        assertThat(JSON.readTree(http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody()).get("turns")).isEmpty();
    }

    private ResponseEntity<String> talk(String token, String role, String said) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/v1/workspace/roles/" + role + "/talk", HttpMethod.POST,
                new HttpEntity<>(Map.of("messages", List.of(Map.of("role", "user", "content", said))), headers), String.class);
    }

    private synchronized String owner() {
        if (ownerToken == null) {
            ownerToken = register("voice-owner@deepwater.test");
            if (ownerToken == null) ownerToken = (String) http.postForObject("/api/v1/auth/login",
                    Map.of("email", "voice-owner@deepwater.test", "password", PASSWORD), Map.class).get("token");
        }
        return ownerToken;
    }

    private String register(String email) {
        var response = http.postForEntity("/api/v1/auth/register", Map.of("name", "Voice Tester", "email", email, "password", PASSWORD), Map.class);
        return response.getStatusCode().is2xxSuccessful() ? (String) response.getBody().get("token") : null;
    }
}
