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
import tools.jackson.databind.ObjectMapper;

/**
 * Any hosted model that speaks the OpenAI-style {@code POST /chat/completions} API with tools. Many have a free
 * tier, for example Groq ({@code https://api.groq.com/openai/v1}), Google Gemini's OpenAI endpoint or OpenRouter.
 * Which one is only settings: base URL, API key, model and the company name shown to the person.
 * <p>
 * Shape used: tools are {@code {"type":"function","function":{name,description,parameters}}}; the reply's
 * {@code choices[0].message.tool_calls[]} has {@code id} and {@code function.name} plus {@code function.arguments}
 * as a JSON string; a tool result goes back as {@code {"role":"tool","tool_call_id":...,"content":...}}.
 */
public final class OpenAiCompatibleAssistantModel implements AssistantModel {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAssistantModel.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROLE = "role";
    private static final String CONTENT = "content";
    private static final String FUNCTION = "function";
    private final RestClient http;
    private final String model;
    private final String company;

    public OpenAiCompatibleAssistantModel(String baseUrl, String apiKey, String model, String company, Duration timeout) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = RestClient.builder().baseUrl(base).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.model = model;
        this.company = company;
    }

    @Override
    public boolean enabled() { return true; }

    @Override
    public String description() { return company.toLowerCase() + " " + model; }

    @Override
    public String sharesDataWith() { return company; }

    @Override
    public Step next(Conversation conversation) {
        JsonNode reply = http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .body(request(conversation)).retrieve().body(JsonNode.class);
        if (reply == null) throw new IllegalStateException(company + " returned no answer.");
        JsonNode choice = reply.path("choices").path(0);
        JsonNode message = choice.path("message");
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode fn = call.path(FUNCTION);
            calls.add(new ToolCall(call.path("id").asString(UUID.randomUUID().toString()), fn.path("name").asString(""),
                    arguments(fn.path("arguments"))));
        }
        String finish = choice.path("finish_reason").asString("");
        if (log.isInfoEnabled())
            log.info("Assistant step: {} model={} finish={} toolCalls={} promptTokens={} outputTokens={}", company, model,
                    finish, calls.size(), reply.at("/usage/prompt_tokens").asInt(), reply.at("/usage/completion_tokens").asInt());
        Stop stop = !calls.isEmpty() ? Stop.TOOLS : "length".equals(finish) ? Stop.CUT_OFF
                : "content_filter".equals(finish) ? Stop.REFUSED : Stop.DONE;
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
                        messages.add(Map.of(ROLE, "tool", "tool_call_id", r.toolCallId(), "name", r.toolName(), CONTENT, r.content())));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (!conversation.tools().isEmpty())
            body.put("tools", conversation.tools().stream().map(t -> Map.of("type", FUNCTION, FUNCTION,
                    Map.of("name", t.name(), "description", t.description(), "parameters", t.inputSchema()))).toList());
        return body;
    }

    private static Map<String, Object> assistantStep(Step step) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put(ROLE, "assistant");
        message.put(CONTENT, step.text() == null ? "" : step.text());
        if (!step.toolCalls().isEmpty())
            message.put("tool_calls", step.toolCalls().stream().map(c -> Map.of("id", c.id(), "type", FUNCTION,
                    FUNCTION, Map.of("name", c.name(), "arguments", JSON.writeValueAsString(c.input())))).toList());
        return message;
    }

    private static Map<String, Object> arguments(JsonNode node) {
        JsonNode parsed = node;
        if (node.isString()) {
            String text = node.asString();
            parsed = text.isBlank() ? JSON.createObjectNode() : JSON.readTree(text);
        }
        Map<String, Object> args = new LinkedHashMap<>();
        if (parsed.isObject()) parsed.properties().forEach(e -> args.put(e.getKey(), value(e.getValue())));
        return args;
    }

    private static Object value(JsonNode v) {
        if (v.isBoolean()) return v.asBoolean();
        if (v.isIntegralNumber()) return v.asLong();
        if (v.isNumber()) return v.asDouble();
        if (v.isString()) return v.asString();
        return v.isNull() ? null : v.toString();
    }
}
