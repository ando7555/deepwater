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
 *   <li>{@code ASSISTANT_PROVIDER=gemini}: Google Gemini's free tier (needs OPENAI_COMPAT_API_KEY from Google AI Studio).</li>
 *   <li>{@code ASSISTANT_PROVIDER=groq}: Groq's free tier (needs OPENAI_COMPAT_API_KEY; model OPENAI_COMPAT_MODEL).</li>
 *   <li>{@code ASSISTANT_PROVIDER=openai-compatible}: any OpenAI-style API (OPENAI_COMPAT_BASE_URL, _API_KEY, _MODEL, _COMPANY).</li>
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
                                  @Value("${deepwater.assistant.timeout-seconds:120}") long timeoutSeconds,
                                  @Value("${deepwater.assistant.openai-compatible.base-url:}") String compatUrl,
                                  @Value("${deepwater.assistant.openai-compatible.api-key:}") String compatKey,
                                  @Value("${deepwater.assistant.openai-compatible.model:}") String compatModel,
                                  @Value("${deepwater.assistant.openai-compatible.company:}") String compatCompany) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        String choice = provider == null ? "auto" : provider.strip().toLowerCase();
        AssistantModel chosen = switch (choice) {
            case "off" -> OFF;
            case "ollama" -> new OllamaAssistantModel(ollamaUrl, ollamaModel, timeout);
            case "groq" -> compatible(or(compatUrl, GROQ_URL), compatKey, or(compatModel, GROQ_MODEL), or(compatCompany, "Groq"), timeout);
            case "gemini" -> compatible(or(compatUrl, GEMINI_URL), compatKey, or(compatModel, GEMINI_MODEL), or(compatCompany, "Google"), timeout);
            case "openai-compatible" -> {
                if (compatUrl == null || compatUrl.isBlank() || compatModel == null || compatModel.isBlank())
                    throw new IllegalStateException("ASSISTANT_PROVIDER=openai-compatible needs OPENAI_COMPAT_BASE_URL and OPENAI_COMPAT_MODEL.");
                yield compatible(compatUrl, compatKey, compatModel, or(compatCompany, "the AI provider"), timeout);
            }
            case "anthropic" -> {
                if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("ASSISTANT_PROVIDER=anthropic needs ANTHROPIC_API_KEY.");
                yield claude(apiKey, model, effort, timeout);
            }
            case "auto" -> apiKey == null || apiKey.isBlank() ? OFF : claude(apiKey, model, effort, timeout);
            default -> throw new IllegalStateException("Unknown ASSISTANT_PROVIDER '" + provider + "'. Use auto, anthropic, gemini, groq, openai-compatible, ollama or off.");
        };
        log.info("Ask Deepwater model: {}", chosen.description());
        return chosen;
    }

    static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    static final String GEMINI_MODEL = "gemini-2.5-flash";
    static final String GROQ_URL = "https://api.groq.com/openai/v1";
    static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private static AssistantModel compatible(String url, String key, String model, String company, Duration timeout) {
        if (key == null || key.isBlank()) {
            // A missing key must not stop the whole app: the assistant says it is off until the key is added.
            log.warn("{} is chosen but OPENAI_COMPAT_API_KEY is not set, so the assistant stays off.", company);
            return OFF;
        }
        return new OpenAiCompatibleAssistantModel(url, key, model, company, timeout);
    }

    private static String or(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

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
