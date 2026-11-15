package com.deepwater.exam;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The Polish B1 practice exam, read once from {@code exam/polish-b1.json}. Its five parts follow the
 * layout of the Polish state certificate exam; the items are Deepwater's own practice items.
 */
@Component
public class ExamContent {
    public static final String CHOICE = "CHOICE";
    private final Exam exam;

    public ExamContent(ObjectMapper json) {
        try (InputStream in = ExamContent.class.getResourceAsStream("/exam/polish-b1.json")) {
            if (in == null) throw new IllegalStateException("exam/polish-b1.json is missing");
            this.exam = json.readValue(in, Exam.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Exam exam() { return exam; }

    /** The exam as a learner sees it: no answer keys and no topic words. */
    public PublicExam publicExam() {
        return new PublicExam(exam.id(), exam.title(), exam.notice(), exam.parts().stream().map(p -> new PublicPart(p.id(), p.name(),
                p.english(), p.kind(), p.instructions(), p.passages() == null ? List.of() : p.passages(),
                p.items().stream().map(i -> new PublicItem(i.id(), i.passage(), i.audio(), i.prompt(),
                        i.options() == null ? List.of() : i.options(), i.minWords(), i.maxWords())).toList())).toList());
    }

    public record Exam(String id, String title, String notice, List<Part> parts) {}

    public record Part(String id, String name, String english, String kind, String instructions, List<Passage> passages, List<Item> items) {
        public boolean closed() { return CHOICE.equals(kind); }
    }

    public record Passage(String id, String title, String text) {}

    public record Item(String id, String passage, String audio, String prompt, List<String> options, Integer answer,
                       Integer minWords, Integer maxWords, List<String> topicWords) {}

    public record PublicExam(String id, String title, String notice, List<PublicPart> parts) {}

    public record PublicPart(String id, String name, String english, String kind, String instructions, List<Passage> passages,
                             List<PublicItem> items) {}

    public record PublicItem(String id, String passage, String audio, String prompt, List<String> options, Integer minWords, Integer maxWords) {}
}
