package com.deepwater.mcp;

import com.deepwater.platform.PlatformService;
import com.deepwater.tools.DeepwaterTools;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Deepwater's MCP server (Model Context Protocol), so an AI assistant such as Claude can read the person's
 * Echo, Harbor and Amber state and add Echo cards for them.
 * <p>
 * Transport: MCP "Streamable HTTP" in its simplest, stateless form. The client POSTs one JSON-RPC 2.0 message to
 * {@code /mcp} and gets one JSON answer back; there is no server-sent event stream and no MCP session id, so any
 * backend instance can answer any request. Authentication is the same Deepwater session token the web app uses,
 * sent as {@code Authorization: Bearer <token>}, checked on every request.
 */
@RestController
class McpController {
    static final List<String> PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");
    private final PlatformService platform;
    private final DeepwaterTools tools;
    private final ObjectMapper json;
    private final String version;

    McpController(PlatformService platform, DeepwaterTools tools, ObjectMapper json,
                  @Value("${deepwater.mcp.server-version:0.1.0}") String version) {
        this.platform = platform;
        this.tools = tools;
        this.json = json;
        this.version = version;
    }

    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> handle(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                             @RequestBody(required = false) JsonNode message) {
        String account;
        try {
            account = platform.requireAccount(authorization);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"deepwater\"")
                    .body(error(null, -32001, "Send your Deepwater session token as Authorization: Bearer <token>."));
        }
        if (message == null || !message.isObject() || !"2.0".equals(message.path("jsonrpc").asString(""))
                || !message.path("method").isString())
            return ResponseEntity.badRequest().body(error(null, -32600, "Send one JSON-RPC 2.0 request."));

        JsonNode id = message.get("id");
        String method = message.get("method").asString();
        // Notifications (no id), such as notifications/initialized, need no answer.
        if (id == null || id.isNull()) return ResponseEntity.accepted().build();

        JsonNode params = message.path("params");
        return ResponseEntity.ok(switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, Map.of());
            case "tools/list" -> result(id, Map.of("tools", tools.list()));
            case "tools/call" -> callTool(id, account, params);
            default -> error(id, -32601, "Unknown method: " + method);
        });
    }

    /** No server-to-client event stream in this version; the spec allows answering 405 here. */
    @GetMapping("/mcp")
    ResponseEntity<Void> stream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    @DeleteMapping("/mcp")
    ResponseEntity<Void> endSession() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    private Map<String, Object> initialize(JsonNode params) {
        String asked = params.path("protocolVersion").asString("");
        String chosen = PROTOCOL_VERSIONS.contains(asked) ? asked : PROTOCOL_VERSIONS.getFirst();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("protocolVersion", chosen);
        out.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        out.put("serverInfo", Map.of("name", "deepwater", "title", "Deepwater", "version", version));
        out.put("instructions", "Tools for the signed-in Deepwater member: Echo language learning (overview, phrase cards, "
                + "adding a card), Harbor jobs (matched jobs, application tracker), Polish B1 practice results, and their own "
                + "Amber profile. Every tool sees only this member's data.");
        return out;
    }

    private Map<String, Object> callTool(JsonNode id, String account, JsonNode params) {
        String name = params.path("name").asString("");
        if (tools.find(name) == null) return error(id, -32602, "Unknown tool: " + name);
        JsonNode rawArgs = params.path("arguments");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = rawArgs.isObject() ? json.convertValue(rawArgs, Map.class) : Map.of();
        DeepwaterTools.Outcome outcome = tools.call(account, name, args);
        return result(id, Map.of("content", List.of(Map.of("type", "text", "text", outcome.text())), "isError", outcome.error()));
    }

    private static Map<String, Object> result(JsonNode id, Object result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.put("result", result);
        return out;
    }

    private static Map<String, Object> error(JsonNode id, int code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.put("error", Map.of("code", code, "message", message));
        return out;
    }
}
