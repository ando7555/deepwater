package com.deepwater.assistant;

import com.deepwater.platform.PlatformService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Ask Deepwater over HTTP. POST a run (the chat so far, last message from the person) and read the answer as a
 * server-sent event stream of AG-UI events. Same sign-in as the rest of the app.
 */
@RestController
@RequestMapping("/api/v1/assistant")
class AssistantController {
    private static final long TIMEOUT_MS = 180_000;
    private final PlatformService platform;
    private final AssistantService assistant;

    AssistantController(PlatformService platform, AssistantService assistant) {
        this.platform = platform;
        this.assistant = assistant;
    }

    @GetMapping
    Map<String, Object> status(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        platform.requireAccount(auth);
        return Map.of("enabled", assistant.enabled());
    }

    @PostMapping(value = "/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter run(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @RequestBody RunInput body) {
        String account = platform.requireAccount(auth);
        RunInput input = body == null ? new RunInput(null, null) : body;
        List<AssistantService.ChatMessage> messages = input.messages();
        assistant.validate(account, messages);
        String threadId = input.threadId() == null || input.threadId().isBlank() ? java.util.UUID.randomUUID().toString() : input.threadId();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Thread.startVirtualThread(() -> {
            try {
                assistant.run(account, threadId, messages, event -> {
                    try {
                        emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
            } catch (RuntimeException _) {
                // The person closed the page mid-answer; nothing left to send to.
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }

    /** A cut-down AG-UI RunAgentInput: the thread id and the messages so far. */
    record RunInput(String threadId, List<AssistantService.ChatMessage> messages) {}
}
