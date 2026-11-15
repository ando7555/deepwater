package com.deepwater.assistant;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything about the assistant that used to be a constant, now set in application.properties or by environment
 * variable (see the "deepwater.assistant" block there). Missing values fall back to {@link #defaults()}.
 *
 * @param maxSteps         model steps per question before the assistant stops (ASSISTANT_MAX_STEPS)
 * @param maxHistory       chat messages accepted per question (ASSISTANT_MAX_HISTORY)
 * @param maxMessageChars  characters per message (ASSISTANT_MAX_MESSAGE_CHARS)
 * @param runsPerHour      questions per person per hour (ASSISTANT_RUNS_PER_HOUR)
 * @param systemPrompt     where the instructions for the model live (ASSISTANT_SYSTEM_PROMPT), a Spring resource
 *                         location such as classpath:assistant/system-prompt.md or file:/etc/deepwater/prompt.md
 * @param suggestions      the starter questions shown in the chat panel (ASSISTANT_SUGGESTIONS, comma-separated)
 */
@ConfigurationProperties("deepwater.assistant.settings")
public record AssistantSettings(Integer maxSteps, Integer maxHistory, Integer maxMessageChars, Integer runsPerHour,
                                String systemPrompt, List<String> suggestions) {
    public AssistantSettings {
        AssistantSettings d = DEFAULTS;
        if (d != null) {
            maxSteps = positive(maxSteps, d.maxSteps);
            maxHistory = positive(maxHistory, d.maxHistory);
            maxMessageChars = positive(maxMessageChars, d.maxMessageChars);
            runsPerHour = positive(runsPerHour, d.runsPerHour);
            systemPrompt = systemPrompt == null || systemPrompt.isBlank() ? d.systemPrompt : systemPrompt.strip();
            suggestions = suggestions == null || suggestions.isEmpty() ? d.suggestions
                    : suggestions.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        }
    }

    private static final AssistantSettings DEFAULTS = new AssistantSettings(6, 20, 2000, 30,
            "classpath:assistant/system-prompt.md", List.of("Which cards should I review today?",
            "Find jobs in Denmark that are open to me", "Am I ready for Polish B1?"));

    public static AssistantSettings defaults() { return DEFAULTS; }

    private static Integer positive(Integer value, Integer fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
