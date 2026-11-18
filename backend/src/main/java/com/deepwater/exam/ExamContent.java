package com.deepwater.exam;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * The practice exams, each read once from {@code exam/<id>.json}: Polish B1 (five parts, in the shape
 * of the Polish state certificate exam), Danish PD3 (four parts, in the shape of Prøve i Dansk 3) and
 * German B1 (four modules, in the shape of the Goethe-Zertifikat B1).
 * The items are Deepwater's own practice items, not official tasks.
 */
@Component
public class ExamContent {
    public static final String CHOICE = "CHOICE";
    public static final String POLISH_B1 = "polish-b1";
    public static final String DANISH_PD3 = "danish-pd3";
    public static final String GERMAN_B1 = "german-b1";
    private final Map<String, Exam> exams = new LinkedHashMap<>();

    public ExamContent(ObjectMapper json) {
        for (String id : List.of(POLISH_B1, DANISH_PD3, GERMAN_B1)) {
            try (InputStream in = ExamContent.class.getResourceAsStream("/exam/" + id + ".json")) {
                if (in == null) throw new IllegalStateException("exam/" + id + ".json is missing");
                Exam exam = json.readValue(in, Exam.class);
                if (!id.equals(exam.id())) throw new IllegalStateException("exam/" + id + ".json has id " + exam.id());
                ExamLanguage.of(exam.language());
                exams.put(id, exam);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    /** The exam with this id; an unknown id is a 404. */
    public Exam exam(String id) {
        Exam exam = exams.get(id);
        if (exam == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no practice exam called " + id + ".");
        return exam;
    }

    public List<String> ids() { return List.copyOf(exams.keySet()); }

    /** The exam as a learner sees it: no answer keys and no topic words. */
    public PublicExam publicExam(String id) {
        Exam exam = exam(id);
        return new PublicExam(exam.id(), exam.title(), exam.notice(), exam.language(), exam.level(), exam.examName(), exam.shortName(),
                exam.question(), exam.parts().stream().map(p -> new PublicPart(p.id(), p.name(),
                p.english(), p.kind(), p.instructions(), p.passages() == null ? List.of() : p.passages(),
                p.items().stream().map(i -> new PublicItem(i.id(), i.passage(), i.audio(), i.prompt(),
                        i.options() == null ? List.of() : i.options(), i.minWords(), i.maxWords())).toList())).toList());
    }

    /**
     * language is a code such as "pl" or "da"; level the CEFR level the exam tests; examName the real exam it
     * is shaped like; shortName what the learner aims for, as in "ready for B1"; question the intro headline.
     */
    public record Exam(String id, String title, String notice, String language, String level, String examName, String shortName,
                       String question, List<Part> parts) {
        public WritingAssessor.Target target() { return new WritingAssessor.Target(language, level, examName); }
    }

    public record Part(String id, String name, String english, String kind, String instructions, List<Passage> passages, List<Item> items) {
        public boolean closed() { return CHOICE.equals(kind); }
    }

    public record Passage(String id, String title, String text) {}

    public record Item(String id, String passage, String audio, String prompt, List<String> options, Integer answer,
                       Integer minWords, Integer maxWords, List<String> topicWords) {}

    public record PublicExam(String id, String title, String notice, String language, String level, String examName, String shortName,
                             String question, List<PublicPart> parts) {}

    public record PublicPart(String id, String name, String english, String kind, String instructions, List<Passage> passages,
                             List<PublicItem> items) {}

    public record PublicItem(String id, String passage, String audio, String prompt, List<String> options, Integer minWords, Integer maxWords) {}
}
