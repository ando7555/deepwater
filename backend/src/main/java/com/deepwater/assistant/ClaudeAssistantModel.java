package com.deepwater.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Claude adapter. One {@link #next} call is one Messages API request with Deepwater's tools attached.
 * <ul>
 *   <li>Thinking is adaptive (the model's default) and effort is set explicitly.</li>
 *   <li>Server-side fallbacks are on: if the model declines, the API retries on a fallback model by itself.</li>
 *   <li>The model's own reply is sent back unchanged on the next step ({@link BetaMessage#toParam()}), as the
 *       API requires for replies that carry thinking blocks.</li>
 * </ul>
 */
public final class ClaudeAssistantModel implements AssistantModel {
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final Logger log = LoggerFactory.getLogger(ClaudeAssistantModel.class);
    private final AnthropicClient client;
    private final String model;
    private final BetaOutputConfig.Effort effort;

    public ClaudeAssistantModel(AnthropicClient client, String model, String effort) {
        this.client = client;
        this.model = model;
        this.effort = switch (effort == null ? "" : effort.strip().toLowerCase()) {
            case "low" -> BetaOutputConfig.Effort.LOW;
            case "high" -> BetaOutputConfig.Effort.HIGH;
            default -> BetaOutputConfig.Effort.MEDIUM;
        };
    }

    @Override
    public boolean enabled() { return true; }

    @Override
    public String description() { return "anthropic " + model; }

    @Override
    public Step next(Conversation conversation) {
        BetaMessage reply = client.beta().messages().create(params(conversation));
        if (log.isInfoEnabled())
            log.info("Assistant step: model={} stop={} inputTokens={} outputTokens={}", reply.model(),
                    reply.stopReason().map(Object::toString).orElse("?"), reply.usage().inputTokens(), reply.usage().outputTokens());
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        reply.content().forEach(block -> {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(use -> calls.add(new ToolCall(use.id(), use.name(), input(use._input()))));
        });
        Stop stop = reply.stopReason().map(ClaudeAssistantModel::stop).orElse(Stop.DONE);
        return new Step(text.toString(), calls, stop, reply);
    }

    MessageCreateParams params(Conversation conversation) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(conversation.system())
                .outputConfig(BetaOutputConfig.builder().effort(effort).build())
                .addBeta(FALLBACK_BETA)
                .fallbacksDefault();
        conversation.tools().forEach(spec -> builder.addTool(tool(spec)));
        for (Entry entry : conversation.transcript()) builder.addMessage(message(entry));
        return builder.build();
    }

    static BetaTool tool(ToolSpec spec) {
        var properties = BetaTool.InputSchema.Properties.builder();
        Object props = spec.inputSchema().get("properties");
        if (props instanceof Map<?, ?> map) map.forEach((k, v) -> properties.putAdditionalProperty(k.toString(), JsonValue.from(v)));
        var schema = BetaTool.InputSchema.builder().properties(properties.build())
                .putAdditionalProperty("additionalProperties", JsonValue.from(false));
        if (spec.inputSchema().get("required") instanceof List<?> required)
            schema.required(required.stream().map(Object::toString).toList());
        return BetaTool.builder().name(spec.name()).description(spec.description()).inputSchema(schema.build()).build();
    }

    static BetaMessageParam message(Entry entry) {
        return switch (entry) {
            case UserText(String text) -> BetaMessageParam.ofUser(text);
            case AssistantText(String text) -> BetaMessageParam.ofAssistant(text);
            case ModelStep(Step step) -> step.vendorMessage() instanceof BetaMessage reply
                    ? reply.toParam() : BetaMessageParam.ofAssistant(step.text());
            case ToolResults(List<ToolResult> results) -> BetaMessageParam.ofUserOfContentBlockParams(results.stream()
                    .map(result -> BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                            .toolUseId(result.toolCallId()).content(result.content()).isError(result.error()).build()))
                    .toList());
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> input(JsonValue value) {
        try {
            Map<String, Object> map = value.convert(Map.class);
            return map == null ? Map.of() : map;
        } catch (RuntimeException _) {
            return Map.of(); // the tool then reports what is missing
        }
    }

    private static Stop stop(BetaStopReason reason) {
        if (BetaStopReason.TOOL_USE.equals(reason)) return Stop.TOOLS;
        if (BetaStopReason.REFUSAL.equals(reason)) return Stop.REFUSED;
        if (BetaStopReason.MAX_TOKENS.equals(reason)) return Stop.CUT_OFF;
        return Stop.DONE;
    }
}
