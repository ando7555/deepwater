package com.deepwater.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A stand-in model for tests: plays back scripted steps and remembers every conversation it was shown. */
class ScriptedModel implements AssistantModel {
    final List<Conversation> seen = new ArrayList<>();
    private final List<Function<Conversation, Step>> script = new ArrayList<>();
    private Function<Conversation, Step> otherwise = c -> new Step("Done.", List.of(), Stop.DONE, null);

    synchronized ScriptedModel then(Function<Conversation, Step> step) { script.add(step); return this; }

    synchronized ScriptedModel always(Function<Conversation, Step> step) { otherwise = step; return this; }

    /** Pretend to be a hosted model run by this company, so the consent rules apply. */
    volatile String sharesWith;

    synchronized void reset() { seen.clear(); script.clear(); otherwise = c -> new Step("Done.", List.of(), Stop.DONE, null); sharesWith = null; }

    @Override public boolean enabled() { return true; }

    @Override public String sharesDataWith() { return sharesWith; }

    @Override
    public synchronized Step next(Conversation conversation) {
        seen.add(conversation);
        return script.isEmpty() ? otherwise.apply(conversation) : script.removeFirst().apply(conversation);
    }

    static Step tools(ToolCall... calls) { return new Step("", List.of(calls), Stop.TOOLS, null); }

    static Step answer(String text) { return new Step(text, List.of(), Stop.DONE, null); }
}
