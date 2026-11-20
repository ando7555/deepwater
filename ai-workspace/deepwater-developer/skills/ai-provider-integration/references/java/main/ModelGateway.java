package dev.course.ai;

import java.util.List;

/** The port: the rest of the application talks to "a model", never to a vendor SDK. */
public interface ModelGateway {

    Completion complete(CompletionRequest request);

    record Message(Role role, String text) {}

    enum Role { USER, ASSISTANT }

    record CompletionRequest(String system, List<Message> messages, int maxOutputTokens) {
        public CompletionRequest {
            if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("At least one message is needed.");
            if (maxOutputTokens < 1) throw new IllegalArgumentException("maxOutputTokens must be positive.");
            messages = List.copyOf(messages);
        }

        public static CompletionRequest ask(String system, String userText, int maxOutputTokens) {
            return new CompletionRequest(system, List.of(new Message(Role.USER, userText)), maxOutputTokens);
        }
    }

    /** What came back, plus the numbers you need for cost and debugging. */
    record Completion(String text, String provider, String model, long inputTokens, long outputTokens, String stopReason) {}

    /** Raised for every provider failure, so callers handle one exception type. */
    class ModelGatewayException extends RuntimeException {
        private final boolean retryable;

        public ModelGatewayException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean retryable() { return retryable; }
    }
}
