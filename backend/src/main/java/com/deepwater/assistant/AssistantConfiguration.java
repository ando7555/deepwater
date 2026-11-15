package com.deepwater.assistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the model from settings, with no code change:
 * <ul>
 *   <li>{@code ASSISTANT_PROVIDER=anthropic}: hosted Claude (needs ANTHROPIC_API_KEY).</li>
 *   <li>{@code ASSISTANT_PROVIDER=ollama}: a local model on Ollama, e.g. {@code OLLAMA_MODEL=hermes3}.</li>
 *   <li>{@code ASSISTANT_PROVIDER=off}: the assistant says it is off.</li>
 *   <li>{@code auto} (default): Claude when ANTHROPIC_API_KEY is set, otherwise off.</li>
 * </ul>
 */
@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(AssistantSettings.class)
public class AssistantConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AssistantConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(AssistantModel.class)
    AssistantModel assistantModel(@Value("${deepwater.assistant.provider:auto}") String provider,
                                  @Value("${deepwater.assistant.anthropic-api-key:}") String apiKey,
                                  @Value("${deepwater.assistant.ai-model:claude-opus-5-5}") String model,
                                  @Value("${deepwater.assistant.effort:medium}") String effort,
                                  @Value("${deepwater.assistant.ollama.base-url:http://localhost:11434}") String ollamaUrl,
                                  @Value("${deepwater.assistant.ollama.model:hermes3}") String ollamaModel,
                                  @Value("${deepwater.assistant.timeout-seconds:120}") long timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        String choice = provider == null ? "auto" : provider.strip().toLowerCase();
        AssistantModel chosen = switch (choice) {
            case "off" -> OFF;
            case "ollama" -> new OllamaAssistantModel(ollamaUrl, ollamaModel, timeout);
            case "anthropic" -> {
                if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("ASSISTANT_PROVIDER=anthropic needs ANTHROPIC_API_KEY.");
                yield claude(apiKey, model, effort, timeout);
            }
            case "auto" -> apiKey == null || apiKey.isBlank() ? OFF : claude(apiKey, model, effort, timeout);
            default -> throw new IllegalStateException("Unknown ASSISTANT_PROVIDER '" + provider + "'. Use auto, anthropic, ollama or off.");
        };
        log.info("Ask Deepwater model: {}", chosen.description());
        return chosen;
    }

    private static AssistantModel claude(String apiKey, String model, String effort, Duration timeout) {
        return new ClaudeAssistantModel(AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).build(), model, effort);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() { return Clock.systemUTC(); }

    static final AssistantModel OFF = new AssistantModel() {
        @Override public boolean enabled() { return false; }
        @Override public Step next(Conversation conversation) { throw new IllegalStateException("The assistant is off."); }
    };
}
