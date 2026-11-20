package dev.course.ai;

import java.time.Duration;
import java.util.Map;

/**
 * Chooses the adapter from configuration, so switching provider is a change of environment,
 * not of code. In Spring Boot this becomes a @Configuration class with @Bean ModelGateway.
 *
 *   AI_PROVIDER     claude | openai-compatible | fake
 *   AI_MODEL        the provider's model id, from the provider's documentation
 *   AI_BASE_URL     openai-compatible only, for example http://localhost:11434/v1 for Ollama
 *   AI_API_KEY_ENV  openai-compatible only: the NAME of the variable that holds the key,
 *                   for example OPENAI_API_KEY; leave empty for a local server
 */
public final class ModelGateways {
    private ModelGateways() {}

    public static ModelGateway fromEnvironment(Map<String, String> env) {
        String provider = env.getOrDefault("AI_PROVIDER", "fake");
        return switch (provider) {
            case "fake" -> FakeModelGateway.alwaysAnswering("(fake model) Set AI_PROVIDER to talk to a real model.");
            case "claude" -> new ClaudeGateway(required(env, "AI_MODEL"));
            case "openai-compatible" -> {
                String keyVariable = env.getOrDefault("AI_API_KEY_ENV", "");
                String key = keyVariable.isBlank() ? null : required(env, keyVariable);
                yield new OpenAiCompatibleGateway(env.getOrDefault("AI_PROVIDER_LABEL", "openai-compatible"),
                        required(env, "AI_BASE_URL"), required(env, "AI_MODEL"), key, Duration.ofSeconds(60));
            }
            default -> throw new IllegalArgumentException("Unknown AI_PROVIDER '" + provider + "'. Use claude, openai-compatible or fake.");
        };
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set the environment variable " + name + ".");
        return value;
    }
}
