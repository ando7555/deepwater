package com.deepwater.assistant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * A local model served by Ollama (for example Hermes: {@code ollama pull hermes3}), through Ollama's
 * {@code POST /api/chat} with tools and {@code stream: false}. Nothing leaves the machine Ollama runs on.
 * <p>
 * Shape used, from Ollama's API documentation: tools are {@code {"type":"function","function":{name,
 * description, parameters}}}; the reply's {@code message.tool_calls[].function} has {@code name} and an
 * {@code arguments} object (no call id, so one is made here); a tool result goes back as
 * {@code {"role":"tool","content":...,"tool_name":...}}.
 */
public final class OllamaAssistantModel implements AssistantModel {
    private static final Logger log = LoggerFactory.getLogger(OllamaAssistantModel.class);
    private static final String ROLE = "role";
    private static final String CONTENT = "content";
    private static final String FUNCTION = "function";
    private final RestClient http;
    private final String model;

    public OllamaAssistantModel(String baseUrl, String model, Duration timeout) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.model = model;
    }

    @Override
    public boolean enabled() { return true; }

    @Override
    public String description() { return "ollama " + model; }

    @Override
    public Step next(Conversation conversation) {
        JsonNode reply = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .body(request(conversation)).retrieve().body(JsonNode.class);
        if (reply == null) throw new IllegalStateException("Ollama returned no answer.");
        JsonNode message = reply.path("message");
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode fn = call.path(FUNCTION);
            calls.add(new ToolCall(call.path("id").asString(UUID.randomUUID().toString()), fn.path("name").asString(""),
                    ToolArguments.read(fn.path("arguments"))));
        }
        if (log.isInfoEnabled())
            log.info("Assistant step: ollama model={} done={} toolCalls={} promptTokens={} outputTokens={}", model,
                    reply.path("done_reason").asString(""), calls.size(), reply.path("prompt_eval_count").asInt(),
                    reply.path("eval_count").asInt());
        Stop stop = !calls.isEmpty() ? Stop.TOOLS : "length".equals(reply.path("done_reason").asString("")) ? Stop.CUT_OFF : Stop.DONE;
        return new Step(message.path(CONTENT).asString(""), calls, stop, null);
    }

    Map<String, Object> request(Conversation conversation) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, "system", CONTENT, conversation.system()));
        for (Entry entry : conversation.transcript()) {
            switch (entry) {
                case UserText(String text) -> messages.add(Map.of(ROLE, "user", CONTENT, text));
                case AssistantText(String text) -> messages.add(Map.of(ROLE, "assistant", CONTENT, text));
                case ModelStep(Step step) -> messages.add(assistantStep(step));
                case ToolResults(List<ToolResult> results) -> results.forEach(r ->
                        messages.add(Map.of(ROLE, "tool", CONTENT, r.content(), "tool_name", r.toolName())));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", conversation.tools().stream().map(t -> Map.of("type", FUNCTION, FUNCTION,
                Map.of("name", t.name(), "description", t.description(), "parameters", t.inputSchema()))).toList());
        body.put("stream", false);
        return body;
    }

    private static Map<String, Object> assistantStep(Step step) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put(ROLE, "assistant");
        message.put(CONTENT, step.text() == null ? "" : step.text());
        if (!step.toolCalls().isEmpty())
            message.put("tool_calls", step.toolCalls().stream()
                    .map(c -> Map.of(FUNCTION, Map.of("name", c.name(), "arguments", c.input()))).toList());
        return message;
    }
}
