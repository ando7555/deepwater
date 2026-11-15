package com.deepwater.assistant;

import static com.deepwater.assistant.AssistantEvents.event;

import com.deepwater.assistant.AssistantModel.AssistantText;
import com.deepwater.assistant.AssistantModel.Conversation;
import com.deepwater.assistant.AssistantModel.Entry;
import com.deepwater.assistant.AssistantModel.ModelStep;
import com.deepwater.assistant.AssistantModel.Step;
import com.deepwater.assistant.AssistantModel.ToolCall;
import com.deepwater.assistant.AssistantModel.ToolResult;
import com.deepwater.assistant.AssistantModel.ToolResults;
import com.deepwater.assistant.AssistantModel.ToolSpec;
import com.deepwater.assistant.AssistantModel.UserText;
import com.deepwater.tools.DeepwaterTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * "Ask Deepwater": the orchestrator. It sends the person's question to the model together with Deepwater's
 * tools, runs each tool the model asks for <em>as the signed-in person</em> (the account id never comes from
 * the model), hands the results back, and repeats until the model answers, at most {@link #MAX_STEPS} times.
 * Every step is reported as an event so the screen can show "looking at your Harbor jobs…" while it works.
 */
@Service
public class AssistantService {
    static final int MAX_STEPS = 6;
    static final int MAX_HISTORY = 20;
    static final int MAX_MESSAGE_CHARS = 2000;
    static final int RUNS_PER_HOUR = 30;
    static final String OFF_MESSAGE = "The AI assistant is switched off on this server (no Anthropic API key is set). "
            + "Your data is safe and nothing was sent anywhere.";
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AssistantModel model;
    private final DeepwaterTools tools;
    private final ObjectMapper json;
    private final Clock clock;
    private final String systemPrompt;
    private final Map<String, Deque<Instant>> recentRuns = new ConcurrentHashMap<>();

    public AssistantService(AssistantModel model, DeepwaterTools tools, ObjectMapper json, Clock clock) {
        this.model = model;
        this.tools = tools;
        this.json = json;
        this.clock = clock;
        this.systemPrompt = read("assistant/system-prompt.md");
    }

    public boolean enabled() { return model.enabled(); }

    /** One chat message from the screen. role is "user" or "assistant". */
    public record ChatMessage(String role, String content) {}

    /** Checks the request before any streaming starts, so mistakes come back as normal HTTP errors. */
    public void validate(String accountId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > MAX_HISTORY)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send 1–" + MAX_HISTORY + " messages.");
        for (ChatMessage m : messages) {
            if (m == null || !("user".equals(m.role()) || "assistant".equals(m.role())) || m.content() == null
                    || m.content().isBlank() || m.content().length() > MAX_MESSAGE_CHARS)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each message needs role user or assistant and 1–" + MAX_MESSAGE_CHARS + " characters.");
        }
        if (!"user".equals(messages.getLast().role()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last message must be the person's question.");
        if (model.enabled()) takeRunSlot(accountId);
    }

    /** Runs the conversation and reports each event to {@code emit}. Never throws: failures become RUN_ERROR. */
    public void run(String accountId, String threadId, List<ChatMessage> messages, Consumer<Map<String, Object>> emit) {
        String runId = UUID.randomUUID().toString();
        emit.accept(event("RUN_STARTED", "threadId", threadId, "runId", runId));
        try {
            if (!model.enabled()) {
                say(emit, OFF_MESSAGE);
            } else {
                loop(accountId, messages, emit);
            }
            emit.accept(event("RUN_FINISHED", "threadId", threadId, "runId", runId));
        } catch (RuntimeException ex) {
            log.warn("Assistant run failed", ex);
            emit.accept(event("RUN_ERROR", "message", "The assistant could not answer just now. Please try again."));
        }
    }

    private void loop(String accountId, List<ChatMessage> messages, Consumer<Map<String, Object>> emit) {
        List<ToolSpec> specs = tools.all().stream()
                .map(t -> new ToolSpec(t.name(), t.description(), t.inputSchema())).toList();
        List<Entry> transcript = new ArrayList<>();
        for (ChatMessage m : messages)
            transcript.add("user".equals(m.role()) ? new UserText(m.content()) : new AssistantText(m.content()));

        for (int stepNo = 1; stepNo <= MAX_STEPS; stepNo++) {
            Step step = model.next(new Conversation(systemPrompt, specs, List.copyOf(transcript)));
            transcript.add(new ModelStep(step));
            if (step.stop() == AssistantModel.Stop.REFUSED) {
                say(emit, "I can't help with that one. Ask me about your Echo learning, Harbor jobs or your own Amber profile.");
                return;
            }
            if (step.text() != null && !step.text().isBlank()) say(emit, step.text());
            if (step.stop() != AssistantModel.Stop.TOOLS || step.toolCalls().isEmpty()) {
                if (step.stop() == AssistantModel.Stop.CUT_OFF) say(emit, "(The answer was cut short.)");
                return;
            }
            List<ToolResult> results = new ArrayList<>();
            for (ToolCall call : step.toolCalls()) results.add(runTool(accountId, call, emit));
            transcript.add(new ToolResults(results));
        }
        say(emit, "I stopped after " + MAX_STEPS + " steps without a final answer. Try asking a narrower question.");
    }

    private ToolResult runTool(String accountId, ToolCall call, Consumer<Map<String, Object>> emit) {
        emit.accept(event("TOOL_CALL_START", "toolCallId", call.id(), "toolCallName", call.name()));
        emit.accept(event("TOOL_CALL_ARGS", "toolCallId", call.id(), "delta", json.writeValueAsString(call.input())));
        emit.accept(event("TOOL_CALL_END", "toolCallId", call.id()));
        // The account comes from the session, never from the model's arguments.
        DeepwaterTools.Outcome outcome = tools.call(accountId, call.name(), call.input());
        emit.accept(event("TOOL_CALL_RESULT", "messageId", UUID.randomUUID().toString(), "toolCallId", call.id(),
                "role", "tool", "content", outcome.text(), "isError", outcome.error()));
        return new ToolResult(call.id(), outcome.text(), outcome.error());
    }

    private static void say(Consumer<Map<String, Object>> emit, String text) {
        String id = UUID.randomUUID().toString();
        emit.accept(event("TEXT_MESSAGE_START", "messageId", id, "role", "assistant"));
        emit.accept(event("TEXT_MESSAGE_CONTENT", "messageId", id, "delta", text));
        emit.accept(event("TEXT_MESSAGE_END", "messageId", id));
    }

    /** AI calls cost money: each person gets {@link #RUNS_PER_HOUR} questions per hour. Kept in memory. */
    private void takeRunSlot(String accountId) {
        Instant now = clock.instant();
        Deque<Instant> runs = recentRuns.computeIfAbsent(accountId, k -> new ArrayDeque<>());
        synchronized (runs) {
            while (!runs.isEmpty() && runs.peekFirst().isBefore(now.minusSeconds(3600))) runs.pollFirst();
            if (runs.size() >= RUNS_PER_HOUR)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You have asked a lot this hour. Try again later.");
            runs.addLast(now);
        }
    }

    private static String read(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
