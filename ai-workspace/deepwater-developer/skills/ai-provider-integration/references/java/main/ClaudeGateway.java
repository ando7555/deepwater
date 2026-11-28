package dev.course.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;

/** Adapter for Claude through Anthropic's official Java SDK. Reads ANTHROPIC_API_KEY from the environment. */
public class ClaudeGateway implements ModelGateway {
    private final AnthropicClient client;
    private final String model;

    public ClaudeGateway(String model) {
        this(AnthropicOkHttpClient.fromEnv(), model);
    }

    ClaudeGateway(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public Completion complete(CompletionRequest request) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxOutputTokens());
        if (request.system() != null && !request.system().isBlank()) params.system(request.system());
        for (Message message : request.messages()) {
            if (message.role() == Role.USER) params.addUserMessage(message.text());
            else params.addAssistantMessage(message.text());
        }
        try {
            com.anthropic.models.messages.Message response = client.messages().create(params.build());
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .reduce("", String::concat);
            return new Completion(text, "claude", model, response.usage().inputTokens(),
                    response.usage().outputTokens(), response.stopReason().map(Object::toString).orElse("unknown"));
        } catch (AnthropicServiceException e) {
            boolean retryable = e.statusCode() == 429 || e.statusCode() >= 500;
            throw new ModelGatewayException("Claude answered " + e.statusCode() + ".", retryable, e);
        }
    }
}
