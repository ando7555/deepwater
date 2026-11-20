package dev.course.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * One adapter for every service that speaks the OpenAI "chat completions" format:
 * OpenAI itself, Ollama on your laptop, OpenRouter, Groq, Mistral, Gemini's compatible endpoint
 * and most company AI gateways. Only the base URL, the model name and the key change.
 */
public class OpenAiCompatibleGateway implements ModelGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String providerName;
    private final URI chatCompletionsUri;
    private final String model;
    private final String apiKey; // may be null for a local server such as Ollama
    private final Duration timeout;

    public OpenAiCompatibleGateway(String providerName, String baseUrl, String model, String apiKey, Duration timeout) {
        this.providerName = providerName;
        this.chatCompletionsUri = URI.create(baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions");
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    @Override
    public Completion complete(CompletionRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(request)));
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ModelGatewayException(providerName + " could not be reached.", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelGatewayException(providerName + " call was interrupted.", false, e);
        }
        int status = response.statusCode();
        if (status == 429 || status >= 500) throw new ModelGatewayException(providerName + " answered " + status + ".", true, null);
        // Never put the response body in the message: it can echo your prompt or personal data.
        if (status >= 400) throw new ModelGatewayException(providerName + " rejected the request with " + status + ".", false, null);
        return parse(response.body());
    }

    private String body(CompletionRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", request.maxOutputTokens());
        ArrayNode messages = root.putArray("messages");
        if (request.system() != null && !request.system().isBlank()) {
            messages.addObject().put("role", "system").put("content", request.system());
        }
        for (Message message : request.messages()) {
            messages.addObject().put("role", message.role() == Role.USER ? "user" : "assistant").put("content", message.text());
        }
        return root.toString();
    }

    private Completion parse(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            JsonNode usage = root.path("usage");
            return new Completion(choice.path("message").path("content").asText(""), providerName,
                    root.path("model").asText(model), usage.path("prompt_tokens").asLong(0),
                    usage.path("completion_tokens").asLong(0), choice.path("finish_reason").asText("unknown"));
        } catch (IOException e) {
            throw new ModelGatewayException(providerName + " sent a response that is not valid JSON.", false, e);
        }
    }
}
