package com.deepwater.assistant;

import java.util.List;
import java.util.Map;

/**
 * The port between Deepwater's assistant and whichever AI model answers. The orchestration (which tools exist,
 * who may call them, how many steps, what streams to the screen) lives in {@link AssistantService} and does not
 * depend on any vendor; {@link ClaudeAssistantModel} is today's adapter, and tests use a scripted one.
 */
public interface AssistantModel {
    /** False when no model is configured (no API key); the assistant then says so instead of guessing. */
    boolean enabled();

    /** One model step: read the conversation so far and answer with text, tool calls, or both. */
    Step next(Conversation conversation);

    record Conversation(String system, List<ToolSpec> tools, List<Entry> transcript) {}

    record ToolSpec(String name, String description, Map<String, Object> inputSchema) {}

    record ToolCall(String id, String name, Map<String, Object> input) {}

    record ToolResult(String toolCallId, String content, boolean error) {}

    enum Stop { DONE, TOOLS, REFUSED, CUT_OFF }

    /** vendorMessage is the adapter's own copy of the reply, so it can send it back unchanged on the next step. */
    record Step(String text, List<ToolCall> toolCalls, Stop stop, Object vendorMessage) {}

    /** One entry of the transcript, in order. */
    sealed interface Entry permits UserText, AssistantText, ModelStep, ToolResults {}
    record UserText(String text) implements Entry {}
    record AssistantText(String text) implements Entry {}
    record ModelStep(Step step) implements Entry {}
    record ToolResults(List<ToolResult> results) implements Entry {}
}
