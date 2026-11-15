package com.deepwater.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepwater.echo.EchoService;
import com.deepwater.platform.PlatformService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The MCP endpoint speaks JSON-RPC as an MCP client expects, and every tool sees only the caller's own data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class McpServerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired TestRestTemplate http;
    @Autowired EchoService echo;
    @Autowired PlatformService platform;

    @Test
    void refusesCallsWithoutADeepwaterSession() {
        var response = post(null, rpc(1, "tools/list", Map.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(post("not-a-real-token", rpc(1, "tools/list", Map.of())).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void handshakeAndToolListFollowTheProtocol() throws Exception {
        String token = register("Mcp Tester");
        JsonNode init = body(post(token, rpc(1, "initialize", Map.of("protocolVersion", "2025-06-18",
                "capabilities", Map.of(), "clientInfo", Map.of("name", "test", "version", "1")))));
        assertThat(init.get("id").asInt()).isEqualTo(1);
        assertThat(init.at("/result/protocolVersion").asString()).isEqualTo("2025-06-18");
        assertThat(init.at("/result/serverInfo/name").asString()).isEqualTo("deepwater");
        assertThat(init.at("/result/capabilities/tools").isObject()).isTrue();

        // An unknown version gets the newest one we speak.
        JsonNode other = body(post(token, rpc(2, "initialize", Map.of("protocolVersion", "1999-01-01"))));
        assertThat(other.at("/result/protocolVersion").asString()).isEqualTo(McpController.PROTOCOL_VERSIONS.getFirst());

        var notification = new HashMap<String, Object>(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        assertThat(post(token, notification).getStatusCode().value()).isEqualTo(202);

        JsonNode tools = body(post(token, rpc(3, "tools/list", Map.of()))).at("/result/tools");
        assertThat(tools.findValuesAsString("name")).containsExactly("echo_overview", "echo_cards", "echo_add_card",
                "harbor_find_jobs", "harbor_applications", "polish_b1_results", "amber_my_profile");
        for (JsonNode tool : tools) {
            assertThat(tool.at("/inputSchema/type").asString()).isEqualTo("object");
            assertThat(tool.get("description").asString()).isNotBlank();
        }
        // Nothing about other Amber members is offered.
        assertThat(tools.toString()).doesNotContain("amber_suggestions", "amber_messages");

        assertThat(body(post(token, rpc(4, "nope/nope", Map.of()))).at("/error/code").asInt()).isEqualTo(-32601);
        assertThat(body(post(token, rpc(5, "tools/call", Map.of("name", "nope")))).at("/error/code").asInt()).isEqualTo(-32602);
        assertThat(http.getForEntity("/mcp", String.class).getStatusCode().value()).isEqualTo(405);
    }

    @Test
    void cardsAddedThroughMcpBelongOnlyToTheCaller() throws Exception {
        String anna = register("Anna");
        String bo = register("Bo");
        // Cards belong to an Echo language, so Echo must be set up first; the rule's own words come back.
        JsonNode early = call(anna, "echo_add_card", Map.of("front", "en kop kaffe", "back", "a cup of coffee"));
        assertThat(early.get("isError").asBoolean()).isTrue();
        assertThat(early.at("/content/0/text").asString()).isEqualTo("Set up your Echo profile first.");
        echo.start(platform.requireAccount("Bearer " + anna), "Order coffee in Copenhagen", "da", "new");
        echo.start(platform.requireAccount("Bearer " + bo), "Read Danish news", "da", "some");

        JsonNode added = call(anna, "echo_add_card", Map.of("front", "en kop kaffe", "back", "a cup of coffee"));
        assertThat(added.get("isError").asBoolean()).isFalse();
        assertThat(text(added).get("front").asString()).isEqualTo("en kop kaffe");

        JsonNode annaCards = text(call(anna, "echo_cards", Map.of("dueOnly", false)));
        assertThat(annaCards.get("count").asInt()).isEqualTo(1);
        assertThat(text(call(bo, "echo_cards", Map.of("dueOnly", false))).get("count").asInt()).isZero();

        // A rule saying no comes back as a tool error the assistant can read, not a crash.
        JsonNode missing = call(anna, "echo_add_card", Map.of("front", "hej"));
        assertThat(missing.get("isError").asBoolean()).isTrue();
        assertThat(missing.at("/content/0/text").asString()).contains("back");
    }

    @Test
    void readToolsAnswerForANewMember() throws Exception {
        String token = register("Newcomer");
        assertThat(text(call(token, "echo_overview", Map.of())).has("nextAction")).isTrue();
        JsonNode jobs = text(call(token, "harbor_find_jobs", Map.of("max", 3, "visa", "OPEN_TO_ME")));
        assertThat(jobs.get("jobs").size()).isLessThanOrEqualTo(3);
        assertThat(text(call(token, "harbor_applications", Map.of())).get("applications").isArray()).isTrue();
        assertThat(text(call(token, "polish_b1_results", Map.of())).get("attempts").isArray()).isTrue();
        JsonNode amber = text(call(token, "amber_my_profile", Map.of()));
        assertThat(amber.get("joined").asBoolean()).isFalse();
    }

    @Test
    void amberProfileToolShowsOnlyTheCallersOwnProfile() throws Exception {
        String mia = register("Mia");
        var profile = new HashMap<String, Object>();
        profile.put("displayName", "Mia");
        profile.put("age", 31);
        profile.put("confirmAdult", true);
        profile.put("area", "Town-" + UUID.randomUUID().toString().substring(0, 8));
        profile.put("intents", List.of("FRIENDS"));
        profile.put("interests", List.of("Jazz"));
        profile.put("visible", true);
        assertThat(http.exchange("/api/v1/amber/profile", HttpMethod.PUT, new HttpEntity<>(profile, headers(mia)), String.class)
                .getStatusCode().value()).isEqualTo(200);

        assertThat(text(call(mia, "amber_my_profile", Map.of())).get("displayName").asString()).isEqualTo("Mia");
        String stranger = register("Stranger");
        JsonNode theirs = text(call(stranger, "amber_my_profile", Map.of()));
        assertThat(theirs.get("joined").asBoolean()).isFalse();
        assertThat(theirs.toString()).doesNotContain("Mia");
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode call(String token, String tool, Map<String, Object> arguments) throws Exception {
        JsonNode response = body(post(token, rpc(7, "tools/call", Map.of("name", tool, "arguments", arguments))));
        assertThat(response.has("result")).as(response.toString()).isTrue();
        return response.get("result");
    }

    private static JsonNode text(JsonNode result) throws Exception {
        assertThat(result.get("isError").asBoolean()).as(result.toString()).isFalse();
        return JSON.readTree(result.at("/content/0/text").asString());
    }

    private static Map<String, Object> rpc(int id, String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }

    private ResponseEntity<String> post(String token, Map<String, Object> message) {
        var headers = token == null ? new HttpHeaders() : headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        return http.exchange("/mcp", HttpMethod.POST, new HttpEntity<>(message, headers), String.class);
    }

    private static JsonNode body(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return JSON.readTree(response.getBody());
    }

    private static HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String register(String name) {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", name,
                "email", "mcp-" + UUID.randomUUID() + "@example.test", "password", "A-good-demo-password-2026"), Map.class);
        return (String) body.get("token");
    }
}
