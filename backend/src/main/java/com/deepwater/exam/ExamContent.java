package com.deepwater.exam;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * The practice exams, each read once from {@code exam/<id>.json}: Polish B1 (five parts, in the shape
 * of the Polish state certificate exam), Danish PD3 (four parts, in the shape of Prøve i Dansk 3) and
 * German B1 (four modules, in the shape of the Goethe-Zertifikat B1).
 * The items are Deepwater's own practice items, not official tasks.
 *
 * <p>Each exam is a series of practice tests: test 1 is {@code exam/<id>.json} and tests 2, 3, ... are
 * {@code exam/<id>-2.json} and so on, each with its own id ({@code polish-b1-2}). A later test may leave out
 * the exam's description (title, notice, language, level...); it then takes it from test 1. Every test must
 * have the same parts, in the same order, as test 1.
 */
@Component
public class ExamContent {
    public static final String CHOICE = "CHOICE";
    public static final String POLISH_B1 = "polish-b1";
    public static final String DANISH_PD3 = "danish-pd3";
    public static final String GERMAN_B1 = "german-b1";
    private static final int MAX_TESTS = 9;
    private final Map<String, Exam> exams = new LinkedHashMap<>();
    /** Every test id of the series a test belongs to, test 1 first. */
    private final Map<String, List<String>> series = new LinkedHashMap<>();

    public ExamContent(ObjectMapper json) {
        for (String id : List.of(POLISH_B1, DANISH_PD3, GERMAN_B1)) {
            Exam first = read(json, id);
            if (first == null) throw new IllegalStateException("exam/" + id + ".json is missing");
            ExamLanguage.of(first.language());
            List<String> tests = new ArrayList<>(List.of(id));
            exams.put(id, first);
            for (int n = 2; n <= MAX_TESTS; n++) {
                Exam test = read(json, id + "-" + n);
                if (test == null) break;
                Exam filled = test.from(first, n);
                checkSameParts(first, filled);
                exams.put(filled.id(), filled);
                tests.add(filled.id());
            }
            for (String test : tests) series.put(test, List.copyOf(tests));
        }
    }

    private static Exam read(ObjectMapper json, String id) {
        try (InputStream in = ExamContent.class.getResourceAsStream("/exam/" + id + ".json")) {
            if (in == null) return null;
            Exam exam = json.readValue(in, Exam.class);
            if (!id.equals(exam.id())) throw new IllegalStateException("exam/" + id + ".json has id " + exam.id());
            return exam;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void checkSameParts(Exam first, Exam test) {
        List<String> want = first.parts().stream().map(p -> p.id() + ":" + p.kind()).toList();
        List<String> got = test.parts().stream().map(p -> p.id() + ":" + p.kind()).toList();
        if (!want.equals(got)) throw new IllegalStateException(test.id() + " has parts " + got + " but " + first.id() + " has " + want);
        Set<String> itemIds = new HashSet<>();
        for (Part part : test.parts())
            for (Item item : part.items()) {
                if (!itemIds.add(item.id())) throw new IllegalStateException(test.id() + " repeats item id " + item.id());
                if (part.closed() && (item.answer() == null || item.options() == null || item.answer() < 0 || item.answer() >= item.options().size()))
                    throw new IllegalStateException(test.id() + " item " + item.id() + " has no valid answer");
            }
    }

    /** The exam with this id; an unknown id is a 404. */
    public Exam exam(String id) {
        Exam exam = exams.get(id);
        if (exam == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no practice exam called " + id + ".");
        return exam;
    }

    public List<String> ids() { return List.copyOf(exams.keySet()); }

    /** The practice tests of the series this exam belongs to, test 1 first. */
    public List<String> tests(String id) {
        exam(id);
        return series.get(id);
    }

    /** The exam as a learner sees it: no answer keys and no topic words. */
    public PublicExam publicExam(String id) {
        Exam exam = exam(id);
        List<String> tests = tests(id);
        return new PublicExam(exam.id(), exam.title(), exam.notice(), exam.language(), exam.level(), exam.examName(), exam.shortName(),
                exam.question(), tests.indexOf(id) + 1, tests, exam.parts().stream().map(p -> new PublicPart(p.id(), p.name(),
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

        /** Test number n of first's series: anything this file leaves out comes from test 1. */
        Exam from(Exam first, int n) {
            return new Exam(id, title != null ? title : first.title() + " · test " + n, or(notice, first.notice()),
                    or(language, first.language()), or(level, first.level()), or(examName, first.examName()),
                    or(shortName, first.shortName()), or(question, first.question()), parts);
        }

        private static String or(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    }

    public record Part(String id, String name, String english, String kind, String instructions, List<Passage> passages, List<Item> items) {
        public boolean closed() { return CHOICE.equals(kind); }
    }

    public record Passage(String id, String title, String text) {}

    public record Item(String id, String passage, String audio, String prompt, List<String> options, Integer answer,
                       Integer minWords, Integer maxWords, List<String> topicWords) {}

    /** testNumber counts from 1; tests are the ids of every practice test in this exam's series, test 1 first. */
    public record PublicExam(String id, String title, String notice, String language, String level, String examName, String shortName,
                             String question, int testNumber, List<String> tests, List<PublicPart> parts) {}

    public record PublicPart(String id, String name, String english, String kind, String instructions, List<Passage> passages,
                             List<PublicItem> items) {}

    public record PublicItem(String id, String passage, String audio, String prompt, List<String> options, Integer minWords, Integer maxWords) {}
}
