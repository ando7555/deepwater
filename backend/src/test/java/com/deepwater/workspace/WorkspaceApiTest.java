package com.deepwater.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The AI workspace end to end: the owner's screen (REST) and the AI roles' door (MCP tools). Scenarios follow
 * docs/specs/53-ai-workspace.md section 6.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "deepwater.workspace.owner-email=workspace-owner@deepwater.test")
class WorkspaceApiTest {
    private static final String OWNER_EMAIL = "workspace-owner@deepwater.test";
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static String ownerToken;
    @Autowired TestRestTemplate http;

    @Test
    void onlyTheOwnerCanOpenTheWorkspace() throws Exception {
        String stranger = register("stranger-" + UUID.randomUUID() + "@example.test");
        assertThat(json(send(HttpMethod.GET, "/api/v1/workspace/access", stranger, null)).get("owner").asBoolean()).isFalse();
        assertThat(send(HttpMethod.GET, "/api/v1/workspace/tasks", stranger, null).getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.POST, "/api/v1/workspace/tasks", stranger, task("Sneaky", "ml-engineer")).getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.GET, "/api/v1/workspace/tasks", null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(json(send(HttpMethod.GET, "/api/v1/workspace/access", owner(), null)).get("owner").asBoolean()).isTrue();

        // Workspace tools are not even listed to other members over MCP.
        JsonNode strangerTools = json(mcp(stranger, "tools/list", Map.of())).at("/result/tools");
        assertThat(strangerTools.toString()).doesNotContain("workspace_");
        JsonNode ownerTools = json(mcp(owner(), "tools/list", Map.of())).at("/result/tools");
        assertThat(ownerTools.findValuesAsString("name")).contains("workspace_tasks", "workspace_propose_task",
                "workspace_start_task", "workspace_submit_evidence");
        // No tool can approve, reject or accept.
        assertThat(ownerTools.findValuesAsString("name")).noneMatch(n -> n.contains("approve") || n.contains("accept") || n.contains("reject"));
    }

    @Test
    void theFiveRolesAreContractsAndNoneCanApprove() throws Exception {
        JsonNode roles = json(send(HttpMethod.GET, "/api/v1/workspace/roles", owner(), null));
        assertThat(roles.findValuesAsString("id")).containsExactly("product-manager", "ai-architect", "ml-engineer",
                "reliability-engineer", "algorithm-developer");
        for (JsonNode role : roles) {
            assertThat(role.get("purpose").asString()).isNotBlank();
            assertThat(role.get("doesNot").size()).isPositive();
            assertThat(role.get("tools").toString()).doesNotContain("approve");
        }
    }

    @Test
    void aTaskGoesFromProposalToDoneOnlyThroughTheOwnersApprovalAndEvidence() throws Exception {
        // An AI role proposes through MCP; it waits for the owner.
        JsonNode proposed = tool("workspace_propose_task", Map.of("roleId", "product-manager", "title", "Stream assistant replies token by token",
                "product", "ASK_DEEPWATER", "assignTo", "ml-engineer", "objective", "Replies appear while the model writes.",
                "acceptanceCriteria", List.of("Given a question, when the model answers, then text appears within one second")));
        String id = proposed.get("id").asString();
        assertThat(proposed.get("status").asString()).isEqualTo("PROPOSED");
        assertThat(proposed.get("proposedBy").asString()).isEqualTo("agent:product-manager");
        assertThat(proposed.get("handoff").isNull()).isTrue();

        // The assigned role cannot start unapproved work.
        assertThat(toolError("workspace_start_task", Map.of("roleId", "ml-engineer", "taskId", id))).contains("cannot move");

        // The owner approves; a stale version is refused, a repeated approve changes nothing.
        assertThat(send(HttpMethod.POST, "/api/v1/workspace/tasks/" + id + "/approve", owner(), Map.of("version", 99)).getStatusCode().value()).isEqualTo(409);
        JsonNode approved = json(send(HttpMethod.POST, "/api/v1/workspace/tasks/" + id + "/approve", owner(), Map.of("version", 0)));
        assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("handoff").asString()).startsWith("/deepwater-developer:feature Stream assistant replies")
                .contains("workspace_start_task", id, "ml-engineer", "Do not merge or deploy");
        JsonNode again = json(send(HttpMethod.POST, "/api/v1/workspace/tasks/" + id + "/approve", owner(), Map.of("version", 0)));
        assertThat(again.get("version").asInt()).isEqualTo(approved.get("version").asInt());

        // Another role cannot start someone else's task; the right role can, twice without harm.
        assertThat(toolError("workspace_start_task", Map.of("roleId", "algorithm-developer", "taskId", id))).contains("belongs to the ml-engineer");
        assertThat(tool("workspace_start_task", Map.of("roleId", "ml-engineer", "taskId", id)).get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(tool("workspace_start_task", Map.of("roleId", "ml-engineer", "taskId", id)).get("status").asString()).isEqualTo("IN_PROGRESS");

        // Review needs evidence.
        assertThat(toolError("workspace_submit_evidence", Map.of("roleId", "ml-engineer", "taskId", id, "evidence", "done"))).contains("evidence");
        JsonNode review = tool("workspace_submit_evidence", Map.of("roleId", "ml-engineer", "taskId", id,
                "evidence", "PR https://github.com/example/pull/1 - BUILD SUCCESSFUL, 12 tests passed"));
        assertThat(review.get("status").asString()).isEqualTo("IN_REVIEW");

        // Only the owner accepts.
        int version = review.get("version").asInt();
        JsonNode done = json(send(HttpMethod.POST, "/api/v1/workspace/tasks/" + id + "/accept", owner(), Map.of("version", version)));
        assertThat(done.get("status").asString()).isEqualTo("DONE");
        assertThat(done.get("events").findValuesAsString("action")).containsExactly("PROPOSED", "APPROVED", "STARTED", "SUBMITTED", "ACCEPTED");
        assertThat(done.get("events").findValuesAsString("actor")).containsExactly("agent:product-manager", "owner",
                "agent:ml-engineer", "agent:ml-engineer", "owner");
        assertThat(send(HttpMethod.POST, "/api/v1/workspace/tasks/" + id + "/send-back", owner(), Map.of("version", done.get("version").asInt()))
                .getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void editingAnApprovedTaskNeedsANewApproval() throws Exception {
        JsonNode created = json(send(HttpMethod.POST, "/api/v1/workspace/tasks", owner(), task("Add a runbook for the assistant", "reliability-engineer")));
        String id = created.get("id").asString();
        assertThat(created.get("proposedBy").asString()).isEqualTo("owner");
        JsonNode approved = json(send(HttpMethod.POST, "/api/v1/workspace/tasks/" + id + "/approve", owner(), Map.of("version", created.get("version").asInt())));

        var changed = task("Add a runbook and alerts for the assistant", "reliability-engineer");
        JsonNode edited = json(send(HttpMethod.PUT, "/api/v1/workspace/tasks/" + id, owner(),
                Map.of("version", approved.get("version").asInt(), "task", changed)));
        assertThat(edited.get("status").asString()).isEqualTo("PROPOSED");
        assertThat(edited.get("approvedAt").isNull()).isTrue();
        assertThat(toolError("workspace_start_task", Map.of("roleId", "reliability-engineer", "taskId", id))).contains("cannot move");
    }

    @Test
    void badInputGetsAPlainAnswer() throws Exception {
        var noCriteria = task("Something", "ml-engineer");
        noCriteria.put("acceptanceCriteria", List.of());
        assertThat(send(HttpMethod.POST, "/api/v1/workspace/tasks", owner(), noCriteria).getBody()).contains("acceptance criterion");
        var unknownRole = task("Something", "astrologer");
        assertThat(send(HttpMethod.POST, "/api/v1/workspace/tasks", owner(), unknownRole).getBody()).contains("Pick one of the roles");
        // A role whose contract does not allow proposing is refused.
        assertThat(toolError("workspace_propose_task", Map.of("roleId", "ml-engineer", "title", "x", "product", "ECHO",
                "objective", "y", "acceptanceCriteria", List.of("z")))).contains("may not use workspace_propose_task");
    }

    // ------------------------------------------------------------------ helpers

    private static HashMap<String, Object> task(String title, String roleId) {
        var body = new HashMap<String, Object>();
        body.put("title", title);
        body.put("product", "ask_deepwater");
        body.put("roleId", roleId);
        body.put("objective", "Make the assistant dependable.");
        body.put("acceptanceCriteria", List.of("Given the assistant is down, when the owner checks, then the runbook says what to do"));
        return body;
    }

    private JsonNode tool(String name, Map<String, Object> args) throws Exception {
        JsonNode result = json(mcp(owner(), "tools/call", Map.of("name", name, "arguments", args))).get("result");
        assertThat(result.get("isError").asBoolean()).as(result.toString()).isFalse();
        return JSON.readTree(result.at("/content/0/text").asString());
    }

    private String toolError(String name, Map<String, Object> args) throws Exception {
        JsonNode result = json(mcp(owner(), "tools/call", Map.of("name", name, "arguments", args))).get("result");
        assertThat(result.get("isError").asBoolean()).as(result.toString()).isTrue();
        return result.at("/content/0/text").asString();
    }

    private ResponseEntity<String> mcp(String token, String method, Map<String, Object> params) {
        var headers = headers(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        return http.exchange("/mcp", HttpMethod.POST, new HttpEntity<>(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params), headers), String.class);
    }

    private synchronized String owner() {
        if (ownerToken == null) {
            ownerToken = register(OWNER_EMAIL);
            if (ownerToken == null) ownerToken = (String) http.postForObject("/api/v1/auth/login",
                    Map.of("email", OWNER_EMAIL, "password", PASSWORD), Map.class).get("token");
        }
        return ownerToken;
    }

    private String register(String email) {
        var response = http.postForEntity("/api/v1/auth/register", Map.of("name", "Workspace Tester", "email", email, "password", PASSWORD), Map.class);
        return response.getStatusCode().is2xxSuccessful() ? (String) response.getBody().get("token") : null;
    }

    private static JsonNode json(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return JSON.readTree(response.getBody());
    }

    private static HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String token, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, headers(token)), String.class);
    }
}
