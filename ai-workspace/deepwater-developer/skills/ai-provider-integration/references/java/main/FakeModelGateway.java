package dev.course.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Test double: no network, no key, fully predictable. Records every request it receives. */
public class FakeModelGateway implements ModelGateway {
    private final Function<CompletionRequest, String> answer;
    private final List<CompletionRequest> received = new ArrayList<>();

    public FakeModelGateway(Function<CompletionRequest, String> answer) { this.answer = answer; }

    public static FakeModelGateway alwaysAnswering(String text) { return new FakeModelGateway(request -> text); }

    @Override
    public Completion complete(CompletionRequest request) {
        received.add(request);
        String text = answer.apply(request);
        return new Completion(text, "fake", "fake-model", 0, 0, "end_turn");
    }

    public List<CompletionRequest> received() { return List.copyOf(received); }
}
