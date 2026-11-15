package com.deepwater.assistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Uses Claude when ANTHROPIC_API_KEY is set; otherwise the assistant is visibly off. */
@Configuration
public class AssistantConfiguration {
    @Bean
    @ConditionalOnMissingBean(AssistantModel.class)
    AssistantModel assistantModel(@Value("${deepwater.assistant.anthropic-api-key:}") String apiKey,
                                  @Value("${deepwater.assistant.ai-model:claude-opus-5-5}") String model,
                                  @Value("${deepwater.assistant.effort:medium}") String effort) {
        if (apiKey == null || apiKey.isBlank()) return OFF;
        return new ClaudeAssistantModel(AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(120)).build(),
                model, effort);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() { return Clock.systemUTC(); }

    static final AssistantModel OFF = new AssistantModel() {
        @Override public boolean enabled() { return false; }
        @Override public Step next(Conversation conversation) { throw new IllegalStateException("The assistant is off."); }
    };
}
