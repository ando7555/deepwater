package com.deepwater.exam;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the examiner: the AI examiner when an Anthropic API key is set, otherwise the fixed rubric. */
@Configuration
public class ExamConfiguration {
    @Bean
    WritingAssessor writingAssessor(@Value("${deepwater.exam.anthropic-api-key:}") String apiKey,
                                    @Value("${deepwater.exam.ai-model:claude-opus-5-5}") String model) {
        if (apiKey == null || apiKey.isBlank()) return new RuleWritingAssessor();
        return new ClaudeWritingAssessor(AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(120)).build(), model);
    }
}
